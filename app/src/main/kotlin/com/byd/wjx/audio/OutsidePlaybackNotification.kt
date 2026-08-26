package com.byd.wjx.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import com.byd.wjx.R

internal class OutsidePlaybackNotification(
    private val service: Service,
) {
    fun start(text: String) {
        ensureChannel()
        update(text)
    }

    fun update(text: String) {
        service.startForeground(NOTIFICATION_ID, build(text))
    }

    private fun ensureChannel() {
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "车外音效", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun build(text: String): Notification = Notification.Builder(service, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(service.getString(R.string.app_name))
        .setContentText(text)
        .build()

    private companion object {
        const val CHANNEL_ID = "wjx_outside_audio"
        const val NOTIFICATION_ID = 1101
    }
}
