package com.byd.wjx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.byd.wjx.SuperToyBoxApp
import com.byd.wjx.log.WjxFileLogger

/**
 * Auto-start entrypoint. System events request KeepAliveService once; WorkManager is
 * reserved for the exceptional case where that foreground-service request throws.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        const val PREFS_NAME = "boot_log"
        const val KEY_LAST_BOOT_TS = "last_boot_ts"
        const val KEY_LAST_BOOT_METHOD = "last_boot_method"
        const val KEY_LAST_BOOT_ACTION = "last_boot_action"
        const val KEY_CHAIN_LOG = "chain_log"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
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
            "com.byd.action.ACC_ON",
            "com.byd.action.ACC_OFF",
            "com.byd.action.IGN_ON",
            "com.byd.accmode.ACC_MODE_CHANGED",
            "android.net.wifi.STATE_CHANGE",
            "android.net.conn.CONNECTIVITY_CHANGE",
        )
        if (intent.action !in validActions) return

        WjxFileLogger.i(TAG, "Boot/user event: ${intent.action}")
        ChainLog.append(context, "BootReceiver: ${intent.action}")

        logBootEvent(context, intent.action ?: "unknown")
        ProcessRevivalReceiver.schedule(context.applicationContext)
        if (!BootState.isUserUnlocked(context)) {
            updateBootMethod(context, "DirectBootDeferred")
            ChainLog.append(context, "DirectBoot deferred until USER_UNLOCKED")
            WjxFileLogger.i(TAG, "DirectBoot deferred until USER_UNLOCKED action=${intent.action}")
            return
        }

        if (!StartupThrottle.shouldStart()) {
            ChainLog.append(context, "BootReceiver debounced: ${intent.action}")
            WjxFileLogger.i(TAG, "debounced action=${intent.action}")
            return
        }

        (context.applicationContext as? SuperToyBoxApp)?.ensureUnlockedRuntime()
        startFullStack(context, intent.action ?: "unknown")
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
        try {
            BootState.deviceProtectedContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_BOOT_TS, System.currentTimeMillis())
                .putString(KEY_LAST_BOOT_ACTION, action)
                .apply()
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "Failed to log boot event: ${e.message}")
        }
    }

    private fun updateBootMethod(context: Context, method: String) {
        try {
            BootState.deviceProtectedContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_BOOT_METHOD, method)
                .apply()
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "Failed to update boot method: ${e.message}")
        }
    }

}
