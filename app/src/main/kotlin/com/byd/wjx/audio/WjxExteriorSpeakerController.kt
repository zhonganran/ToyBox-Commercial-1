package com.byd.wjx.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WjxExteriorSpeakerController(private val context: Context) {
    private var naviFocus: AudioFocusRequest? = null
    private val savedStreamVolumes = mutableMapOf<Int, Int>()
    private var acquiredCount = 0
    @Volatile private var naviFocusActive = false
    private val mainHandler = Handler(Looper.getMainLooper())

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
            requestNaviFocus()
        } else {
            applyStreamVolume(channel.streamType, volumePercent, fallbackVolume = -1, scaleFromMax = scaleFromMax)
            true
        }
    }

    fun updateVolume(
        channel: ExteriorChannel,
        volumePercent: Int,
        fallbackVolume: Int = CHANNEL0_PLAYBACK_PRESET_VOLUME,
        scaleFromMax: Boolean = false,
    ) {
        applyStreamVolume(
            streamType = channel.streamType,
            volumePercent = volumePercent,
            fallbackVolume = if (channel == ExteriorChannel.Channel0) fallbackVolume else -1,
            scaleFromMax = scaleFromMax,
        )
    }

    fun release(channel: ExteriorChannel) {
        acquiredCount = max(0, acquiredCount - 1)
        if (acquiredCount > 0) return
        if (channel == ExteriorChannel.Channel0) {
            flushStream(channel.streamType)
            sleepQuietly(120L)
            abandonNaviFocus()
            sleepQuietly(300L)
            sleepQuietly(500L)
            restoreStream(channel.streamType)
        } else {
            restoreStream(channel.streamType)
        }
    }

    private fun requestNaviFocus(): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        naviFocusActive = true
        runCatching {
            audioManager.javaClass.methods.firstOrNull {
                it.name.contains("requestAudioNaviFocus", ignoreCase = true) && it.parameterTypes.isEmpty()
            }?.let {
                it.isAccessible = true
                it.invoke(audioManager)
            }
        }
        return if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setLegacyStreamType(ExteriorChannel.Channel0.streamType)
                        .build()
                )
                .setOnAudioFocusChangeListener(naviFocusListener)
                .build()
            val ok = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (ok) naviFocus = request
            ok
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                ExteriorChannel.Channel0.streamType,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonNaviFocus() {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        naviFocusActive = false
        mainHandler.removeCallbacksAndMessages(null)
        runCatching {
            audioManager.javaClass.methods.firstOrNull {
                it.name.contains("abandonAudioNaviFocus", ignoreCase = true) && it.parameterTypes.isEmpty()
            }?.let {
                it.isAccessible = true
                it.invoke(audioManager)
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                naviFocus?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        }
        naviFocus = null
    }

    private val naviFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (naviFocusActive && (
            change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            )
        ) {
            mainHandler.post { requestNaviFocus() }
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
                    val fallback = if (fallbackVolume > 0) fallbackVolume else (max * CHANNEL1_FALLBACK_VOLUME_RATIO).roundToInt()
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
        private const val CHANNEL1_FALLBACK_VOLUME_RATIO = 0.55f
        private const val MIN_VOLUME_PERCENT = 0
        private const val MAX_VOLUME_PERCENT = 200
    }
}
