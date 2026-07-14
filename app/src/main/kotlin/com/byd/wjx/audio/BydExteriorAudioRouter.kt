package com.byd.wjx.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class BydExteriorAudioRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) {
    private val routeMutex = Mutex()
    private val speakerController = WjxExteriorSpeakerController(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var playFocus: AudioFocusRequest? = null
    private var pauseFocus: AudioFocusRequest? = null

    suspend fun arm(
        channel: ExteriorChannel,
        usage: Usage = Usage.Playback,
        volumePercent: Int = 100,
    ): Boolean =
        routeMutex.withLock {
            val helperReady = withTimeoutOrNull(HELPER_READY_TIMEOUT_MS) {
                helperBootstrap.ensureCurrent()
            } ?: false
            if (!helperReady) {
                WjxFileLogger.w(TAG, "helper not ready, outside route blocked")
                return@withLock false
            }
            if (usage != Usage.AutoSound) {
                pauseActiveMedia()
            }
            val speakerOk = speakerController.acquire(
                channel,
                volumePercent = volumePercent,
                fallbackVolume = fallbackStreamVolume(usage),
                scaleFromMax = usage != Usage.AutoSound,
            )
            val fidOk = when (channel) {
                ExteriorChannel.Channel1 -> writeAny(
                    Triple(DEV_AUDIO, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET, LOCK_CAR_SOUND_PLAYBACK_ON),
                    Triple(DEV_AUDIO_COMPAT, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET_COMPAT, 1),
                    Triple(DEV_AUDIO, EXTERIOR_SWITCH_SET, SWITCH_ON),
                    Triple(DEV_AUDIO, AVAS_ROUTE_SET, AVAS_ROUTE_ON),
                    Triple(DEV_AUDIO, AUDIO_KARAOKE_MODE_SET, KARAOKE_MODE_EXTERIOR),
                )
                ExteriorChannel.Channel0 -> writeAny(
                    Triple(DEV_ADAS, ADAS_UE_BROADCAST_POSITION_SET, ADAS_BROADCAST_POSITION_ON),
                    Triple(DEV_ADAS_COMPAT, ADAS_UE_BROADCAST_POSITION_SET_COMPAT, 1),
                )
            }
            val lockStateOk = if (channel == ExteriorChannel.Channel1) setCarLockSoundPlayState(true) else false
            val focusOk = requestPlaybackFocus(channel)
            WjxFileLogger.i(TAG, "arm channel=$channel usage=$usage volume=$volumePercent routed=$fidOk speaker=$speakerOk fid=$fidOk lock=$lockStateOk focus=$focusOk")
            fidOk
        }

    suspend fun updateVolume(
        channel: ExteriorChannel,
        usage: Usage = Usage.Playback,
        volumePercent: Int = 100,
    ) {
        routeMutex.withLock {
            speakerController.updateVolume(
                channel = channel,
                volumePercent = volumePercent,
                fallbackVolume = fallbackStreamVolume(usage),
                scaleFromMax = usage != Usage.AutoSound,
            )
            WjxFileLogger.i(TAG, "updateVolume channel=$channel usage=$usage volume=$volumePercent")
        }
    }

    suspend fun disarm(channel: ExteriorChannel) {
        routeMutex.withLock {
            runCatching {
                when (channel) {
                    ExteriorChannel.Channel1 -> {
                        writeAny(
                            Triple(DEV_AUDIO, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET, LOCK_CAR_SOUND_PLAYBACK_OFF),
                            Triple(DEV_AUDIO_COMPAT, LOCK_CAR_SOUND_PLAYBACK_STATUS_SET_COMPAT, 0),
                            Triple(DEV_AUDIO, EXTERIOR_SWITCH_SET, SWITCH_OFF),
                            Triple(DEV_AUDIO, AVAS_ROUTE_SET, AVAS_ROUTE_OFF),
                        )
                        setCarLockSoundPlayState(false)
                    }
                    ExteriorChannel.Channel0 -> writeAny(
                        Triple(DEV_ADAS, ADAS_UE_BROADCAST_POSITION_SET, ADAS_BROADCAST_POSITION_OFF),
                        Triple(DEV_ADAS_COMPAT, ADAS_UE_BROADCAST_POSITION_SET_COMPAT, 0),
                    )
                }
                abandonPlaybackFocus()
                speakerController.release(channel)
                abandonPausedMediaFocus()
            }.onFailure {
                WjxFileLogger.w(TAG, "disarm failed: ${it.message}")
            }
        }
    }

    private suspend fun writeAny(vararg writes: Triple<Int, Int, Int>): Boolean {
        var ok = false
        writes.distinct().forEach { (dev, fid, value) ->
            val writeOk = helperClient.write(dev = dev, fid = fid, value = value)
            WjxFileLogger.i(TAG, "fid dev=$dev fid=$fid value=$value ok=$writeOk")
            ok = writeOk || ok
        }
        return ok
    }

    private suspend fun writeFirstAccepted(vararg writes: Triple<Int, Int, Int>): Boolean {
        writes.distinct().forEach { (dev, fid, value) ->
            val writeOk = helperClient.write(dev = dev, fid = fid, value = value)
            WjxFileLogger.i(TAG, "fid fallback dev=$dev fid=$fid value=$value ok=$writeOk")
            if (writeOk) return true
        }
        return false
    }

    private fun pauseActiveMedia() {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val hadActivePlayback = runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                audioManager.activePlaybackConfigurations.any { config ->
                    runCatching {
                        config.javaClass.getMethod("isActive").invoke(config) as? Boolean
                    }.getOrDefault(false) == true ||
                        runCatching {
                            config.javaClass.getMethod("getPlayerState").invoke(config) as? Int
                        }.getOrDefault(-1) == 2
                }
            } else {
                false
            }
        }.getOrDefault(false)
        val musicActive = runCatching { audioManager.isMusicActive }.getOrDefault(false)
        runCatching {
            var focusGranted = false
            if (Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                            .build()
                    )
                    .build()
                focusGranted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                pauseFocus = request
            } else {
                @Suppress("DEPRECATION")
                focusGranted = audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
            dispatchMediaPause(audioManager)
            Log.i(
                TAG,
                "pauseActiveMedia hadActivePlayback=$hadActivePlayback musicActive=$musicActive " +
                    "focusGranted=$focusGranted"
            )
        }.onFailure {
            WjxFileLogger.w(TAG, "pauseActiveMedia failed: ${it.message}")
        }
    }

    private fun abandonPausedMediaFocus() {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            dispatchMediaPause(audioManager)
            if (Build.VERSION.SDK_INT >= 26) {
                pauseFocus?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            pauseFocus = null
            mainHandler.postDelayed({ dispatchMediaPause(audioManager) }, MEDIA_RESUME_GUARD_MS)
        }
    }

    private fun dispatchMediaPause(audioManager: AudioManager) {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        audioManager.dispatchMediaKeyEvent(event)
        audioManager.dispatchMediaKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP))
    }

    private fun requestPlaybackFocus(channel: ExteriorChannel): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        return runCatching {
            val attributes = WjxAudioAttributes.forOutside(channel)
            val result = if (Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .build()
                playFocus = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, channel.streamType, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrElse {
            WjxFileLogger.w(TAG, "requestPlaybackFocus failed: ${it.message}")
            false
        }
    }

    private fun fallbackStreamVolume(usage: Usage): Int =
        if (usage == Usage.Talk) {
            WjxExteriorSpeakerController.CHANNEL0_TALK_PRESET_VOLUME
        } else {
            WjxExteriorSpeakerController.CHANNEL0_PLAYBACK_PRESET_VOLUME
        }

    private fun abandonPlaybackFocus() {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                playFocus?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            playFocus = null
        }
    }

    private fun setCarLockSoundPlayState(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        for (name in BYD_AUDIO_MANAGER_CLASSES) {
            val ok = runCatching {
                val cls = Class.forName(name)
                val target = runCatching { cls.getDeclaredConstructor().newInstance() }.getOrNull()
                val method = cls.methods.firstOrNull {
                    it.name == "setCarLockSoundPlayState" && it.parameterTypes.size == 1
                } ?: return@runCatching false
                method.isAccessible = true
                method.invoke(target, value)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    enum class Usage {
        Playback,
        Talk,
        AutoSound,
    }

    companion object {
        private const val TAG = "BydExteriorAudio"
        private const val HELPER_READY_TIMEOUT_MS = 12_000L
        private const val MEDIA_RESUME_GUARD_MS = 180L
        private const val DEV_AUDIO = 1000
        private const val DEV_ADAS = 3
        private const val DEV_AUDIO_COMPAT = 1002
        private const val DEV_ADAS_COMPAT = 1000
        private const val EXTERIOR_SWITCH_SET = 891293750
        private const val AVAS_ROUTE_SET = 891293760
        private const val AUDIO_KARAOKE_MODE_SET = 372244513
        private const val SWITCH_ON = 1339031619
        private const val SWITCH_OFF = 3
        private const val AVAS_ROUTE_ON = 2
        private const val AVAS_ROUTE_OFF = 1
        private const val KARAOKE_MODE_EXTERIOR = 2
        private const val LOCK_CAR_SOUND_PLAYBACK_STATUS_SET = 13
        private const val LOCK_CAR_SOUND_PLAYBACK_ON = -1442839775
        private const val LOCK_CAR_SOUND_PLAYBACK_OFF = 1
        private const val ADAS_UE_BROADCAST_POSITION_SET = -1442839934
        private const val ADAS_BROADCAST_POSITION_ON = 1
        private const val ADAS_BROADCAST_POSITION_OFF = 0
        private const val LOCK_CAR_SOUND_PLAYBACK_STATUS_SET_COMPAT = -1442839775
        private const val ADAS_UE_BROADCAST_POSITION_SET_COMPAT = -1796699511
        private val BYD_AUDIO_MANAGER_CLASSES = listOf(
            "android.media.BydAudioManager",
            "com.byd.audio.BYDAudioManager",
            "android.media.byd.BYDAudioManager",
            "com.byd.car.audio.BydAudioManager",
            "com.byd.mediacenter.audio.BydAudioManager",
            "com.byd.ivi.audio.BydAudioManager",
        )
    }
}
