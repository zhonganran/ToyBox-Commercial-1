package com.byd.wjx.helper

import android.content.Context
import android.os.IBinder
import com.byd.wjx.audio.ExteriorChannel
import java.io.File

internal const val LOCK_SOUND_KEY_UNLOCK = "unlock"
internal const val LOCK_SOUND_KEY_LOCK = "lock"
internal const val LOCK_SOUND_KEY_SENTRY = "sentry"
internal const val LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB = 1_500
internal const val LOCK_SOUND_PCM_ENHANCER_GAIN_MB = 2_800

internal data class EventConfig(
    val enabled: Boolean = false,
    val path: String = "",
    val pcm: PcmConfig = PcmConfig(),
)

internal enum class LockEvent(val kind: Int, val key: String, val looping: Boolean = false) {
    Unlock(0, LOCK_SOUND_KEY_UNLOCK),
    Lock(1, LOCK_SOUND_KEY_LOCK),
    Sentry(2, LOCK_SOUND_KEY_SENTRY);

    companion object {
        fun fromKind(kind: Int): LockEvent? = entries.firstOrNull { it.kind == kind }
    }
}

internal fun lockSoundRouteCandidates(preferred: ExteriorChannel): List<ExteriorChannel> =
    listOf(
        preferred,
        if (preferred == ExteriorChannel.Channel0) ExteriorChannel.Channel1 else ExteriorChannel.Channel0,
    )

internal const val LOCK_SOUND_OPEN_DOOR_ROUTE_REFRESH_MS = 400L

internal fun shouldRefreshOpenDoorRoute(
    openedEdge: Boolean,
    nowMs: Long,
    lastRefreshAtMs: Long?,
): Boolean =
    openedEdge ||
        lastRefreshAtMs == null ||
        nowMs < lastRefreshAtMs ||
        nowMs - lastRefreshAtMs >= LOCK_SOUND_OPEN_DOOR_ROUTE_REFRESH_MS

