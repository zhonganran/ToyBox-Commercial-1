package com.byd.wjx.service

import android.content.Context
import android.provider.Settings
import com.byd.wjx.cluster.SteeringWheelKeyService
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.overlay.AutoDoorFloatingService
import com.byd.wjx.overlay.SoundFloatingService
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One place that owns the background stack startup order.
 * KeepAliveService, BootReceiver and future remote/small-program entrypoints
 * should kick this instead of each starting random pieces.
 */
class DaemonStartupManager(
    private val context: Context,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
    private val trackingStateRepository: TrackingStateRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    @Volatile private var lastOverlayGranted: Boolean? = null
    private val runtimeCheckJobLock = Any()
    private var runtimeCheckJob: Job? = null

    fun start(reason: String) {
        val verbose = reason != REASON_HEALTH_CHECK
        if (verbose) {
            ChainLog.append(appContext, "DaemonStartup start: $reason")
            WjxFileLogger.i(TAG, "start reason=$reason")
        }
        if (!BootState.isUserUnlocked(appContext)) {
            ChainLog.append(appContext, "DaemonStartup deferred: user locked")
            WjxFileLogger.i(TAG, "deferred: user locked reason=$reason")
            return
        }

        if (verbose || !trackingStateRepository.isRunning.value) {
            runCatching { TrackingService.start(appContext) }
                .onSuccess {
                    if (verbose) ChainLog.append(appContext, "DaemonStartup tracking OK")
                    WjxFileLogger.i(TAG, "TrackingService start requested reason=$reason")
                }
                .onFailure {
                    ChainLog.append(appContext, "DaemonStartup tracking FAILED: ${it.message}")
                    WjxFileLogger.w(TAG, "TrackingService start failed: ${it.message}")
                }
        }

        if (verbose) {
            runCatching { SoundFloatingService.startIfAllowed(appContext) }
                .onSuccess { WjxFileLogger.i(TAG, "SoundFloatingService startIfAllowed requested") }
                .onFailure { WjxFileLogger.w(TAG, "floating start failed: ${it.message}") }

            runCatching { AutoDoorFloatingService.startIfAllowed(appContext) }
                .onSuccess { WjxFileLogger.i(TAG, "AutoDoorFloatingService startIfAllowed requested") }
                .onFailure { WjxFileLogger.w(TAG, "auto door floating start failed: ${it.message}") }

        }

        if (!shouldCheckRuntime()) {
            if (verbose) ChainLog.append(appContext, "DaemonStartup runtime check skipped by throttle")
            if (verbose) WjxFileLogger.i(TAG, "runtime check skipped by throttle reason=$reason")
            return
        }
        val scheduled = scheduleRuntimeCheck(reason)
        if (!scheduled && verbose) {
            ChainLog.append(appContext, "DaemonStartup runtime check joined existing run")
            WjxFileLogger.i(TAG, "runtime check already running reason=$reason")
        }
    }

    private fun scheduleRuntimeCheck(reason: String): Boolean {
        val newJob = synchronized(runtimeCheckJobLock) {
            if (runtimeCheckJob?.isCompleted == false) return false
            scope.launch(start = CoroutineStart.LAZY) { checkRuntime(reason) }
                .also { runtimeCheckJob = it }
        }
        newJob.start()
        return true
    }

    private suspend fun checkRuntime(reason: String) {
        try {
            val helperAlive = helperBootstrap.ensureCurrent()
            if (reason != REASON_HEALTH_CHECK || !helperAlive) {
                ChainLog.append(appContext, "DaemonStartup helper($reason): $helperAlive")
                WjxFileLogger.i(TAG, "helper reason=$reason alive=$helperAlive")
            }
            if (!helperAlive) return

            if (KeepAliveAccessibilityService.isConnected && SteeringWheelKeyService.isConnected) {
                if (reason != REASON_HEALTH_CHECK) {
                    ChainLog.append(appContext, "DaemonStartup a11y already connected")
                    WjxFileLogger.i(TAG, "a11y already connected")
                }
            } else {
                val a11yEnabled = helperClient.enableAccessibilityService()
                ChainLog.append(appContext, "DaemonStartup a11y($reason): $a11yEnabled")
                WjxFileLogger.i(TAG, "a11y enable requested reason=$reason enabled=$a11yEnabled")
            }

            val overlayGranted = runCatching { Settings.canDrawOverlays(appContext) }.getOrDefault(false)
            if (reason != REASON_HEALTH_CHECK || lastOverlayGranted != overlayGranted) {
                ChainLog.append(appContext, "DaemonStartup overlayPermission=$overlayGranted")
                WjxFileLogger.i(TAG, "overlayPermission=$overlayGranted")
            }
            lastOverlayGranted = overlayGranted
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "runtime check failed: ${e.message}", e)
            ChainLog.append(appContext, "DaemonStartup runtime FAILED: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DaemonStartup"
        const val REASON_HEALTH_CHECK = "health_check"
        private const val MIN_RUNTIME_CHECK_INTERVAL_MS = 5_000L
        private val runtimeCheckLock = Any()
        @Volatile private var lastRuntimeCheckMs = 0L

        private fun shouldCheckRuntime(): Boolean = synchronized(runtimeCheckLock) {
            val now = System.currentTimeMillis()
            if (now - lastRuntimeCheckMs < MIN_RUNTIME_CHECK_INTERVAL_MS) return@synchronized false
            lastRuntimeCheckMs = now
            true
        }
    }
}
