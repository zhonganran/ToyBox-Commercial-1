package com.byd.wjx.audio

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import com.byd.wjx.log.WjxFileLogger
import java.io.File
import kotlinx.coroutines.delay

/** Owns MediaPlayer and LoudnessEnhancer as one release-safe playback resource. */
internal class OutsidePlaybackPlayer {
    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null

    val active: Boolean
        get() = player != null

    val audioSessionId: Int?
        get() = player?.audioSessionId

    fun start(
        file: File,
        channel: ExteriorChannel,
        loop: Boolean,
        volumePercent: Int,
        onCompletion: () -> Unit,
        onError: (Int, Int) -> Unit,
    ): Boolean = runCatching {
        clear()
        player = MediaPlayer().apply {
            setAudioAttributes(WjxAudioAttributes.forOutside(channel))
            setDataSource(file.absolutePath)
            setVolumePercent(volumePercent)
            isLooping = loop
            setOnCompletionListener { onCompletion() }
            setOnErrorListener { _, what, extra ->
                onError(what, extra)
                true
            }
            prepare()
            attachEnhancer(this, channel, volumePercent)
            start()
        }
        true
    }.getOrElse { error ->
        WjxFileLogger.w(TAG, "playback start failed: ${error.message}")
        clear()
        false
    }

    fun updateVolume(channel: ExteriorChannel, volumePercent: Int) {
        player?.setVolumePercent(volumePercent)
        runCatching {
            enhancer?.setTargetGain(outsidePlaybackEnhancerGain(channel, volumePercent))
        }
    }

    fun mute() {
        runCatching { player?.setVolume(0f, 0f) }
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.setTargetGain(0) }
    }

    suspend fun stopBeforeRouteRelease(channel: ExteriorChannel) {
        WjxFileLogger.i(TAG, "mute, drain and release player before route release channel=$channel")
        mute()
        delay(PLAYER_MUTE_DRAIN_MS)
        clear()
        delay(PLAYER_RELEASE_DRAIN_MS)
    }

    fun clear() {
        runCatching { player?.setOnCompletionListener(null) }
        runCatching { player?.setOnErrorListener(null) }
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.release() }
        runCatching { player?.pause() }
        runCatching { player?.stop() }
        runCatching { player?.release() }
        enhancer = null
        player = null
    }

    private fun MediaPlayer.setVolumePercent(volumePercent: Int) {
        val volume = (volumePercent / 100f).coerceIn(0f, 1f)
        setVolume(volume, volume)
    }

    private fun attachEnhancer(
        mediaPlayer: MediaPlayer,
        channel: ExteriorChannel,
        volumePercent: Int,
    ) {
        runCatching {
            val gain = outsidePlaybackEnhancerGain(channel, volumePercent)
            enhancer = LoudnessEnhancer(mediaPlayer.audioSessionId).apply {
                setTargetGain(gain)
                enabled = true
            }
            WjxFileLogger.i(
                TAG,
                "LoudnessEnhancer enabled gain=$gain volume=$volumePercent channel=$channel",
            )
        }.onFailure { error ->
            WjxFileLogger.w(TAG, "LoudnessEnhancer failed: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "WjxOutsidePlay"
        const val PLAYER_MUTE_DRAIN_MS = 180L
        const val PLAYER_RELEASE_DRAIN_MS = 180L
    }
}

internal fun outsidePlaybackEnhancerGain(
    channel: ExteriorChannel,
    volumePercent: Int,
): Int {
    val baseGain = if (channel == ExteriorChannel.Channel0) 8_400 else 3_200
    return (
        baseGain *
            (volumePercent.coerceIn(0, ExteriorHailingSettings.MAX_PLAYBACK_VOLUME_PERCENT) / 100f)
        ).toInt().coerceIn(0, 10_000)
}
