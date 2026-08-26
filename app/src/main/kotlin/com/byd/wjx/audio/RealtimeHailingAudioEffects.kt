package com.byd.wjx.audio

import android.media.AudioTrack
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import com.byd.wjx.log.WjxFileLogger

internal class RealtimeHailingAudioEffects private constructor(
    private val echoCanceler: AcousticEchoCanceler?,
    private val noiseSuppressor: NoiseSuppressor?,
    private val autoGain: AutomaticGainControl?,
    private val loudnessEnhancer: LoudnessEnhancer?,
) {
    fun update(track: AudioTrack?, tuning: RealtimeHailingTuning) {
        val enabled = tuning.noiseSuppressionPercent > 0
        runCatching { echoCanceler?.enabled = enabled }
        runCatching { noiseSuppressor?.enabled = enabled }
        track?.let { applyRealtimeHailingTrackOutputVolume(it, tuning) }
        runCatching {
            loudnessEnhancer?.setTargetGain(realtimeHailingEnhancerGain(tuning))
        }.onFailure {
            WjxFileLogger.w(TAG, "hailing enhancer update failed: ${it.message}")
        }
    }

    fun mute(track: AudioTrack?) {
        runCatching { loudnessEnhancer?.enabled = false }
        runCatching { loudnessEnhancer?.setTargetGain(0) }
        runCatching { track?.setVolume(0f) }
    }

    fun release() {
        runCatching { loudnessEnhancer?.enabled = false }
        runCatching { loudnessEnhancer?.release() }
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { autoGain?.release() }
    }

    companion object {
        private const val TAG = "WjxRealtimeHailing"

        fun create(
            recordSessionId: Int,
            track: AudioTrack,
            tuning: RealtimeHailingTuning,
        ): RealtimeHailingAudioEffects {
            val aec = runCatching {
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler.create(recordSessionId)?.apply {
                        enabled = tuning.noiseSuppressionPercent > 0
                    }
                } else {
                    null
                }
            }.getOrNull()
            val noiseSuppressor = runCatching {
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(recordSessionId)?.apply {
                        enabled = tuning.noiseSuppressionPercent > 0
                    }
                } else {
                    null
                }
            }.getOrNull()
            val autoGain = runCatching {
                if (AutomaticGainControl.isAvailable()) {
                    AutomaticGainControl.create(recordSessionId)?.apply { enabled = false }
                } else {
                    null
                }
            }.getOrNull()
            val enhancer = runCatching {
                LoudnessEnhancer(track.audioSessionId).apply {
                    setTargetGain(realtimeHailingEnhancerGain(tuning))
                    enabled = true
                }
            }.onSuccess {
                WjxFileLogger.i(
                    TAG,
                    "hailing enhancer enabled gain=${realtimeHailingEnhancerGain(tuning)} " +
                        "volume=${tuning.volumePercent}",
                )
            }.onFailure {
                WjxFileLogger.w(TAG, "hailing enhancer failed: ${it.message}")
            }.getOrNull()
            applyRealtimeHailingTrackOutputVolume(track, tuning)
            WjxFileLogger.i(
                TAG,
                "effects aec=${aec != null} ns=${noiseSuppressor != null} " +
                    "agcDisabled=${autoGain != null}",
            )
            return RealtimeHailingAudioEffects(aec, noiseSuppressor, autoGain, enhancer)
        }
    }
}

internal fun realtimeHailingEnhancerGain(tuning: RealtimeHailingTuning): Int {
    val volumeScale =
        tuning.volumePercent.coerceIn(
            0,
            ExteriorHailingSettings.MAX_REALTIME_VOLUME_PERCENT,
        ) / 100f
    return (BASE_ENHANCER_GAIN_MB * volumeScale)
        .toInt()
        .coerceIn(0, 8400)
}

private const val BASE_ENHANCER_GAIN_MB = 3200

internal fun applyRealtimeHailingTrackOutputVolume(
    track: AudioTrack,
    tuning: RealtimeHailingTuning,
) {
    val trackVolume = if (tuning.volumePercent <= ExteriorHailingSettings.MIN_VOLUME_PERCENT) {
        0f
    } else {
        AudioTrack.getMaxVolume()
    }
    runCatching { track.setVolume(trackVolume.coerceIn(0f, AudioTrack.getMaxVolume())) }
}
