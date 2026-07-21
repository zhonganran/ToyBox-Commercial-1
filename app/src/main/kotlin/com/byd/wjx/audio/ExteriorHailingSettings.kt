package com.byd.wjx.audio

import android.content.Context
import com.byd.wjx.vehicle.CarProfile
import kotlin.math.roundToInt

class ExteriorHailingSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs
        get() = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var channelMode: ExteriorChannelMode
        get() = runCatching {
            ExteriorChannelMode.valueOf(
                prefs.getString(KEY_AUDIO_CHANNEL_MODE, ExteriorChannelMode.AUTO.name).orEmpty()
            )
        }.getOrDefault(ExteriorChannelMode.AUTO)
        set(value) {
            prefs.edit().putString(KEY_AUDIO_CHANNEL_MODE, value.name).apply()
        }

    var playbackVolumePercent: Int
        get() = readTuningInt(
            key = KEY_PLAYBACK_VOLUME_PERCENT,
            defaultValue = DEFAULT_PLAYBACK_VOLUME_PERCENT,
            minValue = MIN_VOLUME_PERCENT,
            maxValue = MAX_PLAYBACK_VOLUME_PERCENT,
        )
        set(value) {
            writeTuningInt(
                key = KEY_PLAYBACK_VOLUME_PERCENT,
                value = value,
                minValue = MIN_VOLUME_PERCENT,
                maxValue = MAX_PLAYBACK_VOLUME_PERCENT,
            )
        }

    var realtimeVolumePercent: Int
        get() = readTuningInt(
            key = KEY_REALTIME_VOLUME_PERCENT,
            defaultValue = DEFAULT_REALTIME_VOLUME_PERCENT,
            minValue = MIN_VOLUME_PERCENT,
            maxValue = MAX_REALTIME_VOLUME_PERCENT,
        )
        set(value) {
            writeTuningInt(
                key = KEY_REALTIME_VOLUME_PERCENT,
                value = value,
                minValue = MIN_VOLUME_PERCENT,
                maxValue = MAX_REALTIME_VOLUME_PERCENT,
            )
        }

    val realtimeAutoMicSensitivityPercent: Int
        get() = calibratedMicSensitivityPercent(
            volumePercent = realtimeVolumePercent,
            noiseSuppressionPercent = realtimeNoiseSuppressionPercent,
        )

    var realtimeNoiseSuppressionPercent: Int
        get() = readTuningInt(
            key = KEY_REALTIME_NOISE_SUPPRESSION_PERCENT,
            defaultValue = DEFAULT_REALTIME_NOISE_SUPPRESSION_PERCENT,
            minValue = MIN_NOISE_SUPPRESSION_PERCENT,
            maxValue = MAX_NOISE_SUPPRESSION_PERCENT,
        )
        set(value) {
            writeTuningInt(
                key = KEY_REALTIME_NOISE_SUPPRESSION_PERCENT,
                value = value,
                minValue = MIN_NOISE_SUPPRESSION_PERCENT,
                maxValue = MAX_NOISE_SUPPRESSION_PERCENT,
            )
        }

    fun effectiveChannel(profile: CarProfile): ExteriorChannel =
        when (profile.series) {
            23, 34, 57 -> profile.channel
            else -> when (channelMode) {
                ExteriorChannelMode.AUTO -> profile.channel
                ExteriorChannelMode.CHANNEL_0 -> ExteriorChannel.Channel1
                ExteriorChannelMode.CHANNEL_1 -> ExteriorChannel.Channel1
            }
        }

    fun channelModeLabel(profile: CarProfile): String =
        when (profile.series) {
            23, 34, 57 -> "自动：${profile.seriesLabel} -> ${profile.channel.shortLabel}"
            else -> when (channelMode) {
                ExteriorChannelMode.AUTO -> "自动：${profile.seriesLabel} -> ${profile.channel.shortLabel}"
                ExteriorChannelMode.CHANNEL_0 -> "通道 A 仅 23 控可用，已保护为 ${ExteriorChannel.Channel1.shortLabel}"
                ExteriorChannelMode.CHANNEL_1 -> "手动：${ExteriorChannel.Channel1.shortLabel}"
            }
        }

    fun realtimeTuning(): RealtimeHailingTuning =
        RealtimeHailingTuning(
            volumePercent = realtimeVolumePercent,
            micGainPercent = realtimeAutoMicSensitivityPercent,
            noiseSuppressionPercent = realtimeNoiseSuppressionPercent,
        )

    fun resetAudioTuning() {
        prefs.edit()
            .putInt(KEY_PLAYBACK_VOLUME_PERCENT, DEFAULT_PLAYBACK_VOLUME_PERCENT)
            .putInt(KEY_REALTIME_VOLUME_PERCENT, DEFAULT_REALTIME_VOLUME_PERCENT)
            .putInt(KEY_REALTIME_NOISE_SUPPRESSION_PERCENT, DEFAULT_REALTIME_NOISE_SUPPRESSION_PERCENT)
            .apply()
    }

    private fun readTuningInt(
        key: String,
        defaultValue: Int,
        minValue: Int,
        maxValue: Int,
    ): Int = prefs.getInt(key, defaultValue).coerceIn(minValue, maxValue)

    private fun writeTuningInt(
        key: String,
        value: Int,
        minValue: Int,
        maxValue: Int,
    ) {
        val clamped = value.coerceIn(minValue, maxValue)
        prefs.edit().putInt(key, clamped).apply()
    }

    private fun calibratedMicSensitivityPercent(
        volumePercent: Int,
        noiseSuppressionPercent: Int,
    ): Int {
        val volume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_REALTIME_VOLUME_PERCENT) / 100f
        val noise = noiseSuppressionPercent.coerceIn(
            MIN_NOISE_SUPPRESSION_PERCENT,
            MAX_NOISE_SUPPRESSION_PERCENT,
        ) / 100f
        return (volume * (2.5f - noise * 1.5f) * 100f)
            .roundToInt()
            .coerceIn(MIN_MIC_GAIN_PERCENT, MAX_MIC_GAIN_PERCENT)
    }

    companion object {
        private const val PREF_NAME = "exterior_hailing"
        private const val KEY_AUDIO_CHANNEL_MODE = "audio_channel_mode"
        private const val KEY_PLAYBACK_VOLUME_PERCENT = "playback_volume_pct"
        private const val KEY_REALTIME_VOLUME_PERCENT = "mic_volume_pct"
        private const val KEY_REALTIME_NOISE_SUPPRESSION_PERCENT = "denoise_level"

        const val DEFAULT_PLAYBACK_VOLUME_PERCENT = 65
        const val DEFAULT_REALTIME_VOLUME_PERCENT = 60
        const val DEFAULT_REALTIME_MIC_GAIN_PERCENT = 85
        const val DEFAULT_REALTIME_NOISE_SUPPRESSION_PERCENT = 88
        const val MIN_VOLUME_PERCENT = 0
        const val MAX_PLAYBACK_VOLUME_PERCENT = 200
        const val MAX_REALTIME_VOLUME_PERCENT = 200
        const val MIN_MIC_GAIN_PERCENT = 0
        const val MAX_MIC_GAIN_PERCENT = 500
        const val MIN_NOISE_SUPPRESSION_PERCENT = 0
        const val MAX_NOISE_SUPPRESSION_PERCENT = 100
    }
}

