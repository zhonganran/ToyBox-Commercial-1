package com.byd.wjx.xiaozhi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.byd.wjx.R
import com.byd.wjx.xiaozhi.domain.XiaozhiSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class XiaozhiVoiceService : Service() {
    @Inject lateinit var sessionManager: XiaozhiSessionManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("小智语音对话正在运行"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> sessionManager.toggleConversation()
            ACTION_ABORT -> sessionManager.abortConversation()
            ACTION_KEEP_ALIVE, null -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sessionManager.onVoiceHostStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "小智语音对话",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "仅在小智连接、录音和回答期间保持麦克风会话"
                setShowBadge(false)
            },
        )
    }

    private fun notification(text: String): Notification {
        val stopIntent = Intent(this, XiaozhiVoiceService::class.java).setAction(ACTION_ABORT)
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("超级玩具箱 · 小智")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "停止对话", stopPendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "wjx_xiaozhi_voice"
        private const val NOTIFICATION_ID = 1110
        private const val ACTION_KEEP_ALIVE = "com.byd.wjx.xiaozhi.KEEP_ALIVE"
        private const val ACTION_TOGGLE = "com.byd.wjx.xiaozhi.TOGGLE"
        private const val ACTION_ABORT = "com.byd.wjx.xiaozhi.ABORT"

        fun ensureRunning(context: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, XiaozhiVoiceService::class.java)
                    .setAction(ACTION_KEEP_ALIVE),
            )
        }.isSuccess

        fun toggle(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, XiaozhiVoiceService::class.java)
                    .setAction(ACTION_TOGGLE),
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, XiaozhiVoiceService::class.java),
            )
        }
    }
}
