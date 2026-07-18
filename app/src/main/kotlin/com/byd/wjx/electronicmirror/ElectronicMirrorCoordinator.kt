package com.byd.wjx.electronicmirror

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ElectronicMirrorCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: ElectronicMirrorPreferences,
    private val helper: HelperClient,
    private val bootstrap: HelperBootstrap,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val overlayController = ElectronicMirrorOverlayController(appContext, ::onUnexpectedSurfaceLoss)

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _status = MutableStateFlow(ElectronicMirrorRuntimeStatus())
    val status: StateFlow<ElectronicMirrorRuntimeStatus> = _status.asStateFlow()

    private var cameraStateJob: Job? = null
    private var lastVehicleData: DiParsData? = null
    @Volatile private var originalCameraActive = false
    @Volatile private var immediateAutoParkingActive = false
    private var manualPreview: ElectronicMirrorViewpoint? = null
    private var activeScene: ActiveScene? = null
    private var retryJob: Job? = null
    private var frameWatchdogJob: Job? = null
    private var retryScene: ElectronicMirrorScene? = null
    private var consecutiveFailures = 0
    private var currentConfig: ElectronicMirrorConfig = preferences.snapshot()

    fun start(originalCameraState: StateFlow<Boolean>) {
        if (cameraStateJob?.isActive == true) return
        currentConfig = preferences.snapshot()
        cameraStateJob = applicationScope.launch {
            originalCameraState.collectLatest { active ->
                // Publish before waiting for the mutex so an in-flight camera start can observe
                // pre-emption and tear itself back down before becoming visible.
                originalCameraActive = active
                mutex.withLock {
                    reconcileLocked()
                }
            }
        }
    }

    suspend fun onVehicleData(data: DiParsData) {
        immediateAutoParkingActive = data.adasApaParkingStatus == AUTO_PARKING_ACTIVE
        mutex.withLock {
            lastVehicleData = data
            reconcileLocked()
        }
    }

    fun preview(viewpoint: ElectronicMirrorViewpoint) {
        applicationScope.launch {
            mutex.withLock {
                currentConfig = preferences.snapshot()
                resetRetryLocked()
                manualPreview = viewpoint
                reconcileLocked()
            }
        }
    }

    fun stopPreview() {
        applicationScope.launch {
            mutex.withLock {
                manualPreview = null
                resetRetryLocked()
                reconcileLocked()
            }
        }
    }

    fun applyPreferences() {
        applicationScope.launch {
            mutex.withLock {
                currentConfig = preferences.snapshot()
                resetRetryLocked()
                reconcileLocked()
            }
        }
    }

    fun stop() {
        cameraStateJob?.cancel()
        cameraStateJob = null
        applicationScope.launch {
            mutex.withLock {
                manualPreview = null
                lastVehicleData = null
                originalCameraActive = false
                immediateAutoParkingActive = false
                resetRetryLocked()
                stopActiveLocked("服务停止")
            }
        }
    }

    private suspend fun reconcileLocked() {
        val data = lastVehicleData
        val desired = ElectronicMirrorSceneArbiter.resolve(
            config = currentConfig,
            signals = ElectronicMirrorSignals(
                turnSignalFlash = data?.turnSignalFlash,
                gear = data?.gear,
                originalCameraActive = originalCameraActive,
                autoParkingActive = immediateAutoParkingActive,
            ),
            manualPreview = manualPreview,
        )
        val current = activeScene
        if (desired == null) {
            resetRetryLocked()
            val cameraOwnsDisplay = cameraPriorityActive()
            if (current != null) stopActiveLocked(if (cameraOwnsDisplay) "原车影像优先" else "待机")
            else setInactiveStatus(if (cameraOwnsDisplay) "原车影像占用，电子后视镜让行" else "待机")
            return
        }
        if (retryScene != null && retryScene != desired) resetRetryLocked()
        if (retryScene == desired) return

        when (ElectronicMirrorTransitionPolicy.resolve(current?.scene, desired)) {
            ElectronicMirrorTransition.NONE -> return
            ElectronicMirrorTransition.UPDATE_TRANSFORM -> {
                checkNotNull(current)
                overlayController.updateCalibration(current.handle, desired.calibration)
                overlayController.updateLabel(current.handle, desired)
                activeScene = current.copy(scene = desired)
                setActiveStatus(desired)
                return
            }
            ElectronicMirrorTransition.SWITCH_SOURCE -> {
                checkNotNull(current)
                val frameBeforeSwitch = current.handle.frameCount.get()
                val switched = helper.switchElectronicMirrorView(desired.source.helperValue)
                val freshFrame = switched && awaitFrameAfter(
                    current.handle,
                    frameBeforeSwitch,
                    VIEW_SWITCH_TIMEOUT_MS,
                )
                if (cameraPriorityActive()) {
                    stopActiveLocked("原车影像优先")
                    return
                }
                if (freshFrame) {
                    overlayController.updateCalibration(current.handle, desired.calibration)
                    overlayController.updateLabel(current.handle, desired)
                    activeScene = current.copy(scene = desired)
                    setActiveStatus(desired)
                    return
                }
                WjxFileLogger.w(
                    TAG,
                    "view switch did not produce a fresh frame switched=$switched; rebuilding camera",
                )
            }
            ElectronicMirrorTransition.REBUILD_WINDOW -> Unit
        }

        if (current != null) stopActiveLocked("切换画面")
        showLocked(desired)
    }

    private suspend fun showLocked(scene: ElectronicMirrorScene) {
        if (cameraPriorityActive()) {
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }
        _status.value = ElectronicMirrorRuntimeStatus(message = "正在准备${targetName(scene.target)}画面…")
        if (!bootstrap.ensureCurrent()) {
            scheduleRetryLocked(scene, "Helper 未连接，无法打开摄像头")
            return
        }
        if (cameraPriorityActive()) {
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }
        helper.grantOverlayPermission()
        if (!awaitOverlayPermission()) {
            scheduleRetryLocked(scene, "悬浮窗权限未生效")
            return
        }

        val handle = overlayController.show(scene)
        if (handle == null) {
            scheduleRetryLocked(scene, "无法创建${targetName(scene.target)}画面窗口")
            return
        }
        if (cameraPriorityActive()) {
            overlayController.hide(handle)
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }
        val surface = handle.surface
        if (surface == null || !surface.isValid) {
            overlayController.hide(handle)
            scheduleRetryLocked(scene, "摄像头画面 Surface 已失效")
            return
        }
        val started = helper.startElectronicMirror(surface, scene.source.helperValue, scene.fps)
        if (!started) {
            overlayController.hide(handle)
            scheduleRetryLocked(scene, "APA 摄像头启动失败")
            return
        }
        if (cameraPriorityActive()) {
            helper.stopElectronicMirror()
            overlayController.hide(handle)
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }

        _status.value = ElectronicMirrorRuntimeStatus(message = "摄像头已启动，正在等待首帧…")
        val firstFrameReceived = awaitFirstFrame(handle)
        if (cameraPriorityActive()) {
            helper.stopElectronicMirror()
            overlayController.hide(handle)
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }
        if (!firstFrameReceived || handle.surface?.isValid != true) {
            helper.stopElectronicMirror()
            overlayController.hide(handle)
            scheduleRetryLocked(scene, "摄像头已启动，但5秒内没有收到画面")
            return
        }
        overlayController.reveal(handle)
        if (handle.surface?.isValid != true) {
            helper.stopElectronicMirror()
            overlayController.hide(handle)
            scheduleRetryLocked(scene, "首帧到达后 Surface 失效")
            return
        }

        activeScene = ActiveScene(scene, handle)
        resetRetryLocked()
        ElectronicMirrorRuntimeGuard.isActive = true
        _active.value = true
        setActiveStatus(scene)
        startFrameWatchdogLocked(handle)
        WjxFileLogger.i(
            TAG,
            "active reason=${scene.reason} view=${scene.viewpoint} source=${scene.source} target=${scene.target}",
        )
    }

    private suspend fun stopActiveLocked(message: String, cancelWatchdog: Boolean = true) {
        if (cancelWatchdog) {
            frameWatchdogJob?.cancel()
            frameWatchdogJob = null
        }
        val current = activeScene
        activeScene = null
        if (current != null) {
            helper.stopElectronicMirror()
            overlayController.hide(current.handle)
            WjxFileLogger.i(TAG, "inactive reason=$message")
        }
        ElectronicMirrorRuntimeGuard.isActive = false
        _active.value = false
        setInactiveStatus(message)
    }

    private fun onUnexpectedSurfaceLoss(handle: ElectronicMirrorOverlayHandle) {
        applicationScope.launch {
            mutex.withLock {
                val current = activeScene
                if (current?.handle !== handle) return@withLock
                stopActiveLocked("画面 Surface 已失效")
                scheduleRetryLocked(current.scene, "画面 Surface 意外丢失")
                WjxFileLogger.w(TAG, "unexpected Surface loss target=${handle.target}")
            }
        }
    }

    private suspend fun awaitFirstFrame(handle: ElectronicMirrorOverlayHandle): Boolean {
        val deadline = SystemClock.elapsedRealtime() + FIRST_FRAME_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (cameraPriorityActive() || handle.surface?.isValid != true) return false
            if (handle.firstFrame.isCompleted) return true
            delay(FRAME_POLL_MS)
        }
        return handle.firstFrame.isCompleted
    }

    private suspend fun awaitFrameAfter(
        handle: ElectronicMirrorOverlayHandle,
        previousFrame: Long,
        timeoutMs: Long,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (cameraPriorityActive() || handle.surface?.isValid != true) return false
            if (handle.frameCount.get() > previousFrame) return true
            delay(FRAME_POLL_MS)
        }
        return handle.frameCount.get() > previousFrame
    }

    private fun startFrameWatchdogLocked(handle: ElectronicMirrorOverlayHandle) {
        frameWatchdogJob?.cancel()
        frameWatchdogJob = applicationScope.launch {
            while (true) {
                delay(FRAME_WATCHDOG_INTERVAL_MS)
                val lastFrameAt = handle.lastFrameAtMs
                if (lastFrameAt <= 0L || SystemClock.elapsedRealtime() - lastFrameAt <= FRAME_STALL_TIMEOUT_MS) {
                    continue
                }
                mutex.withLock {
                    val current = activeScene
                    if (current?.handle !== handle) return@withLock
                    // This job is the watchdog being retired; avoid cancelling its own coroutine
                    // before camera cleanup and retry scheduling have completed.
                    frameWatchdogJob = null
                    stopActiveLocked("画面已中断", cancelWatchdog = false)
                    if (!cameraPriorityActive()) {
                        scheduleRetryLocked(current.scene, "超过3秒未收到新画面")
                    }
                }
                return@launch
            }
        }
    }

    private fun scheduleRetryLocked(scene: ElectronicMirrorScene, cause: String) {
        if (retryScene != null && retryScene != scene) resetRetryLocked()
        retryJob?.cancel()
        retryJob = null
        retryScene = scene
        consecutiveFailures += 1
        WjxFileLogger.w(
            TAG,
            "camera unavailable failure=$consecutiveFailures cause=$cause reason=${scene.reason} " +
                "view=${scene.viewpoint} source=${scene.source} target=${scene.target}",
        )
        if (consecutiveFailures > MAX_AUTOMATIC_RETRIES) {
            setInactiveStatus("$cause，已停止自动重试")
            return
        }
        val delayMs = electronicMirrorRetryDelayMs(consecutiveFailures)
        setInactiveStatus("$cause，${delayMs / 1_000}秒后重试")
        retryJob = applicationScope.launch {
            delay(delayMs)
            mutex.withLock {
                if (retryScene != scene) return@withLock
                retryJob = null
                retryScene = null
                reconcileLocked()
            }
        }
    }

    private fun resetRetryLocked() {
        retryJob?.cancel()
        retryJob = null
        retryScene = null
        consecutiveFailures = 0
    }

    private suspend fun awaitOverlayPermission(): Boolean {
        if (Settings.canDrawOverlays(appContext)) return true
        repeat(10) {
            delay(200L)
            if (Settings.canDrawOverlays(appContext)) return true
        }
        return false
    }

    private fun setActiveStatus(scene: ElectronicMirrorScene) {
        _status.value = ElectronicMirrorRuntimeStatus(
            active = true,
            message = "${scene.viewpoint.displayName}影像 · ${targetName(scene.target)}",
            viewpoint = scene.viewpoint,
            target = scene.target,
        )
    }

    private fun setInactiveStatus(message: String) {
        _status.value = ElectronicMirrorRuntimeStatus(active = false, message = message)
    }

    private fun targetName(target: ElectronicMirrorDisplayTarget): String = when (target) {
        ElectronicMirrorDisplayTarget.CLUSTER -> "仪表盘"
        ElectronicMirrorDisplayTarget.CENTER -> "中控屏"
    }

    private fun cameraPriorityActive(): Boolean = originalCameraActive || immediateAutoParkingActive

    private data class ActiveScene(
        val scene: ElectronicMirrorScene,
        val handle: ElectronicMirrorOverlayHandle,
    )

    private companion object {
        const val TAG = "ElectronicMirror"
        const val AUTO_PARKING_ACTIVE = 4
        const val FIRST_FRAME_TIMEOUT_MS = 5_000L
        const val VIEW_SWITCH_TIMEOUT_MS = 2_500L
        const val FRAME_STALL_TIMEOUT_MS = 3_000L
        const val FRAME_WATCHDOG_INTERVAL_MS = 1_000L
        const val FRAME_POLL_MS = 50L
        const val MAX_AUTOMATIC_RETRIES = 5
    }
}

internal fun electronicMirrorRetryDelayMs(failureCount: Int): Long {
    val exponent = (failureCount - 1).coerceIn(0, 4)
    return 1_000L shl exponent
}