data class RealtimeHailingTuning(
    val volumePercent: Int = ExteriorHailingSettings.DEFAULT_REALTIME_VOLUME_PERCENT,
    val micGainPercent: Int = ExteriorHailingSettings.DEFAULT_REALTIME_MIC_GAIN_PERCENT,
    val noiseSuppressionPercent: Int = ExteriorHailingSettings.DEFAULT_REALTIME_NOISE_SUPPRESSION_PERCENT,
) {
    val volumeMultiplier: Float
        get() = volumePercent.coerceIn(
            ExteriorHailingSettings.MIN_VOLUME_PERCENT,
            ExteriorHailingSettings.MAX_REALTIME_VOLUME_PERCENT,
        ) / 100f

    val micGainMultiplier: Float
        get() = micGainPercent.coerceIn(
            ExteriorHailingSettings.MIN_MIC_GAIN_PERCENT,
            ExteriorHailingSettings.MAX_MIC_GAIN_PERCENT,
        ) / 100f

    val noiseSuppression: Float
        get() = noiseSuppressionPercent.coerceIn(
            ExteriorHailingSettings.MIN_NOISE_SUPPRESSION_PERCENT,
            ExteriorHailingSettings.MAX_NOISE_SUPPRESSION_PERCENT,
        ) / 100f
}

enum class ExteriorChannelMode {
    AUTO,
    CHANNEL_0,
    CHANNEL_1,
}
