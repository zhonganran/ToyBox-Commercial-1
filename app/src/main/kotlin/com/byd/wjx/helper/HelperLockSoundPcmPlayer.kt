package com.byd.wjx.helper

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import com.byd.wjx.audio.ExteriorChannel
import com.byd.wjx.audio.WjxAudioAttributes
import java.io.File
import kotlin.math.max

internal const val LOCK_SOUND_PCM_LEAD_SILENCE_MS = 420L
internal const val LOCK_SOUND_PCM_STATIC_MAX_BYTES = 8L * 1024L * 1024L

internal fun lockSoundPcmFrameBytes(channelCount: Int): Int =
    (channelCount.coerceIn(1, 2) * PCM_BYTES_PER_SAMPLE).coerceAtLeast(PCM_BYTES_PER_SAMPLE)

internal fun alignLockSoundPcmBytes(bytes: Int, frameBytes: Int): Int {
    if (bytes <= 0) return 0
    val frame = frameBytes.coerceAtLeast(1)
    return bytes - (bytes % frame)
}

internal fun lockSoundPcmLeadBytes(pcm: PcmConfig): Int {
    val frameBytes = lockSoundPcmFrameBytes(pcm.channelCount)
    return alignLockSoundPcmBytes(
        bytes = (pcm.sampleRate.toLong() * frameBytes * LOCK_SOUND_PCM_LEAD_SILENCE_MS / 1000L).toInt(),
        frameBytes = frameBytes,
    )
}

internal fun lockSoundStaticPayloadBytes(pcm: PcmConfig, pcmBytes: Long): Int? {
    val totalBytes = lockSoundPcmLeadBytes(pcm).toLong() + pcmBytes
    return totalBytes
        .takeIf { it > 0L && it <= LOCK_SOUND_PCM_STATIC_MAX_BYTES && it <= Int.MAX_VALUE }
        ?.toInt()
}

