package com.byd.wjx.electronicmirror

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.ElectronicMirrorCameraRequest
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong
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
import kotlinx.coroutines.withTimeoutOrNull

internal class ElectronicMirrorLifecycleGate {
    private val generation = AtomicLong(0L)

    fun advance(): Long = generation.incrementAndGet()

    fun isCurrent(candidate: Long): Boolean = generation.get() == candidate
}

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
    private val lifecycleGate = ElectronicMirrorLifecycleGate()
    private val overlayController = ElectronicMirrorOverlayController(appContext, ::onUnexpectedOutputLoss)

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _status = MutableStateFlow(ElectronicMirrorRuntimeStatus())
    val status: StateFlow<ElectronicMirrorRuntimeStatus> = _status.asStateFlow()

    private var cameraStateJob: Job? = null
    private var lastVehicleData: DiParsData? = null
    @Volatile private var originalCameraActive = false
    @Volatile private var immediateAutoParkingActive = false
    private var manualPreview: ElectronicMirrorViewpoint? = null
    private val activeScenes = linkedMapOf<ElectronicMirrorReason, ActiveScene>()
    private var frameHub: ElectronicMirrorFrameHub? = null
    private var activeCameraRoute: CameraRoute? = null
    private var cameraStarted = false
    private var retryJob: Job? = null
    private var frameWatchdogJob: Job? = null
    private var retryScenes: List<ElectronicMirrorScene>? = null
    private var consecutiveFailures = 0
    private var currentConfig: ElectronicMirrorConfig = preferences.snapshot()
    private var overlayPermissionReady = false
    private var lastCameraReleaseAtMs = Long.MIN_VALUE

    fun start(originalCameraState: StateFlow<Boolean>) {
        if (cameraStateJob?.isActive == true) return
        val generation = lifecycleGate.advance()
        currentConfig = preferences.snapshot()
        cameraStateJob = applicationScope.launch {
            originalCameraState.collectLatest { active ->
                if (!lifecycleGate.isCurrent(generation)) return@collectLatest
                originalCameraActive = active
                mutex.withLock {
                    if (lifecycleGate.isCurrent(generation)) reconcileLocked()
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

    /** Steering-wheel shortcut: first press enables/shows it, following presses toggle visibility. */
    fun toggleStreamingRearFromShortcut(): Boolean {
        val next = preferences.snapshot().toggledStreamingRearFromShortcut()
        preferences.save(next)
        applyPreferences()
        return next.streamingRearVisible
    }

    fun stop() {
        val generation = lifecycleGate.advance()
        cameraStateJob?.cancel()
        cameraStateJob = null
        applicationScope.launch {
            mutex.withLock {
                if (!lifecycleGate.isCurrent(generation)) return@withLock
                manualPreview = null
                lastVehicleData = null
                originalCameraActive = false
                immediateAutoParkingActive = false
                resetRetryLocked()
                stopSessionLocked("服务停止")
            }
        }
    }


    private suspend fun reconcileLocked() {
        val desiredScenes = desiredScenesLocked()
        if (desiredScenes.isEmpty()) {
            resetRetryLocked()
            val cameraOwnsDisplay = cameraPriorityActive()
            if (frameHub != null || activeScenes.isNotEmpty()) {
                stopSessionLocked(if (cameraOwnsDisplay) "原车影像优先" else "待机")
            } else {
                setInactiveStatus(if (cameraOwnsDisplay) "原车影像占用，电子后视镜让行" else "待机")
            }
            return
        }

        if (retryScenes != null && retryScenes != desiredScenes) resetRetryLocked()
        if (retryScenes == desiredScenes) return

        val desiredRoute = CameraRoute.from(desiredScenes.first())
        if (desiredScenes.any { !desiredRoute.matches(it) }) {
            stopSessionLocked("相机配置冲突")
            scheduleRetryLocked(desiredScenes, "多画面相机配置不一致")
            return
        }

        if (frameHub == null || !cameraStarted || activeCameraRoute != desiredRoute) {
            if (frameHub != null || activeScenes.isNotEmpty()) {
                stopSessionLocked("重建电子后视镜", publishStatus = false)
            }
            showSessionLocked(desiredScenes, desiredRoute)
            return
        }

        if (!reconcileWindowsLocked(desiredScenes)) {
            stopSessionLocked("画面窗口重建失败", publishStatus = false)
            scheduleRetryLocked(desiredScenes, "无法创建全部电子后视镜窗口")
            return
        }
        resetRetryLocked()
        setActiveStatus(desiredScenes)
    }

    private fun desiredScenesLocked(): List<ElectronicMirrorScene> {
        val data = lastVehicleData
        return ElectronicMirrorSceneArbiter.resolveAll(
            config = currentConfig,
            signals = ElectronicMirrorSignals(
                turnSignalFlash = data?.turnSignalFlash,
                gear = data?.gear,
                originalCameraActive = originalCameraActive,
                autoParkingActive = immediateAutoParkingActive,
            ),
            manualPreview = manualPreview,
        )
    }

    private suspend fun showSessionLocked(
        desiredScenes: List<ElectronicMirrorScene>,
        route: CameraRoute,
    ) {
        if (cameraPriorityActive()) {
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }
        _status.value = ElectronicMirrorRuntimeStatus(message = "正在准备电子后视镜画面…")
        if (!bootstrap.ensureCurrent()) {
            scheduleRetryLocked(desiredScenes, "Helper 未连接，无法打开摄像头")
            return
        }
        if (cameraPriorityActive()) {
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }
        if (!ensureOverlayPermission()) {
            scheduleRetryLocked(desiredScenes, "悬浮窗权限未生效")
            return
        }

        awaitCameraReleaseWindowLocked()
        if (cameraPriorityActive()) {
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }

        val hub = ElectronicMirrorFrameHub(
            bufferWidth = route.bufferWidth,
            bufferHeight = route.bufferHeight,
            onRendererFailed = ::onFrameHubFailure,
        )
        frameHub = hub
        activeCameraRoute = route

        for (scene in desiredScenes) {
            val handle = overlayController.show(scene, hub)
            if (handle == null) {
                overlayPermissionReady = false
                stopSessionLocked("画面窗口创建失败", publishStatus = false)
                scheduleRetryLocked(desiredScenes, "无法创建${targetName(scene.target)}画面窗口")
                return
            }
            activeScenes[scene.reason] = ActiveScene(scene, handle)
        }

        val surface = withTimeoutOrNull(CAMERA_INPUT_SURFACE_TIMEOUT_MS) {
            hub.inputReady.await()
        }
        if (surface == null || !surface.isValid) {
            stopSessionLocked("相机输入 Surface 失效", publishStatus = false)
            scheduleRetryLocked(desiredScenes, "相机输入 Surface 未准备好")
            return
        }

        val started = helper.startElectronicMirror(surface, route.request())
        if (!started) {
            stopSessionLocked("APA 摄像头启动失败", publishStatus = false)
            scheduleRetryLocked(desiredScenes, "APA 摄像头启动失败")
            return
        }
        cameraStarted = true
        if (cameraPriorityActive()) {
            stopSessionLocked("原车影像优先")
            return
        }
        if (hub.inputSurface?.isValid != true) {
            stopSessionLocked("摄像头启动后 Surface 已失效", publishStatus = false)
            scheduleRetryLocked(desiredScenes, "摄像头启动后 Surface 已失效")
            return
        }

        resetRetryLocked()
        ElectronicMirrorRuntimeGuard.isActive = true
        _active.value = true
        setActiveStatus(desiredScenes)
        startFrameWatchdogLocked(hub)
        WjxFileLogger.i(
            TAG,
            "active shared camera=${route.cameraTag} surface=${route.surfaceIndex} " +
                "tiles=${route.tileCount} outputs=${desiredScenes.joinToString { it.reason.name }}",
        )
    }

    private suspend fun reconcileWindowsLocked(desiredScenes: List<ElectronicMirrorScene>): Boolean {
        val hub = frameHub ?: return false
        val desiredByReason = desiredScenes.associateBy(ElectronicMirrorScene::reason)

        activeScenes.keys
            .filterNot(desiredByReason::containsKey)
            .toList()
            .forEach { reason ->
                activeScenes.remove(reason)?.let { active ->
                    overlayController.hide(active.handle)
                    WjxFileLogger.i(TAG, "output removed reason=$reason")
                }
            }

        for (scene in desiredScenes) {
            val current = activeScenes[scene.reason]
            if (current == null) {
                val handle = overlayController.show(scene, hub) ?: return false
                activeScenes[scene.reason] = ActiveScene(scene, handle)
                WjxFileLogger.i(TAG, "output added reason=${scene.reason}")
                continue
            }
            if (current.scene.target != scene.target) {
                overlayController.hide(current.handle)
                activeScenes.remove(scene.reason)
                val handle = overlayController.show(scene, hub) ?: return false
                activeScenes[scene.reason] = ActiveScene(scene, handle)
                continue
            }
            if (current.scene.geometry != scene.geometry) {
                overlayController.updateGeometry(current.handle, scene)
            }
            if (current.scene != scene) {
                overlayController.updatePresentation(current.handle, scene)
                activeScenes[scene.reason] = current.copy(scene = scene)
            }
        }

        ElectronicMirrorRuntimeGuard.isActive = activeScenes.isNotEmpty()
        _active.value = activeScenes.isNotEmpty()
        return activeScenes.keys == desiredByReason.keys
    }

    private suspend fun stopSessionLocked(
        message: String,
        cancelWatchdog: Boolean = true,
        publishStatus: Boolean = true,
    ) {
        if (cancelWatchdog) {
            frameWatchdogJob?.cancel()
            frameWatchdogJob = null
        }

        val hadCamera = cameraStarted
        val hub = frameHub
        val handles = activeScenes.values.map(ActiveScene::handle)
        cameraStarted = false
        frameHub = null
        activeCameraRoute = null
        activeScenes.clear()

        if (hadCamera) helper.stopElectronicMirror()
        handles.forEach { overlayController.hide(it) }
        hub?.release()
        if (hadCamera || hub != null) {
            lastCameraReleaseAtMs = SystemClock.elapsedRealtime()
            WjxFileLogger.i(TAG, "inactive reason=$message outputs=${handles.size}")
        }

        ElectronicMirrorRuntimeGuard.isActive = false
        _active.value = false
        if (publishStatus) setInactiveStatus(message)
    }

    private suspend fun awaitCameraReleaseWindowLocked() {
        if (lastCameraReleaseAtMs == Long.MIN_VALUE) return
        val elapsed = SystemClock.elapsedRealtime() - lastCameraReleaseAtMs
        val remaining = CAMERA_REATTACH_GUARD_MS - elapsed
        if (remaining > 0L) {
            WjxFileLogger.i(TAG, "camera reattach delayed ${remaining}ms after release")
            delay(remaining)
        }
    }

    private fun onUnexpectedOutputLoss(handle: ElectronicMirrorOverlayHandle) {
        applicationScope.launch {
            mutex.withLock {
                val current = activeScenes[handle.scene.reason]
                if (current?.handle !== handle) return@withLock
                activeScenes.remove(handle.scene.reason)
                overlayController.hide(handle)
                resetRetryLocked()
                WjxFileLogger.w(TAG, "unexpected output loss reason=${handle.scene.reason}")
                reconcileLocked()
            }
        }
    }

    private fun onFrameHubFailure(error: Throwable) {
        applicationScope.launch {
            mutex.withLock {
                if (frameHub == null) return@withLock
                val desired = desiredScenesLocked()
                WjxFileLogger.e(TAG, "shared frame renderer failed: ${error.message}", error)
                stopSessionLocked("相机画面渲染失败", publishStatus = false)
                if (desired.isNotEmpty() && !cameraPriorityActive()) {
                    scheduleRetryLocked(desired, "相机画面渲染失败")
                } else {
                    setInactiveStatus("相机画面渲染失败")
                }
            }
        }
    }

    private fun startFrameWatchdogLocked(hub: ElectronicMirrorFrameHub) {
        frameWatchdogJob?.cancel()
        frameWatchdogJob = applicationScope.launch {
            while (true) {
                delay(FRAME_WATCHDOG_INTERVAL_MS)
                val lastFrameAt = hub.lastFrameAtMs
                // No first-frame gate: opening a window never waits for or reveals on frame one.
                if (lastFrameAt <= 0L || SystemClock.elapsedRealtime() - lastFrameAt <= FRAME_STALL_TIMEOUT_MS) {
                    continue
                }
                mutex.withLock {
                    if (frameHub !== hub) return@withLock
                    val desired = desiredScenesLocked()
                    frameWatchdogJob = null
                    stopSessionLocked("画面已中断", cancelWatchdog = false, publishStatus = false)
                    if (desired.isNotEmpty() && !cameraPriorityActive()) {
                        scheduleRetryLocked(desired, "超过3秒未收到新画面")
                    } else {
                        setInactiveStatus("画面已中断")
                    }
                }
                return@launch
            }
        }
    }

    private fun scheduleRetryLocked(scenes: List<ElectronicMirrorScene>, cause: String) {
        if (scenes.isEmpty()) {
            setInactiveStatus(cause)
            return
        }
        if (retryScenes != null && retryScenes != scenes) resetRetryLocked()
        retryJob?.cancel()
        retryJob = null
        retryScenes = scenes
        consecutiveFailures += 1
        WjxFileLogger.w(
            TAG,
            "camera unavailable failure=$consecutiveFailures cause=$cause " +
                "outputs=${scenes.joinToString { it.reason.name }}",
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
                if (retryScenes != scenes) return@withLock
                retryJob = null
                retryScenes = null
                reconcileLocked()
            }
        }
    }

    private fun resetRetryLocked() {
        retryJob?.cancel()
        retryJob = null
        retryScenes = null
        consecutiveFailures = 0
    }

    private suspend fun ensureOverlayPermission(): Boolean {
        if (overlayPermissionReady && Settings.canDrawOverlays(appContext)) return true
        helper.grantOverlayPermission()
        if (Settings.canDrawOverlays(appContext)) {
            overlayPermissionReady = true
            return true
        }
        repeat(10) {
            delay(200L)
            if (Settings.canDrawOverlays(appContext)) {
                overlayPermissionReady = true
                return true
            }
        }
        return false
    }

    private fun setActiveStatus(scenes: List<ElectronicMirrorScene>) {
        if (scenes.isEmpty()) {
            setInactiveStatus("待机")
            return
        }
        val labels = scenes.joinToString("、") { scene ->
            when (scene.reason) {
                ElectronicMirrorReason.LEFT_TURN -> "左后侧"
                ElectronicMirrorReason.RIGHT_TURN -> "右后侧"
                ElectronicMirrorReason.STREAMING_REAR -> "流媒体后视"
                ElectronicMirrorReason.REVERSE -> "倒挡后视"
                ElectronicMirrorReason.MANUAL_PREVIEW -> "${scene.viewpoint.displayName}预览"
            }
        }
        val targets = scenes.map(ElectronicMirrorScene::target).distinct()
        val targetText = targets.joinToString("+") { targetName(it) }
        _status.value = ElectronicMirrorRuntimeStatus(
            active = true,
            message = "$labels · $targetText",
            viewpoint = scenes.singleOrNull()?.viewpoint,
            target = targets.singleOrNull(),
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

    private data class CameraRoute(
        val cameraTag: String,
        val surfaceIndex: Int,
        val tileCount: Int,
        val bufferWidth: Int,
        val bufferHeight: Int,
        val fps: Int,
    ) {
        fun matches(scene: ElectronicMirrorScene): Boolean {
            val profile = scene.surfaceProfile.sanitized()
            return cameraTag == profile.cameraTag &&
                surfaceIndex == profile.surfaceIndex &&
                tileCount == profile.tileCount &&
                bufferWidth == profile.bufferWidth &&
                bufferHeight == profile.bufferHeight &&
                fps == scene.fps
        }

        fun request(): ElectronicMirrorCameraRequest = ElectronicMirrorCameraRequest(
            cameraTag = cameraTag,
            surfaceIndex = surfaceIndex,
            tileCount = tileCount,
            bufferWidth = bufferWidth,
            bufferHeight = bufferHeight,
            fps = fps,
        )

        companion object {
            fun from(scene: ElectronicMirrorScene): CameraRoute {
                val profile = scene.surfaceProfile.sanitized()
                return CameraRoute(
                    cameraTag = profile.cameraTag,
                    surfaceIndex = profile.surfaceIndex,
                    tileCount = profile.tileCount,
                    bufferWidth = profile.bufferWidth,
                    bufferHeight = profile.bufferHeight,
                    fps = scene.fps,
                )
            }
        }
    }

    private companion object {
        const val TAG = "ElectronicMirror"
        const val AUTO_PARKING_ACTIVE = 4
        const val CAMERA_INPUT_SURFACE_TIMEOUT_MS = 3_000L
        const val FRAME_STALL_TIMEOUT_MS = 3_000L
        const val FRAME_WATCHDOG_INTERVAL_MS = 1_000L
        const val CAMERA_REATTACH_GUARD_MS = 360L
        const val MAX_AUTOMATIC_RETRIES = 5
    }
}

internal fun electronicMirrorRetryDelayMs(failureCount: Int): Long {
    val exponent = (failureCount - 1).coerceIn(0, 4)
    return 1_000L shl exponent
}
