package com.byd.wjx.service.runtime

import android.content.Context
import android.util.Log
import com.byd.wjx.data.autoservice.AdbOnDeviceClient
import com.byd.wjx.data.camera.CameraStateMonitor
import com.byd.wjx.data.repository.TrackingStateStore
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owns camera-foreground monitoring and publishes it into runtime state. */
@Singleton
class CameraRuntimeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adbOnDeviceClient: AdbOnDeviceClient,
    private val cameraStateMonitor: CameraStateMonitor,
    private val trackingStateStore: TrackingStateStore,
) {
    val active: StateFlow<Boolean> = cameraStateMonitor.active

    private var stateJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (stateJob?.isActive == true) return
        scope.launch {
            grantUsageStatsAppOp()
            // Re-read the current foreground after the AppOps check/grant. This also handles a
            // fresh install whose first UsageEvents query raced ahead of ADB authorization.
            cameraStateMonitor.requestImmediateRescan("usage_stats_checked")
        }
        cameraStateMonitor.start(scope)
        stateJob = scope.launch {
            cameraStateMonitor.active.collect(trackingStateStore::setCameraActive)
        }
    }

    fun stop() {
        stateJob?.cancel()
        stateJob = null
        cameraStateMonitor.stop()
        trackingStateStore.setCameraActive(false)
    }

    private suspend fun grantUsageStatsAppOp() {
        try {
            if (adbOnDeviceClient.connect().isSuccess) {
                val granted = adbOnDeviceClient.grantUsageStatsAppop(context.packageName)
                Log.i(TAG, "GET_USAGE_STATS appop grant: $granted")
                WjxFileLogger.i(TAG, "GET_USAGE_STATS appop grant=$granted")
            } else {
                Log.w(TAG, "ADB connect refused; camera detection may be inactive")
                WjxFileLogger.w(TAG, "ADB connect refused; camera detection may be inactive")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ADB appop grant failed: ${e.message}")
            WjxFileLogger.w(TAG, "ADB appop grant failed: ${e.message}", e)
        }
    }

    private companion object {
        const val TAG = "CameraRuntime"
    }
}
