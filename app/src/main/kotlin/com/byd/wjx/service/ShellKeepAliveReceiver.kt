package com.byd.wjx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.byd.wjx.SuperToyBoxApp
import com.byd.wjx.log.WjxFileLogger

/**
 * Explicit shell/system entrypoint used by the out-of-process runtime watchdog.
 *
 * The manifest protects this receiver with android.permission.DUMP, which the ADB shell
 * and system UID hold but ordinary third-party applications do not. The shell process can
 * therefore revive the non-exported [KeepAliveService] without exposing that service as a
 * public component.
 */
class ShellKeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val reason = intent.getStringExtra(EXTRA_REASON)?.take(120) ?: "shell_watchdog"
        WjxFileLogger.w(TAG, "shell keepalive kick reason=$reason")
        ChainLog.append(appContext, "ShellKeepAliveReceiver: $reason")

        ProcessRevivalReceiver.schedule(appContext)
        if (!BootState.isUserUnlocked(appContext)) {
            WjxFileLogger.i(TAG, "shell kick deferred: user locked")
            return
        }

        (appContext as? SuperToyBoxApp)?.ensureUnlockedRuntime()
        runCatching { KeepAliveService.start(appContext, "shell:$reason") }
            .onFailure {
                WjxFileLogger.e(TAG, "KeepAliveService start failed: ${it.message}", it)
                ProcessRevivalReceiver.scheduleImmediate(appContext, "shell_start_failed")
            }
    }

    companion object {
        private const val TAG = "ShellKeepAliveReceiver"
        private const val EXTRA_REASON = "reason"
    }
}
