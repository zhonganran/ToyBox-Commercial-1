package com.byd.wjx.adbrescue

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.byd.wjx.log.WjxFileLogger

internal object AdbRescueScheduler {
    val retryDelaysMs = longArrayOf(
        10_000L,
        30_000L,
        60_000L,
        120_000L,
        300_000L,
    )

    fun scheduleAll(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            WjxFileLogger.w(TAG, "AlarmManager unavailable")
            return
        }
        val now = SystemClock.elapsedRealtime()
        retryDelaysMs.forEachIndexed { index, delayMs ->
            val intent = Intent(appContext, AdbRescueRetryReceiver::class.java)
                .setAction(ACTION_RETRY)
                .putExtra(EXTRA_DELAY_SECONDS, (delayMs / 1_000L).toInt())
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE_BASE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                now + delayMs,
                pendingIntent,
            )
        }
        WjxFileLogger.i(TAG, "scheduled retries at 10,30,60,120,300 seconds")
    }

    fun cancelAll(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        retryDelaysMs.indices.forEach { index ->
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE_BASE + index,
                Intent(appContext, AdbRescueRetryReceiver::class.java).setAction(ACTION_RETRY),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        WjxFileLogger.i(TAG, "cancelled pending ADB rescue retries")
    }

    const val EXTRA_DELAY_SECONDS = "delay_seconds"
    private const val ACTION_RETRY = "com.byd.wjx.adbrescue.RETRY"
    private const val REQUEST_CODE_BASE = 7_300
    private const val TAG = "AdbRescueScheduler"
}
