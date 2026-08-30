package com.byd.wjx.adbrescue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.byd.wjx.log.WjxFileLogger

class AdbRescueRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val delaySeconds = intent?.getIntExtra(AdbRescueScheduler.EXTRA_DELAY_SECONDS, -1) ?: -1
        if (!AdbRescuePreferences(context.applicationContext).isGuardEnabled()) {
            WjxFileLogger.i(TAG, "retry skipped delay=${delaySeconds}s: guard disabled")
            return
        }
        runCatching {
            AdbRescueManager(context.applicationContext).enableBoth(
                "ALARM_RETRY:${delaySeconds}s",
                runWriteTest = false,
            )
        }.onFailure {
            WjxFileLogger.w(TAG, "direct retry failed delay=${delaySeconds}s: ${it.message}", it)
        }
    }

    private companion object {
        const val TAG = "AdbRescueRetry"
    }
}
