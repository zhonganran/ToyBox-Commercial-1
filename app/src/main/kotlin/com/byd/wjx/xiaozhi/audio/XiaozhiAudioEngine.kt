package com.byd.wjx.xiaozhi.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import androidx.core.content.ContextCompat
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class XiaozhiAudioEngine @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val codec = XiaozhiOpusCodec()
    private val playbackQueue = ArrayBlockingQueue<ByteArray>(PLAYBACK_QUEUE_SIZE)

    @Volatile private var recording = false
    @Volatile private var playing = false
    @Volatile private var playbackFinishing = false
    @Volatile private var playbackFinishCallback: (() -> Unit)? = null
    @Volatile private var focusHeld = false

    private var record: AudioRecord? = null
    private var recordThread: Thread? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var track: AudioTrack? = null
    private var playbackThread: Thread? = null
    private var outputSampleRate: Int = 24_000
    private var outputFrameDurationMs: Int = 60
    private var focusRequest: AudioFocusRequest? = null
    private var focusLossCallback: (() -> Unit)? = null

    fun acquireFocus(onFocusLost: () -> Unit): Result<Unit> = runCatching {
        focusLossCallback = onFocusLost
        if (focusHeld) return@runCatching
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(insideAudioAttributes())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_GAIN) return@setOnAudioFocusChangeListener
                if (focusHeld) focusLossCallback?.invoke()
            }
            .setAcceptsDelayedFocusGain(false)
            .build()
        val result = audioManager?.requestAudioFocus(request)
        check(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "无法取得车内音频焦点" }
        focusRequest = request
        focusHeld = true
    }

    fun abandonFocus() {
        focusHeld = false
        focusRequest?.let { request -> runCatching { audioManager?.abandonAudioFocusRequest(request) } }
        focusRequest = null
        focusLossCallback = null
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        onPacket: (ByteArray) -> Unit,
        onError: (String) -> Unit,
    ): Result<Unit> = runCatching {
        if (recording) return@runCatching
        check(
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        ) { "没有麦克风权限" }
        codec.openEncoder()
        val nextRecord = createAudioRecord() ?: error("无法初始化车内麦克风")
        record = nextRecord
        echoCanceler = runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(nextRecord.audioSessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()
        noiseSuppressor = runCatching {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(nextRecord.audioSessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()
        recording = true
        recordThread = Thread(
            { recordLoop(nextRecord, onPacket, onError) },
            "WJX-XiaozhiRecord",
        ).also { it.start() }
    }

    fun stopRecording() {
        recording = false
        runCatching { record?.stop() }
        runCatching { recordThread?.interrupt() }
        runCatching { recordThread?.join(450L) }
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { record?.release() }
        echoCanceler = null
        noiseSuppressor = null
        record = null
        recordThread = null
        codec.closeEncoder()
    }

    fun configurePlayback(sampleRate: Int, frameDurationMs: Int): Result<Unit> = runCatching {
        stopPlayback()
        outputSampleRate = normalizeOutputRate(sampleRate)
        outputFrameDurationMs = frameDurationMs.coerceIn(10, 120)
        codec.openDecoder(outputSampleRate)
        val minBuffer = AudioTrack.getMinBufferSize(
            outputSampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "车内语音播放缓冲区不可用：$minBuffer" }
        val frameBytes = outputSampleRate * outputFrameDurationMs / 1_000 * 2
        val nextTrack = AudioTrack.Builder()
            .setAudioAttributes(insideAudioAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(outputSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBuffer, frameBytes * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(nextTrack.state == AudioTrack.STATE_INITIALIZED) { "车内语音播放器初始化失败" }
        track = nextTrack
        playbackQueue.clear()
        playbackFinishing = false
        playing = true
        playbackThread = Thread(
            { playbackLoop(nextTrack) },
            "WJX-XiaozhiPlayback",
        ).also { it.start() }
        WjxFileLogger.i(TAG, "playback ready rate=$outputSampleRate frame=${outputFrameDurationMs}ms")
    }

    fun enqueuePlayback(packet: ByteArray) {
        if (!playing || packet.isEmpty()) return
        if (!playbackQueue.offer(packet.copyOf())) {
            playbackQueue.poll()
            playbackQueue.offer(packet.copyOf())
            WjxFileLogger.w(TAG, "playback queue full; dropped oldest packet")
        }
    }

    fun finishPlayback(onFinished: () -> Unit) {
        if (!playing) {
            onFinished()
            return
        }
        playbackFinishCallback = onFinished
        playbackFinishing = true
    }

    fun stopPlayback() {
        playing = false
        playbackFinishing = false
        playbackFinishCallback = null
        playbackQueue.clear()
        runCatching { playbackThread?.interrupt() }
        if (Thread.currentThread() !== playbackThread) {
            runCatching { playbackThread?.join(500L) }
        }
        releasePlaybackResources()
    }

    fun stopAll() {
        stopRecording()
        stopPlayback()
        codec.close()
        abandonFocus()
    }

    private fun recordLoop(
        activeRecord: AudioRecord,
        onPacket: (ByteArray) -> Unit,
        onError: (String) -> Unit,
    ) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val samples = ShortArray(XiaozhiOpusCodec.INPUT_FRAME_SAMPLES)
        try {
            activeRecord.startRecording()
            check(activeRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风没有进入录音状态" }
            while (recording && !Thread.currentThread().isInterrupted) {
                var filled = 0
                while (filled < samples.size && recording && !Thread.currentThread().isInterrupted) {
                    val count = activeRecord.read(
                        samples,
                        filled,
                        samples.size - filled,
                        AudioRecord.READ_BLOCKING,
                    )
                    if (count < 0) error("麦克风读取失败：$count")
                    if (count == 0) continue
                    filled += count
                }
                if (filled == samples.size && recording) {
                    onPacket(codec.encode(shortsToLittleEndian(samples)))
                }
            }
        } catch (error: Throwable) {
            if (recording) onError(error.message ?: "小智录音异常")
        } finally {
            runCatching {
                if (activeRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) activeRecord.stop()
            }
        }
    }

    private fun playbackLoop(activeTrack: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        try {
            activeTrack.play()
            check(activeTrack.playState == AudioTrack.PLAYSTATE_PLAYING) { "车内语音播放没有启动" }
            while (playing && !Thread.currentThread().isInterrupted) {
                val packet = playbackQueue.poll(120L, TimeUnit.MILLISECONDS)
                if (packet == null) {
                    if (playbackFinishing) {
                        Thread.sleep((outputFrameDurationMs + 70).toLong())
                        break
                    }
                    continue
                }
                val pcm = codec.decode(packet, outputFrameDurationMs)
                var offset = 0
                while (offset < pcm.size && playing) {
                    val written = activeTrack.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                    check(written >= 0) { "车内语音写入失败：$written" }
                    if (written == 0) Thread.yield() else offset += written
                }
            }
        } catch (error: Throwable) {
            if (playing) WjxFileLogger.w(TAG, "playback failed: ${error.message}")
        } finally {
            playing = false
            val callback = playbackFinishCallback
            playbackFinishCallback = null
            playbackFinishing = false
            releasePlaybackResources()
            callback?.invoke()
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            XiaozhiOpusCodec.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        val bufferBytes = max(minBuffer, XiaozhiOpusCodec.INPUT_FRAME_SAMPLES * 2 * 4)
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
        )
        val preferred = preferredInputDevice()
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(
                    source,
                    XiaozhiOpusCodec.INPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                )
            }.getOrNull() ?: continue
            if (candidate.state != AudioRecord.STATE_INITIALIZED) {
                candidate.release()
                continue
            }
            preferred?.let { runCatching { candidate.setPreferredDevice(it) } }
            WjxFileLogger.i(
                TAG,
                "record ready source=$source session=${candidate.audioSessionId} mic=${preferred?.address.orEmpty().ifBlank { "default" }}",
            )
            return candidate
        }
        return null
    }

    private fun preferredInputDevice(): AudioDeviceInfo? {
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty()
        return devices.firstOrNull { it.address == BYD_ECNR_MIC_ADDRESS }
            ?: devices.firstOrNull { it.address.contains("ECNR", ignoreCase = true) }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    }

    private fun releasePlaybackResources() {
        val activeTrack = track
        track = null
        playbackThread = null
        runCatching { if (activeTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) activeTrack.stop() }
        runCatching { activeTrack?.flush() }
        runCatching { activeTrack?.release() }
        codec.closeDecoder()
    }

    private fun insideAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun shortsToLittleEndian(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
        return bytes
    }

    private fun normalizeOutputRate(sampleRate: Int): Int = when {
        sampleRate <= 8_000 -> 8_000
        sampleRate <= 12_000 -> 12_000
        sampleRate <= 16_000 -> 16_000
        sampleRate <= 24_000 -> 24_000
        else -> 48_000
    }

    companion object {
        private const val TAG = "XiaozhiAudio"
        private const val PLAYBACK_QUEUE_SIZE = 48
        private const val BYD_ECNR_MIC_ADDRESS = "COM_BYD_AUDIO_BUILTIN_MIC_ECNR"
    }
}
