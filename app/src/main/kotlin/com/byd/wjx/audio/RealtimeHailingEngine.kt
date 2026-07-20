package com.byd.wjx.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import com.byd.wjx.log.WjxFileLogger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

internal class RealtimeHailingEngine(
    private val context: Context,
    private val channel: ExteriorChannel,
    initialTuning: RealtimeHailingTuning,
    private val onError: (String) -> Unit,
) {
    @Volatile var isRunning: Boolean = false
        private set
    @Volatile private var currentTuning = initialTuning

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var recordThread: Thread? = null
    private var playbackThread: Thread? = null
    private var effects: AudioEffects? = null
    private var processor: HailingAudioProcessor? = null
    private var queue: ArrayBlockingQueue<ShortArray>? = null
    private var sampleRate = DEFAULT_SAMPLE_RATE
    private var frameSamples = DEFAULT_SAMPLE_RATE / FRAMES_PER_SECOND

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRunning) return true
        val resources = createAudioResources() ?: return false
        audioRecord = resources.record
        audioTrack = resources.track
        effects = AudioEffects.create(resources.record.audioSessionId, currentTuning)
        loudnessEnhancer = createOutputEnhancer(resources.track, currentTuning)
        applyTrackOutputVolume(resources.track, currentTuning)
        sampleRate = resources.sampleRate
        frameSamples = resources.sampleRate / FRAMES_PER_SECOND
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
        effects?.setNoiseProcessingEnabled(tuning.noiseSuppressionPercent > 0)
        audioTrack?.let { applyTrackOutputVolume(it, tuning) }
        runCatching {
            loudnessEnhancer?.setTargetGain(realtimeEnhancerGain(tuning))
        }.onFailure {
            WjxFileLogger.w(TAG, "hailing enhancer update failed: ${it.message}")
        }
    }

    fun muteForRouteRelease() {
        runCatching { queue?.clear() }
        runCatching { loudnessEnhancer?.enabled = false }
        runCatching { loudnessEnhancer?.setTargetGain(0) }
        runCatching { audioTrack?.setVolume(0f) }
        runCatching {
            val track = audioTrack ?: return@runCatching
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                val silence = ShortArray(frameSamples * OUTPUT_CHANNELS)
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
        val output = ShortArray(frameSamples * OUTPUT_CHANNELS)
        val silence = ShortArray(frameSamples)
        try {
            applyTrackOutputVolume(track, currentTuning)
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
        val silence = ShortArray((frameSamples * OUTPUT_CHANNELS).coerceAtLeast(DEFAULT_SAMPLE_RATE / 100))
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

    @SuppressLint("MissingPermission")
    private fun createAudioResources(): AudioResources? {
        val inputDevices = preferredInputDevices()
        for (rate in SAMPLE_RATE_CANDIDATES) {
            val frame = rate / FRAMES_PER_SECOND
            val recordMin = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val trackMin = AudioTrack.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (recordMin <= 0 || trackMin <= 0) continue
            val sourceCandidates = audioSourceCandidates()
            for (device in inputDevices) {
                for (source in sourceCandidates) {
                    val recordBufferBytes = max(recordMin, frame * BYTES_PER_SAMPLE * 4)
                    val trackBufferBytes = max(trackMin, frame * OUTPUT_CHANNELS * BYTES_PER_SAMPLE * 4)
                    val record = runCatching {
                        AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufferBytes)
                    }.getOrNull() ?: continue
                    if (record.state != AudioRecord.STATE_INITIALIZED) {
                        record.release()
                        continue
                    }
                    val preferredOk = device?.let { preferred ->
                        runCatching { record.setPreferredDevice(preferred) }.getOrDefault(false)
                    } ?: true
                    val track = createSharedSessionTrack(
                        sampleRate = rate,
                        bufferBytes = trackBufferBytes,
                        sessionId = record.audioSessionId,
                    )
                    if (track?.state == AudioTrack.STATE_INITIALIZED) {
                        WjxFileLogger.i(
                            TAG,
                            "audio resources rate=$rate source=$source mic=${device?.logLabel().orEmpty().ifBlank { "default" }} " +
                                "preferred=$preferredOk recordSession=${record.audioSessionId} trackSession=${track.audioSessionId} " +
                                "sessionMatch=${record.audioSessionId == track.audioSessionId} " +
                                "recordBuffer=$recordBufferBytes trackBuffer=$trackBufferBytes",
                        )
                        return AudioResources(rate, record, track)
                    }
                    runCatching { track?.release() }
                    record.release()
                }
            }
        }
        return null
    }

    private fun createSharedSessionTrack(
        sampleRate: Int,
        bufferBytes: Int,
        sessionId: Int,
    ): AudioTrack? {
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val sharedTrack = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(WjxAudioAttributes.forOutside(channel))
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .setSessionId(sessionId)
                .build()
        }.onFailure {
            WjxFileLogger.w(TAG, "shared session AudioTrack build failed session=$sessionId: ${it.message}")
        }.getOrNull()
        if (sharedTrack?.state == AudioTrack.STATE_INITIALIZED && sharedTrack.audioSessionId == sessionId) {
            return sharedTrack
        }
        runCatching { sharedTrack?.release() }
        return runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(WjxAudioAttributes.forOutside(channel))
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()
        }.onFailure {
            WjxFileLogger.w(TAG, "fallback AudioTrack build failed: ${it.message}")
        }.getOrNull()
    }

    private fun audioSourceCandidates(): IntArray =
        if (channel == ExteriorChannel.Channel0) {
            intArrayOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
            )
        } else {
            intArrayOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
            )
        }

    private fun preferredInputDevices(): List<AudioDeviceInfo?> {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return listOf(null)
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        }.getOrDefault(emptyList())
        val preferred = listOfNotNull(
            devices.firstOrNull { it.address == BYD_ECNR_MIC_ADDRESS },
            devices.firstOrNull { it.address.contains("ECNR", ignoreCase = true) },
            devices.firstOrNull { it.address.contains("BUILTIN_MIC", ignoreCase = true) },
            devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC &&
                    !it.address.contains("bottom", ignoreCase = true)
            },
            devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC },
        ).distinctBy { it.id }
        WjxFileLogger.i(
            TAG,
            "input devices=${devices.joinToString { it.logLabel() }} preferred=${preferred.joinToString { it.logLabel() }}",
        )
        return preferred + listOf(null)
    }

    private fun AudioDeviceInfo.logLabel(): String =
        "id=$id type=$type addr=${address.ifBlank { "-" }} product=$productName"

    private fun release() {
        runCatching { queue?.clear() }
        queue = null
        runCatching { loudnessEnhancer?.enabled = false }
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        effects?.release()
        effects = null
        processor = null
        runCatching { audioRecord?.release() }
        runCatching { audioTrack?.release() }
        audioRecord = null
        audioTrack = null
        recordThread = null
        playbackThread = null
    }

    private data class AudioResources(
        val sampleRate: Int,
        val record: AudioRecord,
        val track: AudioTrack,
    )

    private fun createOutputEnhancer(track: AudioTrack, tuning: RealtimeHailingTuning): LoudnessEnhancer? =
        runCatching {
            LoudnessEnhancer(track.audioSessionId).apply {
                setTargetGain(realtimeEnhancerGain(tuning))
                enabled = true
            }
        }.onSuccess {
            WjxFileLogger.i(TAG, "hailing enhancer enabled gain=${realtimeEnhancerGain(tuning)} volume=${tuning.volumePercent}")
        }.onFailure {
            WjxFileLogger.w(TAG, "hailing enhancer failed: ${it.message}")
        }.getOrNull()

    private fun applyTrackOutputVolume(track: AudioTrack, tuning: RealtimeHailingTuning) {
        val trackVolume = if (tuning.volumePercent <= ExteriorHailingSettings.MIN_VOLUME_PERCENT) 0f else AudioTrack.getMaxVolume()
        runCatching { track.setVolume(trackVolume.coerceIn(0f, AudioTrack.getMaxVolume())) }
    }

    private fun realtimeEnhancerGain(tuning: RealtimeHailingTuning): Int =
        (3200 * (tuning.volumePercent.coerceIn(0, ExteriorHailingSettings.MAX_REALTIME_VOLUME_PERCENT) / 100f))
            .toInt()
            .coerceIn(0, 8400)

    private class AudioEffects private constructor(
        private val echoCanceler: AcousticEchoCanceler?,
        private val noiseSuppressor: NoiseSuppressor?,
        private val autoGain: AutomaticGainControl?,
    ) {
        fun setNoiseProcessingEnabled(enabled: Boolean) {
            runCatching { echoCanceler?.enabled = enabled }
            runCatching { noiseSuppressor?.enabled = enabled }
        }

        fun release() {
            runCatching { echoCanceler?.release() }
            runCatching { noiseSuppressor?.release() }
            runCatching { autoGain?.release() }
        }

        companion object {
            fun create(audioSessionId: Int, tuning: RealtimeHailingTuning): AudioEffects {
                val aec = runCatching {
                    if (AcousticEchoCanceler.isAvailable()) {
                        AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = tuning.noiseSuppressionPercent > 0 }
                    } else {
                        null
                    }
                }.getOrNull()
                val ns = runCatching {
                    if (NoiseSuppressor.isAvailable()) {
                        NoiseSuppressor.create(audioSessionId)?.apply { enabled = tuning.noiseSuppressionPercent > 0 }
                    } else {
                        null
                    }
                }.getOrNull()
                val agc = runCatching {
                    if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(audioSessionId)?.apply { enabled = false } else null
                }.getOrNull()
                WjxFileLogger.i(TAG, "effects aec=${aec != null} ns=${ns != null} agcDisabled=${agc != null}")
                return AudioEffects(aec, ns, agc)
            }
        }
    }

    companion object {
        private const val TAG = "WjxRealtimeHailing"
        private const val DEFAULT_SAMPLE_RATE = 16_000
        private const val FRAMES_PER_SECOND = 50
        private const val BYTES_PER_SAMPLE = 2
        private const val OUTPUT_CHANNELS = 1
        private const val QUEUE_SIZE = 3
        private const val QUEUE_TIMEOUT_MS = 20L
        private const val STARTUP_TIMEOUT_MS = 900L
        private const val STARTUP_SILENCE_FRAMES = 6
        private const val BYD_ECNR_MIC_ADDRESS = "COM_BYD_AUDIO_BUILTIN_MIC_ECNR"
        private val SAMPLE_RATE_CANDIDATES = intArrayOf(16_000, 48_000, 44_100)
    }
}
