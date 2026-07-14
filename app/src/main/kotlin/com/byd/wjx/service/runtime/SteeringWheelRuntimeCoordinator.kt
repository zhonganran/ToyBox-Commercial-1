package com.byd.wjx.service.runtime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.cluster.ClusterProjectionManager
import com.byd.wjx.cluster.KeyShortcutSettings
import com.byd.wjx.cluster.SteeringWheelKeyService
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.helper.HelperBinderProtocol
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps the steering-wheel accessibility bridge bound across startup and screen wakes. */
@Singleton
class SteeringWheelRuntimeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
    private val activationManager: ActivationManager,
) {
    private var scope: CoroutineScope? = null
    private var receiverRegistered = false

    private val screenWakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            scope?.launch { ensureRunning("wake:${intent?.action}") }
        }
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        registerScreenWakeReceiver()
        scope.launch { ensureRunning("startup") }
    }

    fun stop() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(screenWakeReceiver)
            } catch (e: Exception) {
                WjxFileLogger.w(TAG, "screen-wake receiver unregister failed: ${e.message}", e)
            }
        }
        receiverRegistered = false
        scope = null
    }

    private fun registerScreenWakeReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            context.registerReceiver(screenWakeReceiver, filter)
            receiverRegistered = true
            WjxFileLogger.i(TAG, "screen-wake receiver registered")
        } catch (e: Exception) {
            Log.w(TAG, "screen-wake receiver register failed: ${e.message}")
            WjxFileLogger.w(TAG, "screen-wake receiver register failed: ${e.message}", e)
        }
    }

    private suspend fun ensureRunning(reason: String) {
        val prefs = context.getSharedPreferences(ClusterProjectionManager.PREFS_NAME, Context.MODE_PRIVATE)
        val mirrorEnabled = prefs.getBoolean(ClusterProjectionManager.KEY_MIRROR_ENABLED, false)
        val mirrorAllowed = mirrorEnabled && activationManager.canUse(PremiumFeature.CLUSTER_PROJECTION)
        val shortcutAllowed = KeyShortcutSettings(context).loadEnabled().isNotEmpty() &&
            activationManager.canUse(PremiumFeature.KEY_SHORTCUT_ACTIONS)
        if (!mirrorAllowed && !shortcutAllowed) return

        repeat(REASSERT_ATTEMPTS) { attempt ->
            if (isRunning()) {
                if (attempt > 0) {
                    Log.i(TAG, "steering a11y confirmed running ($reason, try ${attempt + 1})")
                    WjxFileLogger.i(TAG, "steering a11y confirmed reason=$reason try=${attempt + 1}")
                }
                return
            }
            if (!helperBootstrap.ensureCurrent()) {
                Log.w(TAG, "steering a11y re-assert skipped: helper unreachable ($reason)")
                WjxFileLogger.w(TAG, "steering a11y helper unreachable reason=$reason")
            } else {
                val rebound = helperClient.enableAccessibilityService()
                Log.i(TAG, "steering a11y re-asserted ($reason, try ${attempt + 1}) setting=$rebound")
                WjxFileLogger.i(
                    TAG,
                    "steering a11y re-asserted reason=$reason try=${attempt + 1} setting=$rebound",
                )
            }
            delay(REASSERT_RETRY_MS)
        }
        if (!isRunning()) {
            Log.w(TAG, "steering a11y still not running after $REASSERT_ATTEMPTS tries ($reason)")
            WjxFileLogger.w(TAG, "steering a11y unavailable after retries reason=$reason")
        }
    }

    private fun isRunning(): Boolean = SteeringWheelKeyService.isConnected || isBound()

    private fun isBound(): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val component = ComponentName.unflattenFromString(
            HelperBinderProtocol.ACCESSIBILITY_SERVICE_COMPONENT,
        ) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id.orEmpty()) == component }
    }

    private companion object {
        const val TAG = "SteeringWheelRuntime"
        const val REASSERT_ATTEMPTS = 6
        const val REASSERT_RETRY_MS = 5_000L
    }
}
