package com.byd.wjx.data.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.byd.wjx.data.local.entity.ActionDef
import java.util.concurrent.atomic.AtomicInteger

internal class AutomationNotificationActionHandler(
    private val context: Context,
    private val notificationPresenter: AutomationNotificationPresenter,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationCounter = AtomicInteger(USER_NOTIFICATION_BASE_ID)

    init {
        createChannels()
    }

    suspend fun show(action: ActionDef, silent: Boolean): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
        val title = payload?.optString("title")?.takeIf(String::isNotBlank)
            ?: action.displayName
        val message = payload?.optString("text") ?: ""
        if (!silent && notificationPresenter.show(title, message)) {
            return DispatchResult(true)
        }

        val channelId = if (silent) SILENT_CHANNEL_ID else SOUND_CHANNEL_ID
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(
                if (silent) NotificationCompat.PRIORITY_LOW
                else NotificationCompat.PRIORITY_HIGH,
            )
            .build()
        notificationManager.notify(notificationCounter.incrementAndGet(), notification)
        return DispatchResult(true)
    }

    private fun createChannels() {
        val silentChannel = NotificationChannel(
            SILENT_CHANNEL_ID,
            "Automation Silent",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            description = "Silent automation notifications"
        }
        val soundChannel = NotificationChannel(
            SOUND_CHANNEL_ID,
            "Automation Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Audible automation notifications"
        }
        notificationManager.createNotificationChannel(silentChannel)
        notificationManager.createNotificationChannel(soundChannel)
    }

    companion object {
        private const val SILENT_CHANNEL_ID = "wjx_automation_silent"
        private const val SOUND_CHANNEL_ID = "wjx_automation_sound"
        private const val USER_NOTIFICATION_BASE_ID = 10_000
    }
}
