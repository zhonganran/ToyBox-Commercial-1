package com.byd.wjx.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SentryInformReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INFORM) return
        val warn = intent.extras.warnLevel()
        WjxFileLogger.i(TAG, "received action=${intent.action} warn=$warn extras=${intent.extras.describeExtras()}")
        if (warn != WARN_ALARM) {
            WjxFileLogger.i(TAG, "skip sentry broadcast: not high risk warn=$warn")
            return
        }
        val appContext = context.applicationContext
        if (!LockSoundSettings(appContext).snapshot().sentryEnabled) {
            WjxFileLogger.i(TAG, "skip sentry broadcast: disabled")
            return
        }
        if (!dispatchGate.tryAcquire()) {
            WjxFileLogger.i(TAG, "skip sentry broadcast: throttled or already running")
            return
        }

        val pending = goAsync()
        receiverScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(appContext, ClusterEntryPoint::class.java)
                val activationManager = entryPoint.activationManager()
                val activationAllowed = activationManager.canUse(PremiumFeature.VEHICLE_EVENT_SOUNDS) ||
                    runCatching {
                        activationManager.refreshIfNeeded(forceAfterMs = ACTIVATION_RESTORE_REFRESH_MS)
                    }
                        .onFailure { WjxFileLogger.w(TAG, "activation restore refresh failed: ${it.message}", it) }
                        .getOrNull()
                        ?.let { it.activated || it.trialActive } == true
                if (!activationAllowed) {
                    WjxFileLogger.i(TAG, "skip sentry broadcast: activation locked")
                    return@launch
                }
                // Use the application singleton so sentry, keepalive and settings sync share one
                // mutex and one duplicate-suppression cache instead of racing separate instances.
                entryPoint.lockSoundConfigSync().sync("sentry_broadcast")
                val helper = entryPoint.helperClient()
                val ok = helper.playSentryEvent("broadcast warn=$warn")
                WjxFileLogger.i(TAG, "sentry helper play ok=$ok")
            } catch (e: Exception) {
                WjxFileLogger.w(TAG, "sentry helper play failed: ${e.message}", e)
            } finally {
                dispatchGate.release()
                pending.finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Bundle?.warnLevel(): Int {
        if (this == null) return -1
        listOf(
            "inform_warn",
            "informWarn",
            "inform_warning",
            "warn",
            "warn_level",
            "warning",
            "warning_level",
            "level",
        ).forEach { key ->
            val raw = get(key) ?: return@forEach
            val value = when (raw) {
                is Number -> raw.toInt()
                is String -> raw.trim().toIntOrNull()
                else -> null
            }
            if (value != null) return value
        }
        return -1
    }

    @Suppress("DEPRECATION")
    private fun Bundle?.describeExtras(): String {
        if (this == null) return "{}"
        return keySet().joinToString(prefix = "{", postfix = "}") { key ->
            val value = runCatching { get(key) }.getOrNull()
            "$key=$value"
        }.take(500)
    }

    companion object {
        private const val TAG = "SentryInformReceiver"
        private const val ACTION_INFORM = "com.byd.sentrymode.action_inform"
        private const val WARN_ALARM = 2
        private const val ACTIVATION_RESTORE_REFRESH_MS = 30_000L
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val dispatchGate = SentryDispatchGate()
    }
}

internal class SentryDispatchGate(
    private val clock: () -> Long = System::currentTimeMillis,
    private val minimumIntervalMs: Long = 10_000L,
) {
    private val lock = Any()
    private var inFlight = false
    private var lastAcceptedAt = 0L

    fun tryAcquire(): Boolean = synchronized(lock) {
        val now = clock()
        if (inFlight || now - lastAcceptedAt < minimumIntervalMs) return@synchronized false
        inFlight = true
        lastAcceptedAt = now
        true
    }

    fun release() = synchronized(lock) {
        inFlight = false
    }
}
