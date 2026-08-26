package com.byd.wjx.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.byd.wjx.log.WjxFileLogger
import kotlin.math.max

internal data class RealtimeHailingAudioResources(
    val sampleRate: Int,
    val record: AudioRecord,
    val track: AudioTrack,
)

internal class RealtimeHailingAudioResourceFactory(
    private val context: Context,
    private val channel: ExteriorChannel,
) {
    @SuppressLint("MissingPermission")
    fun create(): RealtimeHailingAudioResources? {
        val inputDevices = preferredInputDevices()
        for (rate in SAMPLE_RATE_CANDIDATES) {
            val frame = RealtimeHailingAudioSpec.frameSamples(rate)
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
                    val recordBufferBytes = max(
                        recordMin,
                        frame * RealtimeHailingAudioSpec.BYTES_PER_SAMPLE * 4,
                    )
                    val trackBufferBytes = max(
                        trackMin,
                        frame * RealtimeHailingAudioSpec.OUTPUT_CHANNELS *
                            RealtimeHailingAudioSpec.BYTES_PER_SAMPLE * 4,
                    )
                    val record = runCatching {
                        AudioRecord(
                            source,
                            rate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            recordBufferBytes,
                        )
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
                            "audio resources rate=$rate source=$source " +
                                "mic=${device?.logLabel().orEmpty().ifBlank { "default" }} " +
                                "preferred=$preferredOk recordSession=${record.audioSessionId} " +
                                "trackSession=${track.audioSessionId} " +
                                "sessionMatch=${record.audioSessionId == track.audioSessionId} " +
                                "recordBuffer=$recordBufferBytes trackBuffer=$trackBufferBytes",
                        )
                        return RealtimeHailingAudioResources(rate, record, track)
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
            "input devices=${devices.joinToString { it.logLabel() }} " +
                "preferred=${preferred.joinToString { it.logLabel() }}",
        )
        return preferred + listOf(null)
    }

    private fun AudioDeviceInfo.logLabel(): String =
        "id=$id type=$type addr=${address.ifBlank { "-" }} product=$productName"

    private companion object {
        const val TAG = "WjxRealtimeHailing"
        const val BYD_ECNR_MIC_ADDRESS = "COM_BYD_AUDIO_BUILTIN_MIC_ECNR"
        val SAMPLE_RATE_CANDIDATES = intArrayOf(
            RealtimeHailingAudioSpec.DEFAULT_SAMPLE_RATE,
            48_000,
            44_100,
        )
    }
}
