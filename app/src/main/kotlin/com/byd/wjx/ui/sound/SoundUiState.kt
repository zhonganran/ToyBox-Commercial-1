package com.byd.wjx.ui.sound

import com.byd.wjx.audio.ExteriorChannelMode
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.storage.SoundLibrary

data class SoundUiState(
    val sounds: List<SoundLibrary.SoundItem> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val playingPath: String? = null,
    val controllerLabel: String = "未知",
    val channelLabel: String = "自动识别中",
    val channelMode: ExteriorChannelMode = ExteriorChannelMode.AUTO,
    val realtimeHailingRunning: Boolean = false,
    val realtimeHailingBusy: Boolean = false,
    val audioTuningPanelVisible: Boolean = false,
    val playbackVolumePercent: Int = ExteriorHailingSettings.DEFAULT_PLAYBACK_VOLUME_PERCENT,
    val realtimeVolumePercent: Int = ExteriorHailingSettings.DEFAULT_REALTIME_VOLUME_PERCENT,
    val realtimeMicGainPercent: Int = ExteriorHailingSettings.DEFAULT_REALTIME_MIC_GAIN_PERCENT,
    val realtimeNoiseSuppressionPercent: Int = ExteriorHailingSettings.DEFAULT_REALTIME_NOISE_SUPPRESSION_PERCENT,
)
