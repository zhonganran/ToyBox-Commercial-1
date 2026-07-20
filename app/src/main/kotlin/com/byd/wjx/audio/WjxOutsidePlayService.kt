package com.byd.wjx.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.byd.wjx.R
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class WjxOutsidePlayService : Service() {
    @Inject lateinit var router: BydExteriorAudioRouter
    @Inject lateinit var audioUsage: AudioUsageCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val actionMutex = Mutex()
    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var activeChannel: ExteriorChannel? = null
    private var activePath: String? = null
    private var activeOwner: String? = null
    private var routeGuardJob: Job? = null
    private var playbackToken = 0L
    private val audioSettings by lazy { ExteriorHailingSettings(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("车外音效待命"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                audioUsage.setActive(AudioOwner.ExteriorPlayback, true)
                val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
                val channel = ExteriorChannel.fromName(intent.getStringExtra(EXTRA_CHANNEL))
                val loop = intent.getBooleanExtra(EXTRA_LOOP, false)
                val owner = intent.getStringExtra(EXTRA_OWNER)?.takeIf { it.isNotBlank() }
                val reverseActive = intent.getBooleanExtra(EXTRA_REVERSE_ACTIVE, false)
                WjxFileLogger.i(TAG, "ACTION_PLAY path=$path channel=$channel loop=$loop owner=${owner.orEmpty()} reverse=$reverseActive")
                serviceScope.launch { actionMutex.withLock { play(File(path), channel, loop, owner, reverseActive) } }
            }
            ACTION_TOGGLE -> {
                audioUsage.setActive(AudioOwner.ExteriorPlayback, true)
                val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
                val channel = ExteriorChannel.fromName(intent.getStringExtra(EXTRA_CHANNEL))
                val loop = intent.getBooleanExtra(EXTRA_LOOP, false)
                val owner = intent.getStringExtra(EXTRA_OWNER)?.takeIf { it.isNotBlank() }
                val reverseActive = intent.getBooleanExtra(EXTRA_REVERSE_ACTIVE, false)
                WjxFileLogger.i(TAG, "ACTION_TOGGLE path=$path channel=$channel loop=$loop owner=${owner.orEmpty()} reverse=$reverseActive")
                serviceScope.launch { actionMutex.withLock { togglePlayback(File(path), channel, loop, owner, reverseActive) } }
            }
            ACTION_STOP -> {
                val owner = intent.getStringExtra(EXTRA_OWNER)?.takeIf { it.isNotBlank() }
                WjxFileLogger.i(TAG, "ACTION_STOP owner=${owner.orEmpty()}")
                serviceScope.launch {
                    actionMutex.withLock {
                        if (owner == null || activeOwner == owner) {
                            stopPlayback()
                        } else {
                            WjxFileLogger.i(TAG, "skip stop: activeOwner=${activeOwner.orEmpty()} requestOwner=$owner")
                            if (activeOwner == null && player == null) {
                                audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
                                stopSelf()
                            }
                        }
                    }
                }
            }
            ACTION_UPDATE_VOLUME -> {
                serviceScope.launch { actionMutex.withLock { updateActiveVolume() } }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelRouteGuard()
        runCatching {
            runBlocking(Dispatchers.IO) {
                activeChannel?.let { channel ->
                    stopPlayerBeforeRouteRelease(channel)
                    releaseOutsideRouteSilently(channel)
                }
            }
        }
        runCatching { clearAndReleasePlayer() }
        audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun togglePlayback(
        file: File,
        channel: ExteriorChannel,
        loop: Boolean,
        owner: String?,
        reverseActive: Boolean,
    ) {
        val sameRequest = activePath == file.absolutePath && activeOwner == owner && activeChannel == channel
        if (sameRequest && (player != null || activePath != null)) {
            WjxFileLogger.i(TAG, "toggle stop file=${file.absolutePath} owner=${owner.orEmpty()}")
            stopPlayback()
            return
        }
        play(file, channel, loop, owner, reverseActive)
    }

    private suspend fun play(
        file: File,
        channel: ExteriorChannel,
        loop: Boolean,
        owner: String?,
        reverseActive: Boolean,
    ) {
        if (!file.isFile) {
            WjxFileLogger.w(TAG, "file missing: ${file.absolutePath}")
            audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
            stopSelf()
            return
        }

        if (audioUsage.isActive(AudioOwner.XiaozhiConversation)) {
            WjxFileLogger.i(TAG, "play skipped while Xiaozhi owns cabin audio path=${file.absolutePath}")
            broadcastState(STATE_STOPPED, file.absolutePath, owner)
            audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
            stopSelf()
            return
        }

        val startedAt = System.currentTimeMillis()
        WjxFileLogger.i(TAG, "play start file=${file.absolutePath} size=${file.length()} channel=$channel loop=$loop owner=${owner.orEmpty()}")
        updateNotification("正在准备车外音频通道")
        if (WjxRealtimeHailingService.stop(this)) {
            val released = audioUsage.awaitInactive(
                owners = setOf(AudioOwner.RealtimeHailing),
                timeoutMs = AUDIO_RESOURCE_RELEASE_TIMEOUT_MS,
            )
            if (!released) {
                WjxFileLogger.w(TAG, "play blocked: realtime hailing route release timeout")
                broadcastState(STATE_ERROR, file.absolutePath, owner)
                audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
                stopSelf()
                return
            }
        }
        if (audioUsage.isActive(AudioOwner.XiaozhiConversation)) {
            WjxFileLogger.i(TAG, "play cancelled because Xiaozhi claimed audio during preparation")
            broadcastState(STATE_STOPPED, file.absolutePath, owner)
            audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
            stopSelf()
            return
        }
        stopPlayback(releaseService = false, releaseOwnership = false)
        val token = ++playbackToken
        activeChannel = channel
        activePath = file.absolutePath
        activeOwner = owner
        if (reverseActive) {
            updateNotification("等待倒挡音频路由稳定")
            delay(REVERSE_ROUTE_SETTLE_MS)
        }
        val volumePercent = audioSettings.playbackVolumePercent
        val armed = router.armConfirmed(channel, BydExteriorAudioRouter.Usage.Playback, volumePercent)
        WjxFileLogger.i(TAG, "route arm result=${armed.summary()} volume=$volumePercent elapsed=${System.currentTimeMillis() - startedAt}ms")
        if (!armed.confirmed) {
            abortBeforePlayback(file, channel, owner, "车外路由强确认失败，已阻止车内播放")
            return
        }

        delay(if (channel == ExteriorChannel.Channel0) 300L else 120L)
        val verified = router.reinforceConfirmed(channel, BydExteriorAudioRouter.Usage.Playback, volumePercent)
        WjxFileLogger.i(TAG, "route pre-play verify=${verified.summary()} elapsed=${System.currentTimeMillis() - startedAt}ms")
        if (!verified.confirmed) {
            abortBeforePlayback(file, channel, owner, "车外路由播放前复核失败，已阻止车内播放")
            return
        }
        val started = runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(WjxAudioAttributes.forOutside(channel))
                setDataSource(file.absolutePath)
                setPlaybackPlayerVolume(this, volumePercent)
                isLooping = loop
                setOnCompletionListener { completePlayback("completion") }
                setOnErrorListener { _, what, extra ->
                    WjxFileLogger.w(TAG, "playback error what=$what extra=$extra")
                    completePlayback("error:$what/$extra")
                    true
                }
                prepare()
                enableEnhancer(this, channel, volumePercent)
                start()
            }
            true
        }.getOrElse {
            WjxFileLogger.w(TAG, "playback start failed: ${it.message}")
            clearAndReleasePlayer()
            runCatching { router.disarm(channel) }
            broadcastState(STATE_ERROR, file.absolutePath, owner)
            activeChannel = null
            activePath = null
            activeOwner = null
            audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
            updateNotification("音效播放失败，已关闭车外路由")
            stopSelf()
            false
        }
        if (started) {
            WjxFileLogger.i(
                TAG,
                "playback started session=${player?.audioSessionId} channel=$channel " +
                    "loop=$loop elapsed=${System.currentTimeMillis() - startedAt}ms"
            )
            broadcastState(STATE_STARTED, file.absolutePath, owner)
            updateNotification(if (loop) "正在循环车外播放：${file.nameWithoutExtension}" else "正在车外播放：${file.nameWithoutExtension}")
            startRouteGuard(token, file.absolutePath, channel, volumePercent)
        }
    }

    private suspend fun abortBeforePlayback(
        file: File,
        channel: ExteriorChannel,
        owner: String?,
        message: String,
    ) {
        WjxFileLogger.w(TAG, "$message channel=$channel")
        runCatching { router.disarm(channel) }
        broadcastState(STATE_ERROR, file.absolutePath, owner)
        updateNotification(message)
        activeChannel = null
        activePath = null
        activeOwner = null
        audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
        delay(800L)
        stopSelf()
    }

    private fun startRouteGuard(
        token: Long,
        path: String,
        channel: ExteriorChannel,
        volumePercent: Int,
    ) {
        cancelRouteGuard()
        routeGuardJob = serviceScope.launch {
            while (isActive) {
                delay(ROUTE_GUARD_INTERVAL_MS)
                val keepRunning = actionMutex.withLock {
                    if (token != playbackToken || activePath != path || activeChannel != channel || player == null) {
                        return@withLock false
                    }
                    val result = router.reinforceConfirmed(
                        channel = channel,
                        usage = BydExteriorAudioRouter.Usage.Playback,
                        volumePercent = volumePercent,
                    )
                    if (!result.confirmed) {
                        WjxFileLogger.w(TAG, "route guard lost ${result.summary()} path=$path; mute before stop")
                        mutePlayerForRouteRelease()
                        updateNotification("车外路由丢失，已静音停止")
                        routeGuardJob = null
                        stopPlayback(state = STATE_ERROR)
                        false
                    } else {
                        true
                    }
                }
                if (!keepRunning) return@launch
            }
        }
    }

    private fun cancelRouteGuard() {
        routeGuardJob?.cancel()
        routeGuardJob = null
    }

    private suspend fun stopPlayback(
        releaseService: Boolean = true,
        releaseOwnership: Boolean = true,
        state: String = STATE_STOPPED,
    ) {
        playbackToken += 1
        cancelRouteGuard()
        val hadPlayer = player != null
        val channel = activeChannel
        val path = activePath
        val owner = activeOwner
        if ((hadPlayer || path != null) && channel != null) {
            stopPlayerBeforeRouteRelease(channel)
            releaseOutsideRouteSilently(channel)
        } else {
            clearAndReleasePlayer()
        }
        activeChannel = null
        activePath = null
        activeOwner = null
        if (releaseOwnership) audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
        if (path != null) broadcastState(state, path, owner)
        if (releaseService) stopSelf()
    }

    private fun completePlayback(reason: String) {
        WjxFileLogger.i(TAG, "completePlayback reason=$reason")
        val state = if (reason.startsWith("error:")) STATE_ERROR else STATE_COMPLETED
        serviceScope.launch { actionMutex.withLock { stopPlayback(state = state) } }
    }

    private fun broadcastState(state: String, path: String, owner: String?) {
        sendBroadcast(
            Intent(ACTION_PLAYBACK_STATE)
                .setPackage(packageName)
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_STATE_PATH, path)
                .putExtra(EXTRA_OWNER, owner.orEmpty())
        )
    }

    private fun clearAndReleasePlayer() {
        runCatching { player?.setOnCompletionListener(null) }
        runCatching { player?.setOnErrorListener(null) }
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.release() }
        runCatching { player?.pause() }
        runCatching { player?.stop() }
        runCatching { player?.release() }
        enhancer = null
        player = null
    }

    private suspend fun stopPlayerBeforeRouteRelease(channel: ExteriorChannel) {
        WjxFileLogger.i(TAG, "mute, drain and release player before route release channel=$channel")
        mutePlayerForRouteRelease()
        delay(PLAYER_MUTE_DRAIN_MS)
        clearAndReleasePlayer()
        delay(PLAYER_RELEASE_DRAIN_MS)
    }

    private suspend fun releaseOutsideRouteSilently(channel: ExteriorChannel) {
        delay(ROUTE_RELEASE_DRAIN_MS)
        WjxFileLogger.i(TAG, "release route silently channel=$channel")
        runCatching {
            val released = withTimeoutOrNull(ROUTE_RELEASE_TIMEOUT_MS) {
                router.disarm(channel)
                true
            } ?: false
            if (!released) {
                WjxFileLogger.w(TAG, "silent route release timeout channel=$channel")
            }
        }.onFailure {
            WjxFileLogger.w(TAG, "silent route release failed: ${it.message}")
        }
    }

    private fun mutePlayerForRouteRelease() {
        val mediaPlayer = player
        runCatching { mediaPlayer?.setVolume(0f, 0f) }
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.setTargetGain(0) }
    }

    private suspend fun updateActiveVolume() {
        val channel = activeChannel
        val mediaPlayer = player
        if (channel == null || mediaPlayer == null) {
            audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
            stopSelf()
            return
        }
        val volumePercent = audioSettings.playbackVolumePercent
        setPlaybackPlayerVolume(mediaPlayer, volumePercent)
        runCatching { enhancer?.setTargetGain(playbackEnhancerGain(channel, volumePercent)) }
        router.updateVolume(channel, BydExteriorAudioRouter.Usage.Playback, volumePercent)
        WjxFileLogger.i(TAG, "playback volume updated channel=$channel volume=$volumePercent gain=${playbackEnhancerGain(channel, volumePercent)}")
    }

    private fun setPlaybackPlayerVolume(mediaPlayer: MediaPlayer, volumePercent: Int) {
        val playerVolume = (volumePercent / 100f).coerceIn(0f, 1f)
        mediaPlayer.setVolume(playerVolume, playerVolume)
    }

    private fun enableEnhancer(mediaPlayer: MediaPlayer, channel: ExteriorChannel, volumePercent: Int) {
        runCatching {
            val gain = playbackEnhancerGain(channel, volumePercent)
            enhancer = LoudnessEnhancer(mediaPlayer.audioSessionId).apply {
                setTargetGain(gain)
                enabled = true
            }
            WjxFileLogger.i(TAG, "LoudnessEnhancer enabled gain=$gain volume=$volumePercent channel=$channel")
        }.onFailure {
            WjxFileLogger.w(TAG, "LoudnessEnhancer failed: ${it.message}")
        }
    }

    private fun playbackEnhancerGain(channel: ExteriorChannel, volumePercent: Int): Int {
        val baseGain = if (channel == ExteriorChannel.Channel0) 8400 else 3200
        return (baseGain * (volumePercent.coerceIn(0, ExteriorHailingSettings.MAX_PLAYBACK_VOLUME_PERCENT) / 100f))
            .toInt()
            .coerceIn(0, 10_000)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "车外音效", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun updateNotification(text: String) {
        startForeground(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .build()
    }

    companion object {
        private const val ACTION_PLAY = "com.byd.wjx.action.PLAY_OUTSIDE"
        private const val ACTION_TOGGLE = "com.byd.wjx.action.TOGGLE_OUTSIDE"
        private const val ACTION_STOP = "com.byd.wjx.action.STOP_OUTSIDE"
        private const val ACTION_UPDATE_VOLUME = "com.byd.wjx.action.UPDATE_OUTSIDE_VOLUME"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_LOOP = "loop"
        private const val EXTRA_REVERSE_ACTIVE = "reverse_active"
        const val EXTRA_OWNER = "owner"
        private const val CHANNEL_ID = "wjx_outside_audio"
        private const val NOTIFICATION_ID = 1101
        private const val TAG = "WjxOutsidePlay"
        private const val PLAYER_MUTE_DRAIN_MS = 180L
        private const val PLAYER_RELEASE_DRAIN_MS = 180L
        private const val ROUTE_RELEASE_DRAIN_MS = 360L
        private const val ROUTE_RELEASE_TIMEOUT_MS = 1_000L
        private const val AUDIO_RESOURCE_RELEASE_TIMEOUT_MS = 2_400L
        private const val REVERSE_ROUTE_SETTLE_MS = 500L
        private const val ROUTE_GUARD_INTERVAL_MS = 1_000L
        const val ACTION_PLAYBACK_STATE = "com.byd.wjx.action.OUTSIDE_PLAYBACK_STATE"
        const val EXTRA_STATE = "state"
        const val EXTRA_STATE_PATH = "state_path"
        const val STATE_STARTED = "started"
        const val STATE_STOPPED = "stopped"
        const val STATE_COMPLETED = "completed"
        const val STATE_ERROR = "error"

        fun play(
            context: Context,
            file: File,
            channel: ExteriorChannel = ExteriorChannel.Channel1,
            loop: Boolean = false,
            owner: String? = null,
            reverseActive: Boolean = false,
        ) {
            val intent = Intent(context, WjxOutsidePlayService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_CHANNEL, channel.name)
                .putExtra(EXTRA_LOOP, loop)
                .putExtra(EXTRA_OWNER, owner.orEmpty())
                .putExtra(EXTRA_REVERSE_ACTIVE, reverseActive)
            ContextCompat.startForegroundService(context, intent)
        }

        fun toggle(
            context: Context,
            file: File,
            channel: ExteriorChannel = ExteriorChannel.Channel1,
            loop: Boolean = false,
            owner: String? = null,
            reverseActive: Boolean = false,
        ) {
            val intent = Intent(context, WjxOutsidePlayService::class.java)
                .setAction(ACTION_TOGGLE)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_CHANNEL, channel.name)
                .putExtra(EXTRA_LOOP, loop)
                .putExtra(EXTRA_OWNER, owner.orEmpty())
                .putExtra(EXTRA_REVERSE_ACTIVE, reverseActive)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, owner: String? = null) {
            val intent = Intent(context, WjxOutsidePlayService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_OWNER, owner.orEmpty())
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateVolume(context: Context) {
            val intent = Intent(context, WjxOutsidePlayService::class.java)
                .setAction(ACTION_UPDATE_VOLUME)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