internal class LockSoundPcmPlayer(
    private val isTokenActive: (Long) -> Boolean,
    private val onComplete: (reason: String, token: Long) -> Unit,
    private val onFallback: (
        token: Long,
        event: LockEvent,
        file: File,
        channel: ExteriorChannel,
        cause: Throwable,
    ) -> Unit,
) {
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var workerThread: Thread? = null
    @Volatile private var enhancer: LoudnessEnhancer? = null

    fun start(
        event: LockEvent,
        eventConfig: EventConfig,
        fallbackFile: File,
        channel: ExteriorChannel,
        token: Long,
    ): Boolean {
        val pcm = eventConfig.pcm
        val pcmFile = File(pcm.path)
        if (!pcm.usable() || !pcmFile.isFile || pcmFile.length() <= 0L) {
            helperLog(
                TAG,
                "pcm unavailable event=${event.key} path=${pcm.path.ifBlank { "none" }} " +
                    "fallbackFile=${fallbackFile.name} exists=${fallbackFile.isFile} fallback=MediaPlayer",
            )
            return false
        }
        val channelMask = if (pcm.channelCount <= 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(pcm.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            helperLog(
                TAG,
                "pcm buffer invalid event=${event.key} rate=${pcm.sampleRate} " +
                    "channels=${pcm.channelCount} min=$minBuffer fallback=MediaPlayer",
            )
            return false
        }
        val bufferBytes = max(minBuffer, PCM_TRACK_BUFFER_BYTES)
        if (!event.looping && startStatic(event, fallbackFile, channel, token, pcm, pcmFile, channelMask, bufferBytes)) {
            return true
        }
        return startStreaming(event, fallbackFile, channel, token, pcm, pcmFile, channelMask, bufferBytes)
    }

    fun releaseTrack() {
        val track = audioTrack ?: return
        val currentEnhancer = enhancer
        runCatching { track.setVolume(0f) }
        runCatching { currentEnhancer?.enabled = false }
        runCatching { currentEnhancer?.setTargetGain(0) }
        sleepQuietly(80L)
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
        runCatching { currentEnhancer?.release() }
        audioTrack = null
        enhancer = null
    }

    fun releaseAll() {
        releaseTrack()
        workerThread = null
    }

    private fun startStreaming(
        event: LockEvent,
        fallbackFile: File,
        channel: ExteriorChannel,
        token: Long,
        pcm: PcmConfig,
        pcmFile: File,
        channelMask: Int,
        bufferBytes: Int,
    ): Boolean {
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(WjxAudioAttributes.forOutside(channel))
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(pcm.sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
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
                writeStreaming(token, event, fallbackFile, channel, pcm, pcmFile, track)
            }, "WJX-LockSoundPcm-${event.key}").apply {
                isDaemon = true
            }
            workerThread = writer
            writer.start()
            helperLog(
                TAG,
                "pcm playback started event=${event.key} file=${pcmFile.name} bytes=${pcmFile.length()} " +
                    "rate=${pcm.sampleRate} channels=${pcm.channelCount} buffer=$bufferBytes",
            )
            true
        }.getOrElse {
            helperLog(TAG, "pcm start failed event=${event.key}: ${throwableSummary(it)} fallback=MediaPlayer")
            releaseTrack()
            false
        }
    }

    private fun startStatic(
        event: LockEvent,
        fallbackFile: File,
        channel: ExteriorChannel,
        token: Long,
        pcm: PcmConfig,
        pcmFile: File,
        channelMask: Int,
        minBufferBytes: Int,
    ): Boolean {
        val leadBytes = lockSoundPcmLeadBytes(pcm)
        val totalBytes = lockSoundStaticPayloadBytes(pcm, pcmFile.length())
        if (totalBytes == null) {
            helperLog(TAG, "pcm static skipped event=${event.key} bytes=${leadBytes.toLong() + pcmFile.length()}")
            return false
        }
        val payload = ByteArray(totalBytes)
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
                        .build(),
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
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                throw IllegalStateException("static state after write=${track.state}")
            }
            applyTrackVolume(track)
            audioTrack = track
            enableEnhancer(track, channel)
            track.play()
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                throw IllegalStateException("AudioTrack static playState=${track.playState}")
            }
            val watcher = Thread({
                waitForStaticCompletion(token, event, fallbackFile, channel, payload.size, pcm, track)
            }, "WJX-LockSoundPcmStatic-${event.key}").apply { isDaemon = true }
            workerThread = watcher
            watcher.start()
            helperLog(
                TAG,
                "pcm static started event=${event.key} file=${pcmFile.name} bytes=${payload.size} " +
                    "leadMs=$LOCK_SOUND_PCM_LEAD_SILENCE_MS rate=${pcm.sampleRate} channels=${pcm.channelCount}",
            )
            true
        }.getOrElse {
            helperLog(TAG, "pcm static start failed event=${event.key}: ${throwableSummary(it)} fallback=stream")
            if (audioTrack === track) releaseTrack() else runCatching { track.release() }
            false
        }
    }

    private fun waitForStaticCompletion(
        token: Long,
        event: LockEvent,
        fallbackFile: File,
        channel: ExteriorChannel,
        totalBytes: Int,
        pcm: PcmConfig,
        track: AudioTrack,
    ) {
        val frameBytes = lockSoundPcmFrameBytes(pcm.channelCount)
        val bytesPerSecond = (pcm.sampleRate.toLong() * frameBytes).coerceAtLeast(1L)
        var remaining = ((totalBytes.toLong() * 1000L) / bytesPerSecond + 300L).coerceAtLeast(300L)
        try {
            while (remaining > 0L && isActive(token, track)) {
                val sleep = minOf(100L, remaining)
                sleepQuietly(sleep)
                remaining -= sleep
            }
            if (isActive(token, track)) onComplete("pcm_static_completion", token)
        } catch (t: Throwable) {
            helperLog(TAG, "pcm static watcher error event=${event.key}: ${throwableSummary(t)} fallback=MediaPlayer")
            onFallback(token, event, fallbackFile, channel, t)
        }
    }

    private fun writeStreaming(
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
            totalWritten += writeLeadSilence(token, track, pcm)
            do {
                completed = false
                pcmFile.inputStream().use { input ->
                    while (isActive(token, track)) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            completed = true
                            break
                        }
                        var offset = 0
                        while (offset < read && isActive(token, track)) {
                            val written = track.write(buffer, offset, read - offset, AudioTrack.WRITE_BLOCKING)
                            if (written < 0) throw IllegalStateException("AudioTrack.write=$written")
                            if (written == 0) sleepQuietly(5L) else {
                                offset += written
                                totalWritten += written
                            }
                        }
                    }
                }
                if (completed && event.looping && isActive(token, track)) {
                    helperLog(TAG, "pcm loop restart event=${event.key}")
                }
            } while (event.looping && completed && isActive(token, track))
            if (completed && !event.looping && isActive(token, track)) {
                waitForStreamDrain(token, event, pcm, track, totalWritten)
                onComplete("pcm_completion", token)
            }
        } catch (t: Throwable) {
            helperLog(TAG, "pcm playback error event=${event.key}: ${throwableSummary(t)} fallback=MediaPlayer")
            onFallback(token, event, fallbackFile, channel, t)
        }
    }

    private fun waitForStreamDrain(
        token: Long,
        event: LockEvent,
        pcm: PcmConfig,
        track: AudioTrack,
        totalBytes: Long,
    ) {
        val frameBytes = lockSoundPcmFrameBytes(pcm.channelCount)
        val totalFrames = (totalBytes / frameBytes).coerceAtLeast(1L)
        val bytesPerSecond = (pcm.sampleRate.toLong() * frameBytes).coerceAtLeast(1L)
        val maxWaitMs = ((totalBytes * 1000L) / bytesPerSecond + 600L).coerceAtLeast(300L)
        val startedAt = System.currentTimeMillis()
        var lastHead = 0L
        while (isActive(token, track)) {
            val head = runCatching { track.playbackHeadPosition.toLong() and 0xffffffffL }.getOrDefault(lastHead)
            lastHead = head
            if (head >= totalFrames) break
            if (System.currentTimeMillis() - startedAt >= maxWaitMs) break
            sleepQuietly(40L)
        }
        helperLog(
            TAG,
            "pcm stream drain event=${event.key} frames=$lastHead/$totalFrames " +
                "wait=${System.currentTimeMillis() - startedAt}ms",
        )
    }

    private fun writeLeadSilence(token: Long, track: AudioTrack, pcm: PcmConfig): Long {
        val frameBytes = lockSoundPcmFrameBytes(pcm.channelCount)
        var remaining = lockSoundPcmLeadBytes(pcm)
        if (remaining <= 0) return 0L
        val chunk = ByteArray(alignLockSoundPcmBytes(minOf(PCM_WRITE_BUFFER_BYTES, remaining), frameBytes))
        var totalWritten = 0L
        while (remaining > 0 && isActive(token, track)) {
            val toWrite = minOf(chunk.size, remaining)
            var offset = 0
            while (offset < toWrite && isActive(token, track)) {
                val written = track.write(chunk, offset, toWrite - offset, AudioTrack.WRITE_BLOCKING)
                if (written < 0) throw IllegalStateException("AudioTrack.write silence=$written")
                if (written == 0) sleepQuietly(5L) else {
                    offset += written
                    totalWritten += written
                }
            }
            remaining -= toWrite
        }
        return totalWritten
    }

    private fun isActive(token: Long, track: AudioTrack): Boolean =
        isTokenActive(token) && audioTrack === track

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

    private fun sleepQuietly(ms: Long) {
        runCatching { Thread.sleep(ms) }
    }

    private companion object {
        private const val TAG = "LockSound"
        private const val PCM_TRACK_BUFFER_BYTES = 64 * 1024
        private const val PCM_WRITE_BUFFER_BYTES = 16 * 1024
    }
}
