package com.byd.wjx.data.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.byd.wjx.log.WjxFileLogger

class AutomationMusicService : Service() {
    private var player: MediaPlayer? = null
    private var savedStreamVolume: Int? = null
    private var playbackGeneration = 0L
    private var activeOwner: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                startForeground(NOTIFICATION_ID, notification())
                val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
                val volume = intent.getIntExtra(EXTRA_VOLUME, DEFAULT_VOLUME).coerceIn(0, 100)
                val loop = intent.getBooleanExtra(EXTRA_LOOP, false)
                val owner = intent.getStringExtra(EXTRA_OWNER)
                val adjustStreamVolume = intent.getBooleanExtra(EXTRA_ADJUST_STREAM_VOLUME, true)
                val audio = getSystemService(AudioManager::class.java)
                WjxFileLogger.i(
                    TAG,
                    "play received source=${source.takeLast(100)} loop=$loop owner=${owner.orEmpty()} " +
                        "adjustStream=$adjustStreamVolume requestedVolume=$volume " +
                        "streamVolume=${audio?.getStreamVolume(AudioManager.STREAM_MUSIC)}/${audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}",
                )
                if (source.isBlank()) {
                    stopAndRestore("empty_source")
                    stopSelf()
                } else {
                    startPlayback(source, volume, loop, owner, adjustStreamVolume)
                }
            }
            ACTION_STOP -> {
                startForeground(NOTIFICATION_ID, notification())
                val requestedOwner = intent.getStringExtra(EXTRA_OWNER)
                if (requestedOwner == null || requestedOwner == activeOwner) {
                    stopAndRestore("action_stop")
                    stopSelf()
                } else {
                    WjxFileLogger.i(TAG, "ignore music stop owner=$requestedOwner active=$activeOwner")
                    if (player == null) {
                        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                        stopSelf()
                    }
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(
        source: String,
        volumePercent: Int,
        loop: Boolean,
        owner: String?,
        adjustStreamVolume: Boolean,
    ) {
        val generation = ++playbackGeneration
        releasePlayer()
        if (!adjustStreamVolume) restoreStreamVolume()
        activeOwner = owner
        AutomationMediaRegistry.musicOwner = owner
        if (adjustStreamVolume) setStreamVolume(volumePercent)
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        runCatching {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(mediaPlayer, source)
            mediaPlayer.isLooping = loop
            mediaPlayer.setVolume(1f, 1f)
            mediaPlayer.setOnPreparedListener {
                if (generation != playbackGeneration) return@setOnPreparedListener
                runCatching { it.start() }
                    .onSuccess {
                        val volumeLabel = if (adjustStreamVolume) "$volumePercent%" else "current"
                        WjxFileLogger.i(
                            TAG,
                            "music started source=${source.takeLast(80)} volume=$volumeLabel loop=$loop",
                        )
                    }
                    .onFailure { error -> failPlayback("start", error) }
            }
            mediaPlayer.setOnCompletionListener {
                if (!loop && generation == playbackGeneration) {
                    stopAndRestore("completed")
                    stopSelf()
                }
            }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                WjxFileLogger.w(TAG, "music error what=$what extra=$extra source=${source.takeLast(80)}")
                stopAndRestore("media_error")
                stopSelf()
                true
            }
            mediaPlayer.prepareAsync()
        }.onFailure { failPlayback("prepare", it) }
    }

    private fun setDataSource(mediaPlayer: MediaPlayer, source: String) {
        val uri = Uri.parse(source)
        if (uri.scheme.equals("content", ignoreCase = true)) {
            mediaPlayer.setDataSource(this, uri)
        } else {
            mediaPlayer.setDataSource(source)
        }
    }

    private fun setStreamVolume(percent: Int) {
        val audio = getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (savedStreamVolume == null) savedStreamVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent * max) / 100f).toInt().coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
            .onFailure { WjxFileLogger.w(TAG, "set music volume failed: ${it.message}", it) }
    }

    private fun restoreStreamVolume() {
        val saved = savedStreamVolume ?: return
        savedStreamVolume = null
        val audio = getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved.coerceIn(0, max), 0) }
            .onFailure { WjxFileLogger.w(TAG, "restore music volume failed: ${it.message}", it) }
    }

    private fun failPlayback(stage: String, error: Throwable) {
        WjxFileLogger.w(TAG, "music $stage failed: ${error.message}", error)
        stopAndRestore(stage)
        stopSelf()
    }

    private fun stopAndRestore(reason: String) {
        playbackGeneration++
        releasePlayer()
        if (AutomationMediaRegistry.musicOwner == activeOwner) AutomationMediaRegistry.musicOwner = null
        activeOwner = null
        restoreStreamVolume()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        WjxFileLogger.i(TAG, "music stopped reason=$reason")
    }

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        current.setOnPreparedListener(null)
        current.setOnCompletionListener(null)
        current.setOnErrorListener(null)
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "智能联动音频", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("智能联动")
        .setContentText("正在播放车内音频")
        .setOngoing(true)
        .setSilent(true)
        .build()

    override fun onDestroy() {
        stopAndRestore("service_destroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AutomationMusicService"
        private const val CHANNEL_ID = "wjx_automation_music"
        private const val NOTIFICATION_ID = 7321
        private const val DEFAULT_VOLUME = 30
        private const val ACTION_PLAY = "com.byd.wjx.action.PLAY_AUTOMATION_MUSIC"
        private const val ACTION_STOP = "com.byd.wjx.action.STOP_AUTOMATION_MUSIC"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_LOOP = "loop"
        private const val EXTRA_OWNER = "owner"
        private const val EXTRA_ADJUST_STREAM_VOLUME = "adjust_stream_volume"

        fun play(
            context: Context,
            source: String,
            volume: Int,
            loop: Boolean,
            owner: String?,
            adjustStreamVolume: Boolean = true,
        ): Boolean {
            val intent = Intent(context, AutomationMusicService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_SOURCE, source)
                .putExtra(EXTRA_VOLUME, volume.coerceIn(0, 100))
                .putExtra(EXTRA_LOOP, loop)
                .putExtra(EXTRA_OWNER, owner)
                .putExtra(EXTRA_ADJUST_STREAM_VOLUME, adjustStreamVolume)
            return runCatching {
                val component = ContextCompat.startForegroundService(context, intent)
                WjxFileLogger.i(
                    TAG,
                    "play service requested component=${component?.toString().orEmpty()} " +
                        "source=${source.takeLast(100)} owner=${owner.orEmpty()}",
                )
                true
            }.getOrElse { error ->
                WjxFileLogger.w(TAG, "play service request failed: ${error.message}", error)
                false
            }
        }

        fun stop(context: Context, owner: String? = null) {
            val intent = Intent(context, AutomationMusicService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_OWNER, owner)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
