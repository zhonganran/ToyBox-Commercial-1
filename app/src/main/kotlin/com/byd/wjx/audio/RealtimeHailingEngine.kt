package com.byd.wjx.audio

import android.content.Context
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Process
import com.byd.wjx.log.WjxFileLogger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class RealtimeHailingEngine(
    context: Context,
    channel: ExteriorChannel,
    initialTuning: RealtimeHailingTuning,
    private val onError: (String) -> Unit,
) {
    @Volatile var isRunning: Boolean = false
        private set
    @Volatile private var currentTuning = initialTuning
    private val resourceFactory = RealtimeHailingAudioResourceFactory(context, channel)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioEffects: RealtimeHailingAudioEffects? = null
    private var recordThread: Thread? = null
    private var playbackThread: Thread? = null
    private var processor: HailingAudioProcessor? = null
    private var queue: ArrayBlockingQueue<ShortArray>? = null
    private var frameSamples = RealtimeHailingAudioSpec.frameSamples(
        RealtimeHailingAudioSpec.DEFAULT_SAMPLE_RATE,
    )

    fun start(): Boolean {
        if (isRunning) return true
        val resources = resourceFactory.create() ?: return false
        audioRecord = resources.record
        audioTrack = resources.track
        audioEffects = RealtimeHailingAudioEffects.create(
            recordSessionId = resources.record.audioSessionId,
            track = resources.track,
            tuning = currentTuning,
        )
        frameSamples = RealtimeHailingAudioSpec.frameSamples(resources.sampleRate)
        queue = ArrayBlockingQueue(QUEUE_SIZE)
        isRunning = true

        val localQueue = queue ?: return false
        val localProcessor = HailingAudioProcessor(currentTuning)
        processor = localProcessor
        val startupLatch = CountDownLatch(2)
        val startupError = AtomicReference<String?>(null)
        recordThread = Thread({
            runRecordLoop(resources.record, localQueue, startupLatch, startupError)
        }, "WJX-HailingRecord").also { it.start() }
        playbackThread = Thread({
            runPlaybackLoop(resources.track, localQueue, localProcessor, startupLatch, startupError)
        }, "WJX-HailingTrack").also { it.start() }
        val ready = runCatching {
            startupLatch.await(STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        val error = startupError.get()
        if (!ready || error != null) {
            WjxFileLogger.w(TAG, "engine startup failed ready=$ready error=${error.orEmpty()}")
            stop()
            return false
        }
        return true
    }

    fun stop() {
        stopForRouteRelease()
    }

    fun stopForRouteRelease() {
        val wasRunning = isRunning
        muteForRouteRelease()
        isRunning = false
        stopAudioDevices()
        runCatching { recordThread?.interrupt() }
        runCatching { playbackThread?.interrupt() }
        runCatching { recordThread?.join(350L) }
        runCatching { playbackThread?.join(350L) }
        stopAudioDevices()
        if (wasRunning) {
            WjxFileLogger.i(TAG, "hailing audio output stopped and flushed before route release")
        }
        release()
    }

    fun updateTuning(tuning: RealtimeHailingTuning) {
        currentTuning = tuning
        processor?.update(tuning)
        audioEffects?.update(audioTrack, tuning)
    }

    fun muteForRouteRelease() {
        runCatching { queue?.clear() }
        audioEffects?.mute(audioTrack)
        runCatching {
            val track = audioTrack ?: return@runCatching
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                val silence = ShortArray(frameSamples * RealtimeHailingAudioSpec.OUTPUT_CHANNELS)
                track.write(silence, 0, silence.size, AudioTrack.WRITE_NON_BLOCKING)
            }
        }
    }

    private fun stopAudioDevices() {
        runCatching {
            val record = audioRecord ?: return@runCatching
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        }
        runCatching {
            val track = audioTrack ?: return@runCatching
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.flush()
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                track.stop()
            }
            track.flush()
        }
    }

    private fun runRecordLoop(
        record: AudioRecord,
        bufferQueue: ArrayBlockingQueue<ShortArray>,
        startupLatch: CountDownLatch,
        startupError: AtomicReference<String?>,
    ) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val scratch = ShortArray(frameSamples)
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                reportLoopFailure("麦克风启动失败", startupLatch, startupError)
                return
            }
            startupLatch.countDown()
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val read = record.read(scratch, 0, scratch.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    val frame = if (read == scratch.size) scratch.copyOf() else ShortArray(frameSamples).also {
                        System.arraycopy(scratch, 0, it, 0, read)
                    }
                    if (!bufferQueue.offer(frame)) {
                        bufferQueue.poll()
                        bufferQueue.offer(frame)
                    }
                } else if (read < 0) {
                    reportLoopFailure("麦克风读取失败：$read", startupLatch, startupError)
                    return
                }
            }
        } catch (error: Exception) {
            if (isRunning) reportLoopFailure("麦克风异常：${error.message}", startupLatch, startupError)
        } finally {
            runCatching { if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() }
        }
    }

    private fun runPlaybackLoop(
        track: AudioTrack,
        bufferQueue: ArrayBlockingQueue<ShortArray>,
        processor: HailingAudioProcessor,
        startupLatch: CountDownLatch,
        startupError: AtomicReference<String?>,
    ) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val output = ShortArray(frameSamples * RealtimeHailingAudioSpec.OUTPUT_CHANNELS)
        val silence = ShortArray(frameSamples)
        try {
            applyRealtimeHailingTrackOutputVolume(track, currentTuning)
            track.play()
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                reportLoopFailure("车外输出启动失败", startupLatch, startupError)
                return
            }
            writeStartupSilence(track)
            startupLatch.countDown()
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val frame = bufferQueue.poll(QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: silence
                val writtenSamples = processor.process(frame, output)
                if (writtenSamples <= 0) continue
                val writeOk = writeTrackFrame(track, output, writtenSamples, startupLatch, startupError)
                if (!writeOk) {
                    return
                }
            }
        } catch (error: Exception) {
            if (isRunning) reportLoopFailure("车外输出异常：${error.message}", startupLatch, startupError)
        } finally {
            runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
        }
    }

    private fun writeStartupSilence(track: AudioTrack) {
        val silence = ShortArray(
            (frameSamples * RealtimeHailingAudioSpec.OUTPUT_CHANNELS)
                .coerceAtLeast(RealtimeHailingAudioSpec.DEFAULT_SAMPLE_RATE / 100),
        )
        repeat(STARTUP_SILENCE_FRAMES) {
            if (!isRunning || Thread.currentThread().isInterrupted) return
            runCatching {
                track.write(silence, 0, silence.size, AudioTrack.WRITE_BLOCKING)
            }.onFailure {
                WjxFileLogger.w(TAG, "startup silence failed: ${it.message}")
                return
            }
        }
    }

    private fun writeTrackFrame(
        track: AudioTrack,
        output: ShortArray,
        sampleCount: Int,
        startupLatch: CountDownLatch,
        startupError: AtomicReference<String?>,
    ): Boolean {
        var offset = 0
        while (offset < sampleCount && isRunning && !Thread.currentThread().isInterrupted) {
            val result = track.write(output, offset, sampleCount - offset, AudioTrack.WRITE_BLOCKING)
            when {
                result > 0 -> offset += result
                result == 0 -> Thread.yield()
                else -> {
                    reportLoopFailure("车外输出失败：$result", startupLatch, startupError)
                    return false
                }
            }
        }
        return true
    }

    private fun reportLoopFailure(
        reason: String,
        startupLatch: CountDownLatch,
        startupError: AtomicReference<String?>,
    ) {
        if (startupLatch.count > 0) {
            startupError.compareAndSet(null, reason)
            while (startupLatch.count > 0) startupLatch.countDown()
        } else if (isRunning) {
            onError(reason)
        }
    }

    private fun release() {
        runCatching { queue?.clear() }
        queue = null
        audioEffects?.release()
        audioEffects = null
        processor = null
        runCatching { audioRecord?.release() }
        runCatching { audioTrack?.release() }
        audioRecord = null
        audioTrack = null
        recordThread = null
        playbackThread = null
    }

    companion object {
        private const val TAG = "WjxRealtimeHailing"
        private const val QUEUE_SIZE = 3
        private const val QUEUE_TIMEOUT_MS = 20L
        private const val STARTUP_TIMEOUT_MS = 900L
        private const val STARTUP_SILENCE_FRAMES = 6
    }
}
