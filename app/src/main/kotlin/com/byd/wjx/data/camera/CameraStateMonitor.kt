package com.byd.wjx.data.camera

import com.byd.wjx.data.foreground.ForegroundAppMonitor
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Derives camera/full-screen parking state from the process-wide foreground-app stream. */
@Singleton
class CameraStateMonitor @Inject constructor(
    private val foregroundAppMonitor: ForegroundAppMonitor,
) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        foregroundAppMonitor.start(scope)
        job = scope.launch {
            foregroundAppMonitor.foreground
                .map { foreground ->
                    CameraForegroundMatcher.isCameraWindow(
                        foreground.packageName,
                        foreground.className,
                    ) to foreground
                }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (cameraActive, foreground) ->
                    _active.value = cameraActive
                    WjxFileLogger.i(
                        TAG,
                        "cameraActive=$cameraActive pkg=${foreground.packageName} cls=${foreground.className}",
                    )
                }
        }
        WjxFileLogger.i(TAG, "start")
    }

    fun requestImmediateRescan(reason: String) {
        foregroundAppMonitor.requestImmediateRescan(reason)
    }

    fun stop() {
        job?.cancel()
        job = null
        _active.value = false
        foregroundAppMonitor.stop()
        WjxFileLogger.i(TAG, "stop")
    }

    private companion object {
        const val TAG = "CameraMonitor"
    }
}
