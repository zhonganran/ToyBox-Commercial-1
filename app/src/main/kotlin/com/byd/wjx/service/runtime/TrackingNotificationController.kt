package com.byd.wjx.service.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.byd.wjx.MainActivity
import com.byd.wjx.R
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.TrackingStateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and updates the tracking foreground-service notification. */
@Singleton
class TrackingNotificationController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackingStateRepository: TrackingStateRepository,
) {
    @Volatile private var lastPublishedText: String? = null

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "超级玩具箱 Tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Trip and charge tracking"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun startingNotification(): Notification {
        lastPublishedText = null
        return buildNotification(context.getString(R.string.service_foreground_content_starting))
    }

    fun update(data: DiParsData) {
        val parts = mutableListOf<String>()
        val socText = data.soc?.let { "$it%" } ?: "—"
        val rangeText = trackingStateRepository.rangeKm.value?.let {
            context.getString(R.string.service_notification_range_suffix, it)
        }.orEmpty()
        val temperatureText = data.avgBatTemp?.let {
            context.getString(R.string.service_notification_bat_temp_suffix, it)
        }.orEmpty()
        parts += context.getString(
            R.string.service_notification_soc_line,
            socText,
            rangeText,
            temperatureText,
        )
        data.voltage12v?.let {
            parts += context.getString(R.string.service_notification_voltage, it)
        }
        val text = parts.joinToString(" | ")
        if (text == lastPublishedText) return
        context.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(text),
        )
        lastPublishedText = text
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("超级玩具箱")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "wjx_tracking"
    }
}
