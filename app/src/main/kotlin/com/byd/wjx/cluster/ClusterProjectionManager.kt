package com.byd.wjx.cluster

import android.content.Context
import android.os.SystemClock
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes public projection requests and keeps the visible mode success-honest. */
object ClusterProjectionManager {
    const val PREFS_NAME = ClusterProjectionPreferences.PREFS_NAME
    const val KEY_MIRROR_ENABLED = ClusterProjectionPreferences.KEY_MIRROR_ENABLED
    const val KEY_TRIGGER_KEYCODE = ClusterProjectionPreferences.KEY_TRIGGER_KEYCODE
    const val KEY_WIDTH_PCT = ClusterProjectionPreferences.KEY_WIDTH_PCT
    const val KEY_HEIGHT_PCT = ClusterProjectionPreferences.KEY_HEIGHT_PCT
    const val KEY_OFFSET_X_PCT = ClusterProjectionPreferences.KEY_OFFSET_X_PCT
    const val KEY_OFFSET_Y_PCT = ClusterProjectionPreferences.KEY_OFFSET_Y_PCT
    const val KEY_SCALE_PCT = ClusterProjectionPreferences.KEY_SCALE_PCT
    const val KEY_PROJECTION_LAYOUT = ClusterProjectionPreferences.KEY_PROJECTION_LAYOUT
    const val KEY_TARGET_PACKAGE = ClusterProjectionPreferences.KEY_TARGET_PACKAGE
    const val KEY_TARGET_LABEL = ClusterProjectionPreferences.KEY_TARGET_LABEL

