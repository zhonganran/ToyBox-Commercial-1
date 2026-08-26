package com.byd.wjx.helper

import com.byd.wjx.audio.ExteriorChannel
import java.io.File
import org.json.JSONObject

internal const val LOCK_SOUND_CONFIG_PATH = "/data/local/tmp/wjx_lock_sound.json"
internal const val LOCK_SOUND_UNLOCK_PATH = "/data/local/tmp/wjx_unlock.wav"
internal const val LOCK_SOUND_LOCK_PATH = "/data/local/tmp/wjx_lock.wav"
internal const val LOCK_SOUND_SENTRY_PATH = "/data/local/tmp/wjx_sentry.wav"
internal const val LOCK_SOUND_UNLOCK_PCM_PATH = "/data/local/tmp/wjx_unlock.pcm"
internal const val LOCK_SOUND_LOCK_PCM_PATH = "/data/local/tmp/wjx_lock.pcm"
internal const val LOCK_SOUND_SENTRY_PCM_PATH = "/data/local/tmp/wjx_sentry.pcm"

internal data class LockSoundRuntimeConfig(
    val channel: ExteriorChannel = ExteriorChannel.Channel1,
    val unlock: EventConfig = EventConfig(),
    val lock: EventConfig = EventConfig(),
    val sentry: EventConfig = EventConfig(),
) {
    fun eventConfig(event: LockEvent): EventConfig =
        when (event) {
            LockEvent.Unlock -> unlock
            LockEvent.Lock -> lock
            LockEvent.Sentry -> sentry
        }

    fun summary(): String =
        "channel=$channel playerVolume=1.0 playerGain=$LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB " +
            "pcmGain=$LOCK_SOUND_PCM_ENHANCER_GAIN_MB " +
            "unlock=${unlock.enabled}/pcm=${unlock.pcm.usable()} " +
            "lock=${lock.enabled}/pcm=${lock.pcm.usable()} " +
            "sentry=${sentry.enabled}/pcm=${sentry.pcm.usable()}"
}

internal class LockSoundConfigStore(
    private val file: File = File(LOCK_SOUND_CONFIG_PATH),
) {
    fun read(): LockSoundRuntimeConfig {
        if (!file.isFile) return LockSoundRuntimeConfig()
        return runCatching {
            val json = JSONObject(file.readText())
            LockSoundRuntimeConfig(
                channel = parseLockSoundChannel(json.optString("channel", ExteriorChannel.Channel1.name)),
                unlock = json.readEvent(LOCK_SOUND_KEY_UNLOCK),
                lock = json.readEvent(LOCK_SOUND_KEY_LOCK),
                sentry = json.readEvent(LOCK_SOUND_KEY_SENTRY),
            )
        }.getOrDefault(LockSoundRuntimeConfig())
    }

    fun write(config: LockSoundRuntimeConfig) {
        val json = JSONObject()
            .put("version", 1)
            .put("channel", config.channel.name)
            .putEvent(LOCK_SOUND_KEY_UNLOCK, config.unlock)
            .putEvent(LOCK_SOUND_KEY_LOCK, config.lock)
            .putEvent(LOCK_SOUND_KEY_SENTRY, config.sentry)
        file.writeText(json.toString())
    }
}

internal fun parseLockSoundChannel(value: String): ExteriorChannel =
    runCatching { ExteriorChannel.valueOf(value) }.getOrDefault(ExteriorChannel.Channel1)

private fun JSONObject.readEvent(key: String): EventConfig {
    val obj = optJSONObject(key) ?: return EventConfig()
    val path = obj.optString("path")
    val pcm = obj.optJSONObject("pcm")?.let {
        PcmConfig(
            path = it.optString("path"),
            sampleRate = it.optInt("sampleRate", 0),
            channelCount = it.optInt("channelCount", 0),
            bytes = it.optLong("bytes", 0L),
        )
    } ?: PcmConfig()
    return EventConfig(
        enabled = obj.optBoolean("enabled", false) && File(path).isFile,
        path = path,
        pcm = pcm.takeIf { it.usable() && File(it.path).isFile } ?: PcmConfig(),
    )
}

private fun JSONObject.putEvent(key: String, config: EventConfig): JSONObject =
    put(
        key,
        JSONObject()
            .put("enabled", config.enabled)
            .put("path", config.path)
            .put(
                "pcm",
                JSONObject()
                    .put("path", config.pcm.path)
                    .put("sampleRate", config.pcm.sampleRate)
                    .put("channelCount", config.pcm.channelCount)
                    .put("bytes", config.pcm.bytes),
            ),
    )
