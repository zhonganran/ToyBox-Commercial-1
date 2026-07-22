package com.byd.wjx.cluster

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.audio.WjxRealtimeHailingService
import com.byd.wjx.data.foreground.AccessibilityForegroundBridge
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Steering-wheel key filter for cluster projection. When the settings master switch
 * ([ClusterProjectionManager.KEY_MIRROR_ENABLED]) is ON, a short press of the configured trigger
 * button (keycode in [ClusterProjectionManager.KEY_TRIGGER_KEYCODE], default
 * [DEFAULT_TRIGGER_KEYCODE] = the right star) toggles the selected navigation app between the cluster and the centre
 * screen and is consumed; every other key (switch OFF, a non-trigger button, the right-star
 * long-press that opens the native action menu) passes through untouched.
 *
 * Inert unless enabled in secure settings (self-enabled via the daemon when the switch turns on —
 * no Accessibility UI on DiLink) AND the settings switch is on, so it does nothing for users who
 * never opt in.
 */
class SteeringWheelKeyService : AccessibilityService() {

    private var cachedEntryPoint: ClusterEntryPoint? = null
    private val handler = Handler(Looper.getMainLooper())
    private val projectionPreferences by lazy { ClusterProjectionPreferences(applicationContext) }
    private val shortcutSettings: KeyShortcutSettings by lazy { KeyShortcutSettings(applicationContext) }
    private val hailingSettings: ExteriorHailingSettings by lazy { ExteriorHailingSettings(applicationContext) }
    private var pendingDoubleKeyCode: Int? = null
    private var pendingDoubleAt: Long = 0L
    private var pendingSingleToken: Int = 0
    private var pendingLongKeyCode: Int? = null
    private var pendingLongToken: Int = 0
    private var longTriggeredKeyCode: Int? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
        AccessibilityForegroundBridge.onServiceConnected(this)
        instance = this
        isConnected = true
        WjxFileLogger.i(TAG, "connected; filtering steering-wheel keys")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (learnMode) {
            return when (learnDecision(event.keyCode, isDown, learnPreferLongPress)) {
                LearnAction.CAPTURE -> {
                    capturedKey.value = CaptureResult(event.keyCode, assignable = true)
                    WjxFileLogger.i(TAG, "learn captured keyCode=${event.keyCode} long=$learnPreferLongPress")
                    learnMode = false  // got it; dialog moves to the confirm step
                    learnPreferLongPress = false
                    true
                }
                LearnAction.REJECT -> {
                    capturedKey.value = CaptureResult(event.keyCode, assignable = false)
                    WjxFileLogger.w(TAG, "learn rejected keyCode=${event.keyCode}")
                    true  // stay in learn mode; dialog shows "can't assign", waits for another key
                }
                LearnAction.CONSUME -> true
            }
        }
        handleKeyShortcut(event)?.let { return it }
        val enabled = projectionPreferences.isEnabled()
        val trigger = projectionPreferences.triggerKeyCode()
        val ep = if (enabled) entryPoint() else null
        val activationAllowed = ep?.activationManager()?.canUse(PremiumFeature.CLUSTER_PROJECTION) ?: enabled
        return when (starDecision(event.keyCode, isDown, enabled && activationAllowed, trigger)) {
            StarDecision.CONSUME_AND_TOGGLE -> {
                val activeEntryPoint = ep ?: entryPoint()
                WjxFileLogger.i(TAG, "projection trigger key=${event.keyCode} enabled=$enabled activationAllowed=$activationAllowed")
                ClusterProjectionManager.toggle(applicationContext, activeEntryPoint.helperClient(), activeEntryPoint.helperBootstrap())
                true
            }
            StarDecision.CONSUME -> true
            StarDecision.PASS_THROUGH -> false
        }
    }

    private fun handleKeyShortcut(event: KeyEvent): Boolean? {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val pendingKey = pendingDoubleKeyCode
            if (pendingKey != null && pendingKey != event.keyCode) {
                clearPendingShortcut()
            }
            val pendingLongKey = pendingLongKeyCode
            if (pendingLongKey != null && pendingLongKey != event.keyCode) {
                clearPendingLong()
            }
        }
        val bindings = shortcutSettings.loadEnabled().filter { it.keyCode == event.keyCode }
        if (bindings.isEmpty()) return null
        if (!entryPoint().activationManager().canUse(PremiumFeature.KEY_SHORTCUT_ACTIONS)) {
            clearPendingShortcut()
            clearPendingLong()
            WjxFileLogger.i(TAG, "shortcut ignored: activation locked key=${event.keyCode}")
            return null
        }
        if (event.action == KeyEvent.ACTION_UP) {
            return handleKeyShortcutUp(event, bindings)
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0) return true

        val single = bindings.firstOrNull { it.pressType == KeyShortcutPressType.Short }
        val double = bindings.firstOrNull { it.pressType == KeyShortcutPressType.Double }
        val long = bindings.firstOrNull { it.pressType == KeyShortcutPressType.Long }
        if (long != null) {
            if (event.keyCode in DEDICATED_LONG_KEYCODES) {
                clearPendingShortcut()
                clearPendingLong()
                longTriggeredKeyCode = event.keyCode
                executeShortcut(long)
            } else {
                armLongShortcut(event.keyCode, long)
            }
            return true
        }

        handleTapShortcut(event.keyCode, single, double)
        return true
    }

    private fun handleKeyShortcutUp(event: KeyEvent, bindings: List<KeyShortcutBinding>): Boolean {
        val long = bindings.firstOrNull { it.pressType == KeyShortcutPressType.Long }
        val single = bindings.firstOrNull { it.pressType == KeyShortcutPressType.Short }
        val double = bindings.firstOrNull { it.pressType == KeyShortcutPressType.Double }
        if (long == null) return true

        val longAlreadyFired = longTriggeredKeyCode == event.keyCode
        clearPendingLong()
        if (longAlreadyFired) {
            longTriggeredKeyCode = null
            return true
        }
        handleTapShortcut(event.keyCode, single, double)
        return true
    }

    private fun handleTapShortcut(
        keyCode: Int,
        single: KeyShortcutBinding?,
        double: KeyShortcutBinding?,
    ) {
        if (double == null) {
            single?.let {
                WjxFileLogger.i(TAG, "shortcut short fire key=$keyCode binding=${it.id}")
                executeShortcut(it)
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        if (pendingDoubleKeyCode == keyCode && now - pendingDoubleAt <= DOUBLE_PRESS_TIMEOUT_MS) {
            pendingSingleToken++
            pendingDoubleKeyCode = null
            pendingDoubleAt = 0L
            WjxFileLogger.i(TAG, "shortcut double fire key=$keyCode binding=${double.id}")
            executeShortcut(double)
            return
        }

        pendingDoubleKeyCode = keyCode
        pendingDoubleAt = now
        if (single != null) {
            val token = ++pendingSingleToken
            handler.postDelayed({
                if (token == pendingSingleToken && pendingDoubleKeyCode == keyCode) {
                    pendingDoubleKeyCode = null
                    pendingDoubleAt = 0L
                    executeShortcut(single)
                }
            }, DOUBLE_PRESS_TIMEOUT_MS)
        } else {
            val token = ++pendingSingleToken
            handler.postDelayed({
                if (token == pendingSingleToken && pendingDoubleKeyCode == keyCode) {
                    pendingDoubleKeyCode = null
                    pendingDoubleAt = 0L
                }
            }, DOUBLE_PRESS_TIMEOUT_MS)
        }
    }

    private fun clearPendingShortcut() {
        pendingSingleToken++
        pendingDoubleKeyCode = null
        pendingDoubleAt = 0L
    }

    private fun armLongShortcut(keyCode: Int, binding: KeyShortcutBinding) {
        pendingLongKeyCode = keyCode
        longTriggeredKeyCode = null
        val token = ++pendingLongToken
        handler.postDelayed({
            if (token == pendingLongToken && pendingLongKeyCode == keyCode) {
                pendingLongKeyCode = null
                longTriggeredKeyCode = keyCode
                clearPendingShortcut()
                WjxFileLogger.i(TAG, "shortcut long fire key=$keyCode binding=${binding.id}")
                executeShortcut(binding)
            }
        }, LONG_PRESS_TIMEOUT_MS)
    }

    private fun clearPendingLong() {
        pendingLongToken++
        pendingLongKeyCode = null
    }

    private fun executeShortcut(binding: KeyShortcutBinding) {
        val profile = entryPoint().carProfileDetector().detect()
        val channel = hailingSettings.effectiveChannel(profile)
        when (binding.actionType) {
            KeyShortcutActionType.PlaySound -> {
                val file = File(binding.soundPath)
                if (!file.exists()) {
                    WjxFileLogger.w(TAG, "shortcut sound missing: ${binding.soundPath}")
                    return
                }
                WjxFileLogger.i(TAG, "shortcut play sound binding=${binding.id} file=${file.name} channel=$channel")
                WjxOutsidePlayService.toggle(
                    applicationContext,
                    file,
                    channel,
                    owner = "key_shortcut:${binding.id}",
                )
            }
            KeyShortcutActionType.RealtimeHailing -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    WjxFileLogger.w(TAG, "shortcut hailing ignored: RECORD_AUDIO not granted binding=${binding.id}")
                    return
                }
                WjxFileLogger.i(TAG, "shortcut toggle hailing binding=${binding.id} channel=$channel")
                WjxOutsidePlayService.stop(applicationContext)
                WjxRealtimeHailingService.toggle(applicationContext, channel)
            }
            KeyShortcutActionType.StreamingRearMirror -> {
                val visible = entryPoint().electronicMirrorCoordinator()
                    .toggleStreamingRearFromShortcut()
                WjxFileLogger.i(
                    TAG,
                    "shortcut streaming rear binding=${binding.id} visible=$visible",
                )
            }
        }
    }

    private fun entryPoint(): ClusterEntryPoint =
        cachedEntryPoint ?: EntryPointAccessors
            .fromApplication(applicationContext, ClusterEntryPoint::class.java)
            .also { cachedEntryPoint = it }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        AccessibilityForegroundBridge.onAccessibilityEvent(event)
    }
    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        AccessibilityForegroundBridge.onServiceDisconnected(this)
        if (instance === this) instance = null
        clearPendingShortcut()
        clearPendingLong()
        WjxFileLogger.i(TAG, "unbound; star key filter inactive")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        AccessibilityForegroundBridge.onServiceDisconnected(this)
        if (instance === this) instance = null
        clearPendingShortcut()
        clearPendingLong()
        WjxFileLogger.i(TAG, "destroyed")
        super.onDestroy()
    }

    companion object {
        const val TAG = "SteeringWheelKeySvc"

        /**
         * True liveness signal. Set when the framework actually binds + connects this service (the
         * only moment the key filter is live), cleared on unbind/destroy. The Secure-settings
         * enabled-list is NOT reliable across boot/wake, so the re-assert watchdog in TrackingService
         * gates on THIS first. Mirrors OpenBYD's SteeringWheelAccessibilityService.isConnected.
         */
        @Volatile
        var isConnected: Boolean = false
            private set

        @Volatile
        private var instance: SteeringWheelKeyService? = null

        /**
         * Learn mode: when true, the next steering-wheel key is captured into [capturedKey] instead
         * of toggling projection, and every key is consumed so its native action can't fire. Set by
         * the settings learn dialog; cleared by the service on CAPTURE or by the dialog on dismiss.
         */
        @Volatile
        var learnMode: Boolean = false

        @Volatile
        var learnPreferLongPress: Boolean = false

        /** Result of a learn-mode key press; the settings dialog (same process) collects this. */
        data class CaptureResult(val keyCode: Int, val assignable: Boolean)

        /** Last captured key while learning; null = nothing captured yet. */
        val capturedKey = MutableStateFlow<CaptureResult?>(null)

        fun resetShortcutRuntimeState() {
            val service = instance ?: return
            service.handler.post {
                service.clearPendingShortcut()
                service.clearPendingLong()
                service.longTriggeredKeyCode = null
            }
        }

        private const val DOUBLE_PRESS_TIMEOUT_MS = 400L
        private const val LONG_PRESS_TIMEOUT_MS = 600L
    }
}
