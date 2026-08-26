package com.byd.wjx.audio

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class WjxOutsidePlayService : Service() {
    @Inject lateinit var router: BydExteriorAudioRouter
    @Inject lateinit var audioUsage: AudioUsageCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val actionMutex = Mutex()
    private val playbackPlayer = OutsidePlaybackPlayer()
    private lateinit var notification: OutsidePlaybackNotification
    private var activeChannel: ExteriorChannel? = null
    private var activePath: String? = null
    private var activeOwner: String? = null
    private var routeGuardJob: Job? = null
    private var playbackToken = 0L
    private val audioSettings by lazy { ExteriorHailingSettings(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        notification = OutsidePlaybackNotification(this)
        notification.start("车外音效待命")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (val command = OutsidePlaybackContract.parse(intent)) {
            is OutsidePlaybackCommand.Play -> {
                audioUsage.setActive(AudioOwner.ExteriorPlayback, true)
                WjxFileLogger.i(
                    TAG,
                    "${if (command.toggle) "TOGGLE" else "PLAY"} path=${command.file.absolutePath} " +
                        "channel=${command.channel} loop=${command.loop} " +
                        "owner=${command.owner.orEmpty()} reverse=${command.reverseActive}",
                )
                serviceScope.launch {
                    actionMutex.withLock {
                        if (command.toggle) {
                            togglePlayback(command)
                        } else {
                            play(command)
                        }
                    }
                }
            }
            is OutsidePlaybackCommand.Stop -> {
                WjxFileLogger.i(TAG, "STOP owner=${command.owner.orEmpty()}")
                serviceScope.launch { actionMutex.withLock { stopForOwner(command.owner) } }
            }
            OutsidePlaybackCommand.UpdateVolume -> {
                serviceScope.launch { actionMutex.withLock { updateActiveVolume() } }
            }
            OutsidePlaybackCommand.Ignore -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelRouteGuard()
        playbackToken += 1
        val channel = activeChannel
        activeChannel = null
        activePath = null
        activeOwner = null
        playbackPlayer.mute()
        if (channel != null) {
            cleanupScope.launch {
                runCatching {
                    playbackPlayer.stopBeforeRouteRelease(channel)
                    releaseOutsideRouteSilently(channel)
                }.onFailure {
                    WjxFileLogger.w(TAG, "service destroy cleanup failed: ${it.message}", it)
                }
            }
        } else {
            playbackPlayer.clear()
        }
        audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun stopForOwner(owner: String?) {
        if (owner == null || activeOwner == owner) {
            stopPlayback()
        } else {
            WjxFileLogger.i(
                TAG,
                "skip stop: activeOwner=${activeOwner.orEmpty()} requestOwner=$owner",
            )
            if (activeOwner == null && !playbackPlayer.active) {
                audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
                stopSelf()
            }
        }
    }

    private suspend fun togglePlayback(command: OutsidePlaybackCommand.Play) {
        val sameRequest = activePath == command.file.absolutePath &&
            activeOwner == command.owner &&
            activeChannel == command.channel
        if (sameRequest && (playbackPlayer.active || activePath != null)) {
            WjxFileLogger.i(
                TAG,
                "toggle stop file=${command.file.absolutePath} owner=${command.owner.orEmpty()}",
            )
            stopPlayback()
        } else {
            play(command)
        }
    }

    private suspend fun play(command: OutsidePlaybackCommand.Play) {
        val file = command.file
        if (!file.isFile) {
            WjxFileLogger.w(TAG, "file missing: ${file.absolutePath}")
            audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
            stopSelf()
            return
        }

        val startedAt = System.currentTimeMillis()
        WjxFileLogger.i(
            TAG,
            "play start file=${file.absolutePath} size=${file.length()} channel=${command.channel} " +
                "loop=${command.loop} owner=${command.owner.orEmpty()}",
        )
        notification.update("正在准备车外音频通道")
        if (WjxRealtimeHailingService.stop(this)) {
            val released = audioUsage.awaitInactive(
                owners = setOf(AudioOwner.RealtimeHailing),
                timeoutMs = AUDIO_RESOURCE_RELEASE_TIMEOUT_MS,
            )
            if (!released) {
                WjxFileLogger.w(TAG, "play blocked: realtime hailing route release timeout")
                broadcastState(STATE_ERROR, file.absolutePath, command.owner)
                audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
                stopSelf()
                return
            }
        }
        stopPlayback(releaseService = false, releaseOwnership = false)
        val token = ++playbackToken
        activePath = file.absolutePath
        activeOwner = command.owner
        if (command.reverseActive) {
            notification.update("等待倒挡音频路由稳定")
            delay(REVERSE_ROUTE_SETTLE_MS)
        }
        val volumePercent = audioSettings.playbackVolumePercent
        val routed = router.armVerified(
            command.channel,
            BydExteriorAudioRouter.Usage.Playback,
            volumePercent,
        )
        if (routed == null) {
            abortBeforePlayback(file, command.channel, command.owner, "车外路由无法确认，已阻止车内播放")
            return
        }
        val playbackChannel = routed.channel
        activeChannel = playbackChannel
        WjxFileLogger.i(
            TAG,
            "route ready requested=${command.channel} active=$playbackChannel " +
                "result=${routed.summary()} volume=$volumePercent " +
                "elapsed=${System.currentTimeMillis() - startedAt}ms",
        )
        val started = playbackPlayer.start(
            file = file,
            channel = playbackChannel,
            loop = command.loop,
            volumePercent = volumePercent,
            onCompletion = { completePlayback("completion") },
            onError = { what, extra ->
                WjxFileLogger.w(TAG, "playback error what=$what extra=$extra")
                completePlayback("error:$what/$extra")
            },
        )
        if (!started) {
            runCatching { router.disarm(playbackChannel) }
            broadcastState(STATE_ERROR, file.absolutePath, command.owner)
            clearActiveSession()
            audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
            notification.update("音效播放失败，已关闭车外路由")
            stopSelf()
            return
        }

        WjxFileLogger.i(
            TAG,
            "playback started session=${playbackPlayer.audioSessionId} channel=$playbackChannel " +
                "loop=${command.loop} elapsed=${System.currentTimeMillis() - startedAt}ms",
        )
        broadcastState(STATE_STARTED, file.absolutePath, command.owner)
        notification.update(
            if (command.loop) {
                "正在循环车外播放：${file.nameWithoutExtension}"
            } else {
                "正在车外播放：${file.nameWithoutExtension}"
            },
        )
        startRouteGuard(token, file.absolutePath, playbackChannel, volumePercent)
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
        notification.update(message)
        clearActiveSession()
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
                    if (
                        token != playbackToken || activePath != path ||
                        activeChannel != channel || !playbackPlayer.active
                    ) {
                        return@withLock false
                    }
                    val result = router.reinforceConfirmed(
                        channel = channel,
                        usage = BydExteriorAudioRouter.Usage.Playback,
                        volumePercent = volumePercent,
                    )
                    if (!result.confirmed) {
                        WjxFileLogger.w(
                            TAG,
                            "route guard lost ${result.summary()} path=$path; mute before stop",
                        )
                        playbackPlayer.mute()
                        notification.update("车外路由丢失，已静音停止")
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
        val hadPlayer = playbackPlayer.active
        val channel = activeChannel
        val path = activePath
        val owner = activeOwner
        if ((hadPlayer || path != null) && channel != null) {
            playbackPlayer.stopBeforeRouteRelease(channel)
            releaseOutsideRouteSilently(channel)
        } else {
            playbackPlayer.clear()
        }
        clearActiveSession()
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
                .putExtra(EXTRA_OWNER, owner.orEmpty()),
        )
    }

    private suspend fun releaseOutsideRouteSilently(channel: ExteriorChannel) {
        delay(ROUTE_RELEASE_DRAIN_MS)
        WjxFileLogger.i(TAG, "release route silently channel=$channel")
        runCatching {
            val released = withTimeoutOrNull(ROUTE_RELEASE_TIMEOUT_MS) {
                router.disarm(channel)
                true
            } ?: false
            if (!released) WjxFileLogger.w(TAG, "silent route release timeout channel=$channel")
        }.onFailure { error ->
            WjxFileLogger.w(TAG, "silent route release failed: ${error.message}")
        }
    }

    private suspend fun updateActiveVolume() {
        val channel = activeChannel
        if (channel == null || !playbackPlayer.active) {
            audioUsage.setActive(AudioOwner.ExteriorPlayback, false)
            stopSelf()
            return
        }
        val volumePercent = audioSettings.playbackVolumePercent
        playbackPlayer.updateVolume(channel, volumePercent)
        router.updateVolume(channel, BydExteriorAudioRouter.Usage.Playback, volumePercent)
        WjxFileLogger.i(
            TAG,
            "playback volume updated channel=$channel volume=$volumePercent " +
                "gain=${outsidePlaybackEnhancerGain(channel, volumePercent)}",
        )
    }

    private fun clearActiveSession() {
        activeChannel = null
        activePath = null
        activeOwner = null
    }

    companion object {
        const val ACTION_PLAYBACK_STATE = OutsidePlaybackContract.ACTION_PLAYBACK_STATE
        const val EXTRA_STATE = OutsidePlaybackContract.EXTRA_STATE
        const val EXTRA_STATE_PATH = OutsidePlaybackContract.EXTRA_STATE_PATH
        const val EXTRA_OWNER = OutsidePlaybackContract.EXTRA_OWNER
        const val STATE_STARTED = OutsidePlaybackContract.STATE_STARTED
        const val STATE_STOPPED = OutsidePlaybackContract.STATE_STOPPED
        const val STATE_COMPLETED = OutsidePlaybackContract.STATE_COMPLETED
        const val STATE_ERROR = OutsidePlaybackContract.STATE_ERROR

        private const val TAG = "WjxOutsidePlay"
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val ROUTE_RELEASE_DRAIN_MS = 360L
        private const val ROUTE_RELEASE_TIMEOUT_MS = 1_000L
        private const val AUDIO_RESOURCE_RELEASE_TIMEOUT_MS = 2_400L
        private const val REVERSE_ROUTE_SETTLE_MS = 500L
        private const val ROUTE_GUARD_INTERVAL_MS = 1_000L

        fun play(
            context: Context,
            file: File,
            channel: ExteriorChannel = ExteriorChannel.Channel1,
            loop: Boolean = false,
            owner: String? = null,
            reverseActive: Boolean = false,
        ) {
            OutsidePlaybackStarter.play(
                context,
                file,
                channel,
                loop,
                owner,
                reverseActive,
                toggle = false,
            )
        }

        fun toggle(
            context: Context,
            file: File,
            channel: ExteriorChannel = ExteriorChannel.Channel1,
            loop: Boolean = false,
            owner: String? = null,
            reverseActive: Boolean = false,
        ) {
            OutsidePlaybackStarter.play(
                context,
                file,
                channel,
                loop,
                owner,
                reverseActive,
                toggle = true,
            )
        }

        fun stop(context: Context, owner: String? = null) {
            OutsidePlaybackStarter.stop(context, owner)
        }

        fun updateVolume(context: Context) {
            OutsidePlaybackStarter.updateVolume(context)
        }
    }
}
