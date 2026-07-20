package com.byd.wjx.helper

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.IBinder
import com.byd.wjx.audio.ExteriorChannel
import com.byd.wjx.audio.WjxAudioAttributes
import java.io.File
import java.util.ArrayDeque
import kotlin.math.max

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

private data class PlaybackRequest(
    val event: LockEvent,
    val eventCfg: EventConfig,
    val preferredChannel: ExteriorChannel,
    val reason: String,
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
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var pcmThread: Thread? = null
    @Volatile private var enhancer: LoudnessEnhancer? = null
    @Volatile private var activeChannel: ExteriorChannel? = null
    @Volatile private var activeEvent: LockEvent? = null
    @Volatile private var activePlaybackToken: Long = 0L
    private var playbackTokenSeed = 0L
    private var lastOpenDoorRouteRefreshAtMs: Long? = null
    private var openDoorChannel0SwitchAttempted = false
    private val pendingRequests = ArrayDeque<PlaybackRequest>()

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
        val removed = pendingRequests.removeAll { it.event == event }
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
        if (activeEvent == event || pendingRequests.any { it.event == event }) {
            helperLog(TAG, "merge ${event.key}: already active/queued reason=$reason")
            return@synchronized true
        }
        val request = PlaybackRequest(event, eventCfg, preferredChannel, reason)
        if (activeEvent != null) {
            if (pendingRequests.size >= MAX_PENDING_REQUESTS) {
                helperLog(TAG, "queue full event=${event.key} active=$activeEvent reason=$reason")
                return@synchronized false
            }
            pendingRequests.addLast(request)
            helperLog(TAG, "queued event=${event.key} active=$activeEvent size=${pendingRequests.size} reason=$reason")
            return@synchronized true
        }
        startRequestLocked(request)
    }

    private fun startRequestLocked(request: PlaybackRequest): Boolean {
        val event = request.event
        val eventCfg = request.eventCfg
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
        if (startPcmPlaybackLocked(event, eventCfg, file, playbackChannel, token)) {
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

    private fun startPcmPlaybackLocked(
        event: LockEvent,
        eventCfg: EventConfig,
        fallbackFile: File,
        channel: ExteriorChannel,
        token: Long,
    ): Boolean {
        val pcm = eventCfg.pcm
        val pcmFile = File(pcm.path)
        if (!pcm.usable() || !pcmFile.isFile || pcmFile.length() <= 0L) {
            helperLog(
                TAG,
                "pcm unavailable event=${event.key} path=${pcm.path.ifBlank { "none" }} " +
                    "fallbackFile=${fallbackFile.name} exists=${fallbackFile.isFile} fallback=MediaPlayer"
            )
            return false
        }
        val channelMask = if (pcm.channelCount <= 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(pcm.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            helperLog(TAG, "pcm buffer invalid event=${event.key} rate=${pcm.sampleRate} channels=${pcm.channelCount} min=$minBuffer fallback=MediaPlayer")
            return false
        }
        val bufferBytes = max(minBuffer, PCM_TRACK_BUFFER_BYTES)
        if (!event.looping && startPcmStaticPlaybackLocked(event, fallbackFile, channel, token, pcm, pcmFile, channelMask, bufferBytes)) {
            return true
        }
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(WjxAudioAttributes.forOutside(channel))
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(pcm.sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()
        }.getOrElse {
            helperLog(TAG, "pcm AudioTrack build failed event=${event.key}: ${throwableSummary(it)} fallback=MediaPlayer")
            return false
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            helperLog(TAG, "pcm AudioTrack not initialized event=${event.key} state=${track.state} fallback=MediaPlayer")
            runCatching { track.release() }
            return false
        }
        return runCatching {
            applyTrackVolume(track)
            audioTrack = track
            enableEnhancer(track, channel)
            track.play()
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                throw IllegalStateException("AudioTrack playState=${track.playState}")
            }
            val writer = Thread({
                writePcmPlayback(token, event, fallbackFile, channel, pcm, pcmFile, track)
            }, "WJX-LockSoundPcm-${event.key}").apply {
                isDaemon = true
            }
            pcmThread = writer
            writer.start()
            helperLog(
                TAG,
                "pcm playback started event=${event.key} file=${pcmFile.name} bytes=${pcmFile.length()} " +
                    "rate=${pcm.sampleRate} channels=${pcm.channelCount} buffer=$bufferBytes"
            )
            true
        }.getOrElse {
            helperLog(TAG, "pcm start failed event=${event.key}: ${throwableSummary(it)} fallback=MediaPlayer")
            releaseAudioTrackOnly()
            false
        }
    }

    private fun startPcmStaticPlaybackLocked(
        event: LockEvent,
        fallbackFile: File,
        channel: ExteriorChannel,
        token: Long,
        pcm: PcmConfig,
        pcmFile: File,
        channelMask: Int,
        minBufferBytes: Int,
    ): Boolean {
        val frameBytes = (pcm.channelCount.coerceIn(1, 2) * PCM_BYTES_PER_SAMPLE).coerceAtLeast(PCM_BYTES_PER_SAMPLE)
        val leadBytes = alignPcmBytes(
            bytes = (pcm.sampleRate.toLong() * frameBytes * PCM_LEAD_SILENCE_MS / 1000L).toInt(),
            frameBytes = frameBytes,
        )
        val totalBytesLong = leadBytes.toLong() + pcmFile.length()
        if (totalBytesLong <= 0L || totalBytesLong > PCM_STATIC_MAX_BYTES || totalBytesLong > Int.MAX_VALUE) {
            helperLog(TAG, "pcm static skipped event=${event.key} bytes=$totalBytesLong")
            return false
        }
        val payload = ByteArray(totalBytesLong.toInt())
        val readOk = runCatching {
            pcmFile.inputStream().use { input ->
                var offset = leadBytes
                while (offset < payload.size) {
                    val read = input.read(payload, offset, payload.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                offset == payload.size
            }
        }.getOrDefault(false)
        if (!readOk) {
            helperLog(TAG, "pcm static read failed event=${event.key} file=${pcmFile.name}")
            return false
        }
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(WjxAudioAttributes.forOutside(channel))
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(pcm.sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(max(minBufferBytes, payload.size))
                .build()
        }.getOrElse {
            helperLog(TAG, "pcm static AudioTrack build failed event=${event.key}: ${throwableSummary(it)}")
            return false
        }
        if (track.state != AudioTrack.STATE_INITIALIZED && track.state != AudioTrack.STATE_NO_STATIC_DATA) {
            helperLog(TAG, "pcm static AudioTrack not initialized event=${event.key} state=${track.state}")
            runCatching { track.release() }
            return false
        }
        return runCatching {
            val written = track.write(payload, 0, payload.size)
            if (written < payload.size) throw IllegalStateException("static write=$written/${payload.size}")
            if (track.state != AudioTrack.STATE_INITIALIZED) throw IllegalStateException("static state after write=${track.state}")
            applyTrackVolume(track)
            audioTrack = track
            enableEnhancer(track, channel)
            track.play()
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                throw IllegalStateException("AudioTrack static playState=${track.playState}")
            }
            val watcher = Thread({
                waitForStaticPcmCompletion(token, event, fallbackFile, channel, payload.size, pcm, track)
            }, "WJX-LockSoundPcmStatic-${event.key}").apply { isDaemon = true }
            pcmThread = watcher
            watcher.start()
            helperLog(
                TAG,
                "pcm static started event=${event.key} file=${pcmFile.name} bytes=${payload.size} " +
                    "leadMs=$PCM_LEAD_SILENCE_MS rate=${pcm.sampleRate} channels=${pcm.channelCount}"
            )
            true
        }.getOrElse {
            helperLog(TAG, "pcm static start failed event=${event.key}: ${throwableSummary(it)} fallback=stream")
            releaseAudioTrackOnly()
            false
        }
    }

    private fun waitForStaticPcmCompletion(
        token: Long,
        event: LockEvent,
        fallbackFile: File,
        channel: ExteriorChannel,
        totalBytes: Int,
        pcm: PcmConfig,
        track: AudioTrack,
    ) {
        val frameBytes = (pcm.channelCount.coerceIn(1, 2) * PCM_BYTES_PER_SAMPLE).coerceAtLeast(PCM_BYTES_PER_SAMPLE)
        val bytesPerSecond = (pcm.sampleRate.toLong() * frameBytes).coerceAtLeast(1L)
        var remaining = ((totalBytes.toLong() * 1000L) / bytesPerSecond + 300L).coerceAtLeast(300L)
        try {
            while (remaining > 0L && activePlaybackToken == token && audioTrack === track) {
                val sleep = minOf(100L, remaining)
                sleepQuietly(sleep)
                remaining -= sleep
            }
            if (activePlaybackToken == token && audioTrack === track) {
                completePlayback("pcm_static_completion", token)
            }
        } catch (t: Throwable) {
            helperLog(TAG, "pcm static watcher error event=${event.key}: ${throwableSummary(t)} fallback=MediaPlayer")
            fallbackToMediaPlayerFromPcm(token, event, fallbackFile, channel, t)
        }
    }

    private fun writePcmPlayback(
        token: Long,
        event: LockEvent,
        fallbackFile: File,
        channel: ExteriorChannel,
        pcm: PcmConfig,
        pcmFile: File,
        track: AudioTrack,
    ) {
        val buffer = ByteArray(PCM_WRITE_BUFFER_BYTES)
        var completed = false
        var totalWritten = 0L
        try {
            totalWritten += writePcmLeadSilence(token, track, pcm)
            do {
                completed = false
                pcmFile.inputStream().use { input ->
                    while (activePlaybackToken == token && audioTrack === track) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            completed = true
                            break
                        }
                        var offset = 0
                        while (offset < read && activePlaybackToken == token && audioTrack === track) {
                            val written = track.write(buffer, offset, read - offset, AudioTrack.WRITE_BLOCKING)
                            if (written < 0) throw IllegalStateException("AudioTrack.write=$written")
                            if (written == 0) {
                                sleepQuietly(5L)
                            } else {
                                offset += written
                                totalWritten += written
                            }
                        }
                    }
                }
                if (completed && event.looping && activePlaybackToken == token && audioTrack === track) {
                    helperLog(TAG, "pcm loop restart event=${event.key}")
                }
            } while (event.looping && completed && activePlaybackToken == token && audioTrack === track)
            if (completed && !event.looping && activePlaybackToken == token && audioTrack === track) {
                waitForStreamPcmDrain(token, event, pcm, track, totalWritten)
                completePlayback("pcm_completion", token)
            }
        } catch (t: Throwable) {
            helperLog(TAG, "pcm playback error event=${event.key}: ${throwableSummary(t)} fallback=MediaPlayer")
            fallbackToMediaPlayerFromPcm(token, event, fallbackFile, channel, t)
        }
    }

    private fun waitForStreamPcmDrain(
        token: Long,
        event: LockEvent,
        pcm: PcmConfig,
        track: AudioTrack,
        totalBytes: Long,
    ) {
        val frameBytes = (pcm.channelCount.coerceIn(1, 2) * PCM_BYTES_PER_SAMPLE).coerceAtLeast(PCM_BYTES_PER_SAMPLE)
        val totalFrames = (totalBytes / frameBytes).coerceAtLeast(1L)
        val bytesPerSecond = (pcm.sampleRate.toLong() * frameBytes).coerceAtLeast(1L)
        val maxWaitMs = ((totalBytes * 1000L) / bytesPerSecond + 600L).coerceAtLeast(300L)
        val startedAt = System.currentTimeMillis()
        var lastHead = 0L
        while (activePlaybackToken == token && audioTrack === track) {
            val head = runCatching { track.playbackHeadPosition.toLong() and 0xffffffffL }.getOrDefault(lastHead)
            lastHead = head
            if (head >= totalFrames) break
            if (System.currentTimeMillis() - startedAt >= maxWaitMs) break
            sleepQuietly(40L)
        }
        helperLog(
            TAG,
            "pcm stream drain event=${event.key} frames=$lastHead/$totalFrames wait=${System.currentTimeMillis() - startedAt}ms"
        )
    }

    private fun writePcmLeadSilence(token: Long, track: AudioTrack, pcm: PcmConfig): Long {
        val frameBytes = (pcm.channelCount.coerceIn(1, 2) * PCM_BYTES_PER_SAMPLE).coerceAtLeast(PCM_BYTES_PER_SAMPLE)
        var remaining = alignPcmBytes(
            bytes = (pcm.sampleRate.toLong() * frameBytes * PCM_LEAD_SILENCE_MS / 1000L).toInt(),
            frameBytes = frameBytes,
        )
        if (remaining <= 0) return 0L
        val chunk = ByteArray(alignPcmBytes(minOf(PCM_WRITE_BUFFER_BYTES, remaining), frameBytes))
        var totalWritten = 0L
        while (remaining > 0 && activePlaybackToken == token && audioTrack === track) {
            val toWrite = minOf(chunk.size, remaining)
            var offset = 0
            while (offset < toWrite && activePlaybackToken == token && audioTrack === track) {
                val written = track.write(chunk, offset, toWrite - offset, AudioTrack.WRITE_BLOCKING)
                if (written < 0) throw IllegalStateException("AudioTrack.write silence=$written")
                if (written == 0) {
                    sleepQuietly(5L)
                } else {
                    offset += written
                    totalWritten += written
                }
            }
            remaining -= toWrite
        }
        return totalWritten
    }

    private fun alignPcmBytes(bytes: Int, frameBytes: Int): Int {
        if (bytes <= 0) return 0
        val frame = frameBytes.coerceAtLeast(1)
        return bytes - (bytes % frame)
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
            releaseAudioTrackOnly()
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
    ): Boolean = runCatching {
        val mp = MediaPlayer().apply {
            setAudioAttributes(WjxAudioAttributes.forOutside(channel))
            setDataSource(file.absolutePath)
            setVolume(1.0f, 1.0f)
            isLooping = event.looping
            setOnCompletionListener { completePlayback("completion", token) }
            setOnErrorListener { _, what, extra ->
                helperLog(TAG, "player error event=${event.key} what=$what extra=$extra")
                completePlayback("error:$what/$extra", token)
                true
            }
            prepare()
        }
        player = mp
        enableEnhancer(mp, channel)
        mp.start()
        if (!mp.isPlaying) throw IllegalStateException("MediaPlayer did not enter playing state")
        helperLog(TAG, "MediaPlayer started event=${event.key} reason=$reason file=${file.name}")
        true
    }.getOrElse {
        helperLog(TAG, "MediaPlayer failed event=${event.key} reason=$reason: ${throwableSummary(it)}")
        releaseMediaPlayerOnly()
        false
    }

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
        while (activeEvent == null && pendingRequests.isNotEmpty()) {
            val next = pendingRequests.removeFirst()
            helperLog(TAG, "dequeue event=${next.event.key} remaining=${pendingRequests.size}")
            if (startRequestLocked(next)) return
        }
    }

    private fun muteAndReleasePlayer() {
        releaseMediaPlayerOnly()
        releaseAudioTrackOnly()
        pcmThread = null
    }

    private fun releaseMediaPlayerOnly() {
        val mp = player
        if (mp == null) return
        runCatching { mp?.setOnCompletionListener(null) }
        runCatching { mp?.setOnErrorListener(null) }
        runCatching { mp?.setVolume(0f, 0f) }
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.setTargetGain(0) }
        sleepQuietly(220L)
        runCatching { mp?.pause() }
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
        runCatching { enhancer?.release() }
        player = null
        enhancer = null
    }

    private fun releaseAudioTrackOnly() {
        val track = audioTrack
        if (track == null) return
        runCatching { track?.setVolume(0f) }
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.setTargetGain(0) }
        sleepQuietly(80L)
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        runCatching { enhancer?.release() }
        audioTrack = null
        enhancer = null
    }

    private fun enableEnhancer(mediaPlayer: MediaPlayer, channel: ExteriorChannel) {
        runCatching {
            val gain = LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB
            enhancer = LoudnessEnhancer(mediaPlayer.audioSessionId).apply {
                setTargetGain(gain)
                enabled = true
            }
            helperLog(TAG, "enhancer enabled gain=$gain playerVolume=1.0 channel=$channel mode=player")
        }.onFailure {
            helperLog(TAG, "enhancer failed: ${throwableSummary(it)}")
        }
    }

    private fun enableEnhancer(track: AudioTrack, channel: ExteriorChannel) {
        runCatching {
            val gain = LOCK_SOUND_PCM_ENHANCER_GAIN_MB
            enhancer = LoudnessEnhancer(track.audioSessionId).apply {
                setTargetGain(gain)
                enabled = true
            }
            helperLog(TAG, "pcm enhancer enabled gain=$gain trackVolume=full channel=$channel mode=pcm")
        }.onFailure {
            helperLog(TAG, "pcm enhancer failed: ${throwableSummary(it)}")
        }
    }

    private fun applyTrackVolume(track: AudioTrack) {
        val volume = AudioTrack.getMaxVolume()
        runCatching { track.setVolume(volume) }.onFailure {
            helperLog(TAG, "pcm volume failed: ${throwableSummary(it)}")
        }
    }

    private companion object {
        private const val TAG = "LockSound"
        private const val ROUTE_RELEASE_DRAIN_MS = 180L
        private const val PCM_TRACK_BUFFER_BYTES = 64 * 1024
        private const val PCM_WRITE_BUFFER_BYTES = 16 * 1024
        private const val PCM_LEAD_SILENCE_MS = 420L
        private const val PCM_STATIC_MAX_BYTES = 8L * 1024L * 1024L
        private const val MAX_PENDING_REQUESTS = 8
        private const val PLAYBACK_TIMEOUT_MS = 180_000L
        private const val LOOPING_PLAYBACK_TIMEOUT_MS = 15L * 60L * 1000L

        private fun sleepQuietly(ms: Long) {
            runCatching { Thread.sleep(ms) }
        }
    }
}
