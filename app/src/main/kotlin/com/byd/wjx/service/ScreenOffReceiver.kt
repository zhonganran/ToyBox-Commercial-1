package com.byd.wjx.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.byd.wjx.log.WjxFileLogger

/**
 * SCREEN_OFF cannot be relied on from the manifest. KeepAliveService registers
 * this dynamically so screen sleep also re-seeds the watchdog and helper stack.
 */
class ScreenOffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_OFF) return
        val appContext = context.applicationContext
        WjxFileLogger.i(TAG, "SCREEN_OFF")
        ChainLog.append(appContext, "ScreenOffReceiver")
        KeepAliveService.start(appContext, "screen_off")
    }

    companion object {
        private const val TAG = "ScreenOffReceiver"

        fun register(context: Context): ScreenOffReceiver {
            val receiver = ScreenOffReceiver()
            context.applicationContext.registerReceiver(
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
            )
            ChainLog.append(context, "ScreenOffReceiver registered")
            return receiver
        }

        fun unregister(context: Context, receiver: ScreenOffReceiver) {
            runCatching {
                context.applicationContext.unregisterReceiver(receiver)
            }.onFailure {
                WjxFileLogger.w(TAG, "unregister failed: ${it.message}")
            }
        }
    }
}
