package com.byd.wjx.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import com.byd.wjx.R

internal class RealtimeHailingNotification(
    private val service: Service,
) {
    fun start(text: String) {
        service.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "实时喊话", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
        update(text)
    }

    fun update(text: String) {
        service.startForeground(NOTIFICATION_ID, build(text))
    }

    private fun build(text: String): Notification =
        Notification.Builder(service, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText(text)
            .build()

    private companion object {
        const val CHANNEL_ID = "wjx_realtime_hailing"
        const val NOTIFICATION_ID = 1103
    }
}
