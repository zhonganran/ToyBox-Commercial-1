package com.byd.wjx.adbrescue

import android.content.Context
import com.byd.wjx.log.WjxFileLogger

object AdbRescueBootCoordinator {
    fun handle(context: Context, action: String) {
        if (!AdbRescueTriggerPolicy.shouldRun(action)) return
        val preferences = AdbRescuePreferences(context.applicationContext)
        if (!preferences.isGuardEnabled()) {
            WjxFileLogger.i(TAG, "boot action=$action skipped: guard disabled")
            return
        }
        if (!AdbRescueTriggerThrottle.shouldRun(action)) {
            WjxFileLogger.i(TAG, "boot action=$action skipped: vendor trigger throttled")
            return
        }
        val manager = AdbRescueManager(context.applicationContext)
        runCatching {
            val report = manager.enableBoth("BOOT_RECEIVER:$action", runWriteTest = false)
            WjxFileLogger.i(
                TAG,
                "boot action=$action allEnabled=${report.allModesEnabled} " +
                    "permission=${report.writeSecureSettingsGranted}",
            )
        }.onFailure {
            WjxFileLogger.w(TAG, "boot repair failed action=$action: ${it.message}", it)
        }
        if (AdbRescueTriggerPolicy.shouldScheduleRetries(action)) {
            runCatching { AdbRescueScheduler.scheduleAll(context) }
                .onFailure { WjxFileLogger.w(TAG, "retry scheduling failed: ${it.message}", it) }
        }
    }

    private const val TAG = "AdbRescueBoot"
}
