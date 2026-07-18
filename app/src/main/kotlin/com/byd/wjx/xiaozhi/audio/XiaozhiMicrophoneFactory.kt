package com.byd.wjx.xiaozhi.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Selects the same noise-reduced cabin microphone for wake-word and conversation capture. */
@Singleton
class XiaozhiMicrophoneFactory @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    @SuppressLint("MissingPermission")
    fun create(
        sampleRate: Int,
        frameSamples: Int,
        purpose: String,
    ): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        val bufferBytes = max(minBuffer, frameSamples * Short.SIZE_BYTES * BUFFERED_FRAME_COUNT)
        val preferred = preferredInputDevice()
        for (source in AUDIO_SOURCES) {
            val candidate = runCatching {
                AudioRecord(
                    source,
                    sampleRate,
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
                "record ready purpose=$purpose source=$source rate=$sampleRate " +
                    "session=${candidate.audioSessionId} mic=${preferred?.address.orEmpty().ifBlank { "default" }}",
            )
            return candidate
        }
        WjxFileLogger.w(TAG, "no usable microphone purpose=$purpose rate=$sampleRate")
        return null
    }

    private fun preferredInputDevice(): AudioDeviceInfo? {
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty()
        return devices.firstOrNull { it.address == BYD_ECNR_MIC_ADDRESS }
            ?: devices.firstOrNull { it.address.contains("ECNR", ignoreCase = true) }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    }

    private companion object {
        const val TAG = "XiaozhiMicrophone"
        const val BUFFERED_FRAME_COUNT = 4
        const val BYD_ECNR_MIC_ADDRESS = "COM_BYD_AUDIO_BUILTIN_MIC_ECNR"
        val AUDIO_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
        )
    }
}
