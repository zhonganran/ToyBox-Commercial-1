package com.byd.wjx.audio

import android.content.Context
import android.media.AudioManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WjxExteriorSpeakerController(private val context: Context) {
    private val savedStreamVolumes = mutableMapOf<Int, Int>()
    private var acquiredCount = 0

    fun acquire(
        channel: ExteriorChannel,
        volumePercent: Int = 100,
        fallbackVolume: Int = CHANNEL0_PLAYBACK_PRESET_VOLUME,
        scaleFromMax: Boolean = false,
    ): Boolean {
        acquiredCount += 1
        return if (channel == ExteriorChannel.Channel0) {
            flushStream(channel.streamType)
            sleepQuietly(80L)
            applyStreamVolume(channel.streamType, volumePercent, fallbackVolume, scaleFromMax)
            true
        } else {
            true
        }
    }

    fun updateVolume(
        channel: ExteriorChannel,
        volumePercent: Int,
        fallbackVolume: Int = CHANNEL0_PLAYBACK_PRESET_VOLUME,
        scaleFromMax: Boolean = false,
    ) {
        if (channel == ExteriorChannel.Channel0) {
            applyStreamVolume(
                streamType = channel.streamType,
                volumePercent = volumePercent,
                fallbackVolume = fallbackVolume,
                scaleFromMax = scaleFromMax,
            )
        }
    }

    fun reinforce(
        channel: ExteriorChannel,
        volumePercent: Int = 100,
        fallbackVolume: Int = CHANNEL0_PLAYBACK_PRESET_VOLUME,
        scaleFromMax: Boolean = false,
    ): Boolean {
        if (channel == ExteriorChannel.Channel0) {
            applyStreamVolume(
                streamType = channel.streamType,
                volumePercent = volumePercent,
                fallbackVolume = fallbackVolume,
                scaleFromMax = scaleFromMax,
            )
        }
        return true
    }

    fun release(channel: ExteriorChannel) {
        acquiredCount = max(0, acquiredCount - 1)
        if (acquiredCount > 0) return
        if (channel == ExteriorChannel.Channel0) {
            flushStream(channel.streamType)
            sleepQuietly(120L)
            restoreStream(channel.streamType)
        }
    }

    private fun flushStream(streamType: Int) {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (!savedStreamVolumes.containsKey(streamType)) {
                savedStreamVolumes[streamType] = audioManager.getStreamVolume(streamType)
            }
            invokeSetStreamMute(audioManager, streamType, true)
            val max = audioManager.getStreamMaxVolume(streamType)
            if (max > 0) {
                audioManager.setStreamVolume(streamType, 0, 0)
            }
        }
    }

    private fun restoreStream(streamType: Int) {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            invokeSetStreamMute(audioManager, streamType, false)
            val saved = savedStreamVolumes.remove(streamType)
            val max = audioManager.getStreamMaxVolume(streamType)
            if (saved != null && max > 0) {
                audioManager.setStreamVolume(streamType, min(max, max(0, saved)), 0)
            }
        }
    }

    private fun applyStreamVolume(
        streamType: Int,
        volumePercent: Int,
        fallbackVolume: Int,
        scaleFromMax: Boolean,
    ) {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            invokeSetStreamMute(audioManager, streamType, false)
            val max = audioManager.getStreamMaxVolume(streamType)
            if (max > 0) {
                if (!savedStreamVolumes.containsKey(streamType)) {
                    savedStreamVolumes[streamType] = audioManager.getStreamVolume(streamType)
                }
                val volume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
                val target = if (volume <= 0) {
                    0
                } else if (scaleFromMax) {
                    (max * (volume / 100f)).roundToInt().coerceIn(1, max)
                } else {
                    val saved = savedStreamVolumes[streamType] ?: audioManager.getStreamVolume(streamType)
                    val fallback = fallbackVolume.coerceAtLeast(1)
                    val base = if (saved in 1 until max) saved else fallback.coerceIn(1, max)
                    (base * (volume / 100f)).roundToInt().coerceIn(1, max)
                }
                audioManager.setStreamVolume(streamType, target, 0)
            }
        }
    }

    private fun invokeSetStreamMute(audioManager: AudioManager, streamType: Int, enabled: Boolean) {
        runCatching {
            val method = audioManager.javaClass.getDeclaredMethod(
                "setStreamMute",
                Integer.TYPE,
                java.lang.Boolean.TYPE
            )
            method.isAccessible = true
            method.invoke(audioManager, streamType, enabled)
        }
    }

    private fun sleepQuietly(ms: Long) {
        runCatching { Thread.sleep(ms) }
    }

    companion object {
        const val CHANNEL0_PLAYBACK_PRESET_VOLUME = 1
        const val CHANNEL0_TALK_PRESET_VOLUME = 3
        private const val MIN_VOLUME_PERCENT = 0
        private const val MAX_VOLUME_PERCENT = 200
    }
}
