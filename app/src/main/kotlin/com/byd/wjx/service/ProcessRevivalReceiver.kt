package com.byd.wjx.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.byd.wjx.log.WjxFileLogger

/**
 * AlarmManager watchdog. If Android kills the app process but does not force-stop it,
 * the next alarm resurrects the process and kicks [KeepAliveService] again.
 */
class ProcessRevivalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val trigger = intent.action ?: intent.dataString ?: "unknown"
        Log.i(TAG, "revival fired trigger=$trigger data=${intent.dataString}")
        WjxFileLogger.i(TAG, "revival fired trigger=$trigger data=${intent.dataString}")
        ChainLog.append(appContext, "ProcessRevival fired: $trigger")

        schedule(appContext)
        if (!BootState.isUserUnlocked(appContext)) {
            ChainLog.append(appContext, "ProcessRevival deferred: user locked")
            return
        }
        if (intent.action != ACTION && !StartupThrottle.shouldStart()) {
            ChainLog.append(appContext, "ProcessRevival debounced: $trigger")
            return
        }
        runCatching {
            KeepAliveService.start(appContext, "revival:$trigger")
        }.onFailure {
            Log.w(TAG, "KeepAliveService start failed: ${it.message}")
            WjxFileLogger.w(TAG, "KeepAliveService start failed: ${it.message}")
            ChainLog.append(appContext, "ProcessRevival start FAILED: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "ProcessRevival"
        private const val ACTION = "com.byd.wjx.action.PROCESS_REVIVAL"
        private const val INTERVAL_MS = 5 * 60 * 1000L
        private const val BACKUP_OFFSET_MS = 3 * 60 * 1000L
        private const val PRIMARY_REQUEST_CODE = 0x5701
        private const val BACKUP_REQUEST_CODE = 0x5702

        fun schedule(context: Context) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val now = System.currentTimeMillis()
            scheduleOne(alarmManager, appContext, PRIMARY_REQUEST_CODE, "primary", now + INTERVAL_MS, flags)
            scheduleOne(
                alarmManager,
                appContext,
                BACKUP_REQUEST_CODE,
                "backup",
                now + INTERVAL_MS + BACKUP_OFFSET_MS,
                flags,
            )
            ChainLog.append(appContext, "ProcessRevival scheduled")
        }

        private fun scheduleOne(
            alarmManager: AlarmManager,
            context: Context,
            requestCode: Int,
            tag: String,
            triggerAtMs: Long,
            flags: Int,
        ) {
            val intent = Intent(context, ProcessRevivalReceiver::class.java).apply {
                action = ACTION
                data = Uri.parse("wjx://revival/$tag")
            }
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } catch (e: SecurityException) {
                Log.w(TAG, "exact alarm denied, fallback: ${e.message}")
                WjxFileLogger.w(TAG, "exact alarm denied, fallback tag=$tag: ${e.message}")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }
    }
}
