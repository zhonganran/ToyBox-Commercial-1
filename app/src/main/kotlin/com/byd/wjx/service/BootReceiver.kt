package com.byd.wjx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.byd.wjx.SuperToyBoxApp
import com.byd.wjx.adbrescue.AdbRescueBootCoordinator
import com.byd.wjx.log.WjxFileLogger

/**
 * Auto-start entrypoint. System events request KeepAliveService once; WorkManager is
 * reserved for the exceptional case where that foreground-service request throws.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        internal val SYSTEM_EVENTS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf(SYSTEM_EVENTS::contains) ?: return
        BootStartupCoordinator.handle(context, action, "system")
    }
}

internal object BootStartupCoordinator {
    private const val TAG = "BootReceiver"

    fun handle(context: Context, action: String, source: String) {
        WjxFileLogger.i(TAG, "boot event source=$source action=$action")
        ChainLog.append(context, "BootReceiver[$source]: $action")
        logBootEvent(context, action)
        if (!BootState.isUserUnlocked(context)) {
            if (com.byd.wjx.adbrescue.AdbRescueTriggerPolicy.shouldRunWhileUserLocked(action)) {
                AdbRescueBootCoordinator.handle(context, action)
            }
            updateBootMethod(context, "DirectBootDeferred")
            ChainLog.append(context, "DirectBoot deferred until USER_UNLOCKED")
            WjxFileLogger.i(TAG, "DirectBoot deferred until USER_UNLOCKED action=$action")
            return
        }

        if (!StartupThrottle.shouldStart()) {
            ChainLog.append(context, "BootReceiver debounced: $action")
            WjxFileLogger.i(TAG, "debounced source=$source action=$action")
            return
        }

        AdbRescueBootCoordinator.handle(context, action)
        ProcessRevivalReceiver.schedule(context.applicationContext)
        (context.applicationContext as? SuperToyBoxApp)?.ensureUnlockedRuntime()
        startFullStack(context, action)
    }

    private fun startFullStack(context: Context, action: String) {
        try {
            KeepAliveService.start(context.applicationContext, "broadcast:$action")
            updateBootMethod(context, "KeepAlive")
            ChainLog.append(context, "KeepAliveService start requested")
            WjxFileLogger.i(TAG, "KeepAliveService start requested action=$action")
        } catch (e: Exception) {
            WjxFileLogger.e(TAG, "KeepAlive start failed: ${e.message}", e)
            ChainLog.append(context, "KeepAlive FAILED: ${e.message}")
            try {
                val request = OneTimeWorkRequestBuilder<ServiceStartWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ServiceStartWorker.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
                updateBootMethod(context, "WorkManagerFallback")
                ChainLog.append(context, "WorkManager fallback enqueued")
            } catch (e2: Exception) {
                updateBootMethod(context, "FAILED: ${e2.message}")
                ChainLog.append(context, "WorkManager fallback FAILED: ${e2.message}")
                WjxFileLogger.e(TAG, "All start methods failed: ${e2.message}", e2)
            }
        }
    }

    private fun logBootEvent(context: Context, action: String) {
        if (!BootDiagnosticsStore.recordBootEvent(context, action)) {
            WjxFileLogger.w(TAG, "Failed to log boot event")
        }
    }

    private fun updateBootMethod(context: Context, method: String) {
        if (!BootDiagnosticsStore.updateBootMethod(context, method)) {
            WjxFileLogger.w(TAG, "Failed to update boot method")
        }
    }
}