    private const val TAG = "ClusterProjection"
    private const val PROJECTION_FAILURE_COOLDOWN_MS = 1_500L
    private val RECOVERY_DELAYS_MS = longArrayOf(0L, 350L, 900L, 1_600L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private val runtime = ClusterProjectionRuntime()

    @Volatile
    var currentMode: ClusterMode = ClusterMode.OFF
        private set

    private var recoveryToken: Int = 0
    private var lastProjectionFailureAtMs: Long = 0L

    fun setMode(
        context: Context,
        mode: ClusterMode,
        helper: HelperClient,
        bootstrap: HelperBootstrap,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (mode == ClusterMode.OFF) cancelPendingRecovery()
                if (mode == ClusterMode.FULLSCREEN && isFailureCoolingDown()) {
                    WjxFileLogger.w(TAG, "projection request ignored during failure cooldown")
                    return@withLock
                }
                if (mode == currentMode) return@withLock
                WjxFileLogger.i(TAG, "setMode: $currentMode -> $mode")
                applyModeLocked(appContext, mode, helper, bootstrap)
            }
        }
    }

    fun toggle(context: Context, helper: HelperClient, bootstrap: HelperBootstrap) {
        setMode(context, nextMode(currentMode), helper, bootstrap)
    }

    fun enableStarControl(helper: HelperClient, bootstrap: HelperBootstrap) {
        scope.launch {
            if (!bootstrap.ensureCurrent()) {
                WjxFileLogger.e(TAG, "helper daemon not running; cannot self-enable a11y")
                return@launch
            }
            val enabled = helper.enableAccessibilityService()
            WjxFileLogger.i(TAG, "enableStarControl: a11y enabled=$enabled")
        }
    }

    fun prepareDuplicateProjection(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val ready = ClusterMirrorService.ensureProjection(appContext)
            WjxFileLogger.i(TAG, "prepare duplicate MediaProjection fallback ok=$ready")
        }
    }

    fun stopDuplicateProjectionFallback() {
        scope.launch { ClusterMirrorService.releaseProjectionAndStop() }
    }

    fun reproject(context: Context, helper: HelperClient, bootstrap: HelperBootstrap) {
        val appContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (currentMode != ClusterMode.FULLSCREEN) return@withLock
                WjxFileLogger.i(TAG, "reproject: in-place resize")
                val result = runtime.resize(appContext, helper, bootstrap) {
                    handleUnexpectedSurfaceDestroyed(appContext, helper, bootstrap)
                }
                if (result == ProjectionResizeResult.REBUILD_REQUIRED) {
                    WjxFileLogger.w(TAG, "in-place resize failed; rebuilding projection")
                    applyModeLocked(appContext, ClusterMode.FULLSCREEN, helper, bootstrap)
                }
            }
        }
    }

    fun readProjectionLayout(context: Context): ProjectionLayout =
        runtime.readProjectionLayout(context)

    fun setTriggerKeyCode(context: Context, keyCode: Int) {
        ClusterProjectionPreferences(context).setTriggerKeyCode(keyCode)
    }

    private suspend fun applyModeLocked(
        context: Context,
        mode: ClusterMode,
        helper: HelperClient,
        bootstrap: HelperBootstrap,
    ) {
        when (mode) {
            ClusterMode.OFF -> {
                runtime.stop(context, helper)
                currentMode = ClusterMode.OFF
            }
            ClusterMode.FULLSCREEN -> {
                val projected = runtime.project(context, mode, helper, bootstrap) {
                    handleUnexpectedSurfaceDestroyed(context, helper, bootstrap)
                }
                if (projected) {
                    lastProjectionFailureAtMs = 0L
                    currentMode = mode
                } else {
                    WjxFileLogger.e(TAG, "projection failed; falling back to OFF")
                    if (readProjectionLayout(context) == ProjectionLayout.INDEPENDENT) {
                        runtime.pullProjectedAppBack(context, helper, focus = true)
                    }
                    runtime.clearProjectedPackage()
                    lastProjectionFailureAtMs = SystemClock.elapsedRealtime()
                    currentMode = ClusterMode.OFF
                }
            }
        }
    }

    private fun handleUnexpectedSurfaceDestroyed(
        context: Context,
        helper: HelperClient,
        bootstrap: HelperBootstrap,
    ) {
        scope.launch {
            mutex.withLock {
                val shouldRecover = shouldRecoverProjection(context)
                runtime.releaseRemoteDisplayIfAlive(context, helper)
                if (shouldRecover) {
                    scheduleProjectionRecovery(context, helper, bootstrap, "surfaceDestroyed")
                }
            }
        }
    }

    private fun shouldRecoverProjection(context: Context): Boolean {
        val enabled = ClusterProjectionPreferences(context).isEnabled()
        return enabled && currentMode == ClusterMode.FULLSCREEN
    }

    private fun cancelPendingRecovery() {
        recoveryToken++
    }

    private fun isFailureCoolingDown(): Boolean {
        val failedAt = lastProjectionFailureAtMs
        return failedAt > 0L &&
            SystemClock.elapsedRealtime() - failedAt < PROJECTION_FAILURE_COOLDOWN_MS
    }

    private fun scheduleProjectionRecovery(
        context: Context,
        helper: HelperClient,
        bootstrap: HelperBootstrap,
        reason: String,
    ) {
        val appContext = context.applicationContext
        val token = ++recoveryToken
        scope.launch {
            for ((attempt, delayMs) in RECOVERY_DELAYS_MS.withIndex()) {
                if (delayMs > 0) delay(delayMs)
                mutex.withLock {
                    if (token != recoveryToken) return@launch
                    if (!shouldRecoverProjection(appContext)) return@launch
                    if (runtime.hasLiveProjection) return@launch
                    WjxFileLogger.w(
                        TAG,
                        "recover projection after $reason, attempt=${attempt + 1}",
                    )
                    val recovered = runtime.project(
                        appContext,
                        ClusterMode.FULLSCREEN,
                        helper,
                        bootstrap,
                    ) {
                        handleUnexpectedSurfaceDestroyed(appContext, helper, bootstrap)
                    }
                    if (recovered) {
                        currentMode = ClusterMode.FULLSCREEN
                        WjxFileLogger.i(TAG, "projection recovered after $reason")
                        return@launch
                    }
                }
            }
            mutex.withLock {
                if (token == recoveryToken && shouldRecoverProjection(appContext)) {
                    WjxFileLogger.e(
                        TAG,
                        "projection recovery exhausted after $reason; falling back to OFF",
                    )
                    runtime.clearProjectedPackage()
                    currentMode = ClusterMode.OFF
                }
            }
        }
    }
}
