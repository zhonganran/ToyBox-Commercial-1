package com.byd.wjx.electronicmirror

import android.content.Context
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
                manualPreview = viewpoint
                reconcileLocked()
            }
        }
    }

    fun stopPreview() {
        applicationScope.launch {
            mutex.withLock {
                manualPreview = null
                reconcileLocked()
            }
        }
    }

    fun applyPreferences() {
        applicationScope.launch {
            mutex.withLock {
                currentConfig = preferences.snapshot()
                reconcileLocked(forceLayoutRefresh = true)
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
                stopActiveLocked("服务停止")
            }
        }
    }

    private suspend fun reconcileLocked(forceLayoutRefresh: Boolean = false) {
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
            val cameraOwnsDisplay = cameraPriorityActive()
            if (current != null) stopActiveLocked(if (cameraOwnsDisplay) "原车影像优先" else "待机")
            else setInactiveStatus(if (cameraOwnsDisplay) "原车影像占用，电子后视镜让行" else "待机")
            return
        }
        if (!forceLayoutRefresh && current?.scene == desired) return

        val sameWindow = !forceLayoutRefresh && current != null &&
            current.scene.target == desired.target &&
            current.scene.geometry == desired.geometry &&
            current.scene.fps == desired.fps
        if (sameWindow) {
            val switched = helper.switchElectronicMirrorView(desired.viewpoint.helperValue)
            if (switched) {
                overlayController.updateLabel(current.handle, desired)
                activeScene = current.copy(scene = desired)
                setActiveStatus(desired)
                return
            }
            WjxFileLogger.w(TAG, "view switch failed; rebuilding camera")
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
            setInactiveStatus("Helper 未连接，无法打开摄像头")
            return
        }
        if (cameraPriorityActive()) {
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }
        helper.grantOverlayPermission()
        if (!awaitOverlayPermission()) {
            setInactiveStatus("悬浮窗权限未生效")
            return
        }

        val handle = overlayController.show(scene)
        if (handle == null) {
            setInactiveStatus("无法创建${targetName(scene.target)}画面窗口")
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
            setInactiveStatus("摄像头画面 Surface 已失效")
            return
        }
        val started = helper.startElectronicMirror(surface, scene.viewpoint.helperValue, scene.fps)
        if (!started) {
            overlayController.hide(handle)
            setInactiveStatus("APA 摄像头启动失败，请查看日志")
            return
        }
        if (cameraPriorityActive()) {
            helper.stopElectronicMirror()
            overlayController.hide(handle)
            setInactiveStatus("原车影像占用，电子后视镜让行")
            return
        }

        activeScene = ActiveScene(scene, handle)
        ElectronicMirrorActivity.isActive = true
        _active.value = true
        setActiveStatus(scene)
        WjxFileLogger.i(TAG, "active reason=${scene.reason} view=${scene.viewpoint} target=${scene.target}")
    }

    private suspend fun stopActiveLocked(message: String) {
        val current = activeScene
        activeScene = null
        if (current != null) {
            helper.stopElectronicMirror()
            overlayController.hide(current.handle)
            WjxFileLogger.i(TAG, "inactive reason=$message")
        }
        ElectronicMirrorActivity.isActive = false
        _active.value = false
        setInactiveStatus(message)
    }

    private fun onUnexpectedSurfaceLoss(handle: ElectronicMirrorOverlayHandle) {
        applicationScope.launch {
            mutex.withLock {
                val current = activeScene
                if (current?.handle !== handle) return@withLock
                activeScene = null
                helper.stopElectronicMirror()
                ElectronicMirrorActivity.isActive = false
                _active.value = false
                setInactiveStatus("画面 Surface 已失效")
                WjxFileLogger.w(TAG, "unexpected Surface loss target=${handle.target}")
            }
        }
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
    }
}