internal class WjxLockSoundPlaybackEngine(
    svc: IBinder,
    autoIface: String,
    systemContext: Context?,
    private val isDriverDoorOpen: () -> Boolean,
) {
    private val playLock = Any()
    private val routeController = WjxHelperExteriorRouteController(svc, autoIface, systemContext)
    private val mediaPlayback = LockSoundMediaPlayer()
    @Volatile private var activeChannel: ExteriorChannel? = null
    @Volatile private var activeEvent: LockEvent? = null
    @Volatile private var activePlaybackToken: Long = 0L
    private val pcmPlayback = LockSoundPcmPlayer(
        isTokenActive = { token -> activePlaybackToken == token },
        onComplete = { reason, token -> completePlayback(reason, token) },
        onFallback = { token, event, file, channel, cause ->
            fallbackToMediaPlayerFromPcm(token, event, file, channel, cause)
        },
    )
    private var playbackTokenSeed = 0L
    private var lastOpenDoorRouteRefreshAtMs: Long? = null
    private var openDoorChannel0SwitchAttempted = false
    private val requestQueue = LockSoundPlaybackQueue()

    fun hasActivePlayback(): Boolean = activeEvent != null

    fun keepActiveOutsideForOpenDoor(openedEdge: Boolean, nowMs: Long): Boolean = synchronized(playLock) {
        val event = activeEvent ?: return@synchronized true
        val channel = activeChannel ?: return@synchronized false
        if (!shouldRefreshOpenDoorRoute(openedEdge, nowMs, lastOpenDoorRouteRefreshAtMs)) {
            return@synchronized true
        }
        lastOpenDoorRouteRefreshAtMs = nowMs
        if (channel == ExteriorChannel.Channel1 && !openDoorChannel0SwitchAttempted) {
            openDoorChannel0SwitchAttempted = true
            val switched = routeController.switch(channel, ExteriorChannel.Channel0)
            if (switched == null) {
                helperLog(TAG, "open-door route lost event=${event.key} channel=$channel; stop to prevent cabin playback")
                activeChannel = null
                finishActiveLocked(
                    reason = "open_door_route_lost",
                    releaseRoute = false,
                    routeReleaseDelayMs = 0L,
                )
                return@synchronized false
            }
            activeChannel = switched
            helperLog(
                TAG,
                "open-door route ${if (openedEdge) "edge" else "held"} switched=$channel->$switched",
            )
            return@synchronized true
        }
        val refreshed = routeController.refresh(channel)
        helperLog(
            TAG,
            "open-door route ${if (openedEdge) "edge" else "held"} refresh channel=$channel ok=$refreshed",
        )
        true
    }

    fun stop(
        event: LockEvent,
        reason: String,
        routeReleaseDelayMs: Long = ROUTE_RELEASE_DRAIN_MS,
    ): Boolean = synchronized(playLock) {
        val removed = requestQueue.remove(event)
        if (activeEvent != event) {
            helperLog(TAG, "stop ${event.key} active=$activeEvent removedQueued=$removed reason=$reason")
            return@synchronized true
        }
        finishActiveLocked(
            reason = reason,
            releaseRoute = true,
            routeReleaseDelayMs = routeReleaseDelayMs,
        )
        true
    }

    fun play(
        event: LockEvent,
        eventCfg: EventConfig,
        preferredChannel: ExteriorChannel,
        reason: String,
    ): Boolean = synchronized(playLock) {
        if (!eventCfg.enabled) {
            helperLog(TAG, "skip ${event.key}: disabled reason=$reason")
            return@synchronized false
        }
        val file = File(eventCfg.path)
        if (!file.isFile || file.length() <= 0L) {
            helperLog(TAG, "skip ${event.key}: file missing path=${eventCfg.path} reason=$reason")
            return@synchronized false
        }
        val request = LockSoundPlaybackRequest(event, eventCfg, preferredChannel, reason)
        when (requestQueue.submit(request, activeEvent)) {
            LockSoundQueueDecision.Merged -> {
                helperLog(TAG, "merge ${event.key}: already active/queued reason=$reason")
                return@synchronized true
            }
            LockSoundQueueDecision.Full -> {
                helperLog(TAG, "queue full event=${event.key} active=$activeEvent reason=$reason")
                return@synchronized false
            }
            LockSoundQueueDecision.Queued -> {
                helperLog(TAG, "queued event=${event.key} active=$activeEvent size=${requestQueue.size} reason=$reason")
                return@synchronized true
            }
            LockSoundQueueDecision.StartNow -> startRequestLocked(request)
        }
    }

    private fun startRequestLocked(request: LockSoundPlaybackRequest): Boolean {
        val event = request.event
        val eventCfg = request.eventConfig
        val file = File(eventCfg.path)
        if (!eventCfg.enabled || !file.isFile || file.length() <= 0L) {
            helperLog(TAG, "drop queued ${event.key}: config/file unavailable reason=${request.reason}")
            return false
        }
        val routes = lockSoundRouteCandidates(request.preferredChannel)
        helperLog(
            TAG,
            "play ${event.key} file=${file.name} routes=$routes " +
                "playerVolume=1.0 playerGain=$LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB pcmGain=$LOCK_SOUND_PCM_ENHANCER_GAIN_MB reason=${request.reason}"
        )
        routes.forEachIndexed { index, channel ->
            val routeReason = if (index == 0) request.reason else "${request.reason} retry=${channel.shortLabel}"
            if (tryPlayEventOnChannelLocked(event, eventCfg, file, channel, routeReason)) {
                return true
            }
            helperLog(TAG, "route failed event=${event.key} channel=$channel index=$index")
        }
        return false
    }

    private fun tryPlayEventOnChannelLocked(
        event: LockEvent,
        eventCfg: EventConfig,
        file: File,
        channel: ExteriorChannel,
        reason: String,
    ): Boolean {
        var playbackChannel = channel
        activeChannel = channel
        activeEvent = event
        lastOpenDoorRouteRefreshAtMs = null
        openDoorChannel0SwitchAttempted = false
        val armed = routeController.arm(channel)
        if (!armed) {
            helperLog(TAG, "route arm failed event=${event.key} channel=$channel")
            routeController.disarm(channel)
            activeChannel = null
            activeEvent = null
            return false
        }
        sleepQuietly(if (channel == ExteriorChannel.Channel0) 300L else 120L)
        if (isDriverDoorOpen()) {
            if (channel == ExteriorChannel.Channel1) {
                openDoorChannel0SwitchAttempted = true
                val switched = routeController.switch(channel, ExteriorChannel.Channel0)
                if (switched != ExteriorChannel.Channel0) {
                    switched?.let { routeController.disarm(it) }
                    activeChannel = null
                    activeEvent = null
                    helperLog(
                        TAG,
                        "open-door setup switch failed event=${event.key} channel=$channel; try next route",
                    )
                    return false
                }
                playbackChannel = switched
                activeChannel = switched
                sleepQuietly(300L)
                helperLog(TAG, "open-door setup switched event=${event.key} channel=$channel->$switched")
            } else {
                val refreshed = routeController.refresh(channel)
                helperLog(TAG, "open-door setup refresh event=${event.key} channel=$channel ok=$refreshed")
            }
            lastOpenDoorRouteRefreshAtMs = System.currentTimeMillis()
        }
        val token = nextPlaybackTokenLocked()
        if (pcmPlayback.start(event, eventCfg, file, playbackChannel, token)) {
            startPlaybackTimeoutWatcher(event, token)
            return true
        }
        if (startMediaPlayerLocked(file, event, playbackChannel, token, "pcm_unavailable")) {
            startPlaybackTimeoutWatcher(event, token)
            return true
        }
        stopActiveLocked("start_failed_${playbackChannel.shortLabel}", releaseRoute = true)
        return false
    }

    private fun nextPlaybackTokenLocked(): Long {
        playbackTokenSeed += 1
        activePlaybackToken = playbackTokenSeed
        return playbackTokenSeed
    }

    private fun fallbackToMediaPlayerFromPcm(
        token: Long,
        event: LockEvent,
        file: File,
        channel: ExteriorChannel,
        cause: Throwable,
    ) {
        synchronized(playLock) {
            if (activePlaybackToken != token || activeEvent != event) {
                helperLog(TAG, "ignore stale pcm fallback event=${event.key} cause=${throwableSummary(cause)}")
                return
            }
            pcmPlayback.releaseTrack()
            val routeChannel = activeChannel ?: channel
            if (!startMediaPlayerLocked(file, event, routeChannel, token, "pcm_error:${cause.javaClass.simpleName}")) {
                finishActiveLocked("pcm_fallback_failed", releaseRoute = true)
            }
        }
    }

    private fun startMediaPlayerLocked(
        file: File,
        event: LockEvent,
        channel: ExteriorChannel,
        token: Long,
        reason: String,
    ): Boolean =
        mediaPlayback.start(
            file = file,
            event = event,
            channel = channel,
            reason = reason,
            onCompletion = { completePlayback("completion", token) },
            onError = { what, extra ->
                helperLog(TAG, "player error event=${event.key} what=$what extra=$extra")
                completePlayback("error:$what/$extra", token)
            },
        )

    private fun startPlaybackTimeoutWatcher(event: LockEvent, token: Long) {
        val timeoutMs = if (event.looping) LOOPING_PLAYBACK_TIMEOUT_MS else PLAYBACK_TIMEOUT_MS
        Thread({
            sleepQuietly(timeoutMs)
            synchronized(playLock) {
                if (activePlaybackToken != token || activeEvent != event) return@synchronized
                helperLog(TAG, "playback timeout event=${event.key} timeoutMs=$timeoutMs")
                finishActiveLocked("timeout_${event.key}", releaseRoute = true)
            }
        }, "WJX-LockSoundTimeout-${event.key}").apply { isDaemon = true; start() }
    }

    private fun completePlayback(reason: String, token: Long? = null) {
        Thread({
            synchronized(playLock) {
                if (token != null && token != activePlaybackToken) {
                    helperLog(TAG, "ignore stale completion reason=$reason token=$token active=$activePlaybackToken")
                    return@synchronized
                }
                helperLog(TAG, "complete playback reason=$reason")
                finishActiveLocked(reason, releaseRoute = true)
            }
        }, "WJX-LockSoundStop").apply { isDaemon = true; start() }
    }

    private fun stopActiveLocked(
        reason: String,
        releaseRoute: Boolean,
        routeReleaseDelayMs: Long = ROUTE_RELEASE_DRAIN_MS,
    ) {
        val channel = activeChannel
        muteAndReleasePlayer()
        playbackTokenSeed += 1
        activePlaybackToken = playbackTokenSeed
        if (releaseRoute && channel != null) {
            if (routeReleaseDelayMs > 0L) sleepQuietly(routeReleaseDelayMs)
            routeController.disarm(channel)
        }
        activeChannel = null
        activeEvent = null
        lastOpenDoorRouteRefreshAtMs = null
        openDoorChannel0SwitchAttempted = false
        helperLog(TAG, "stopped reason=$reason channel=$channel")
    }

    private fun finishActiveLocked(
        reason: String,
        releaseRoute: Boolean,
        routeReleaseDelayMs: Long = ROUTE_RELEASE_DRAIN_MS,
    ) {
        stopActiveLocked(reason, releaseRoute, routeReleaseDelayMs)
        startNextLocked()
    }

    private fun startNextLocked() {
        while (activeEvent == null) {
            val next = requestQueue.poll() ?: return
            helperLog(TAG, "dequeue event=${next.event.key} remaining=${requestQueue.size}")
            if (startRequestLocked(next)) return
        }
    }

    private fun muteAndReleasePlayer() {
        mediaPlayback.release()
        pcmPlayback.releaseAll()
    }

    private companion object {
        private const val TAG = "LockSound"
        private const val ROUTE_RELEASE_DRAIN_MS = 180L
        private const val PLAYBACK_TIMEOUT_MS = 180_000L
        private const val LOOPING_PLAYBACK_TIMEOUT_MS = 15L * 60L * 1000L

        private fun sleepQuietly(ms: Long) {
            runCatching { Thread.sleep(ms) }
        }
    }
}
