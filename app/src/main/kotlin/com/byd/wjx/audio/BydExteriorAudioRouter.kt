package com.byd.wjx.audio

import android.content.Context
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
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

    data class ArmResult(
        val channel: ExteriorChannel,
        val confirmed: Boolean,
        val writeResults: Map<ExteriorRouteWrite, Boolean>,
        val speakerOk: Boolean,
        val lockStateOk: Boolean,
    ) {
        val anyWriteAccepted: Boolean
            get() = writeResults.values.any { it }

        fun summary(): String =
            "confirmed=$confirmed writes=${writeResults.values.count { it }}/${writeResults.size} " +
                "speaker=$speakerOk lock=$lockStateOk"
    }

    suspend fun armVerified(
        channel: ExteriorChannel,
        usage: Usage = Usage.Playback,
        volumePercent: Int = 100,
    ): ArmResult? {
        val armed = armConfirmed(channel, usage, volumePercent)
        if (!armed.confirmed) {
            WjxFileLogger.w(
                TAG,
                "route rejected channel=$channel usage=$usage ${armed.summary()}",
            )
            runCatching { disarm(channel) }
            return null
        }
        delay(if (channel == ExteriorChannel.Channel0) CHANNEL_0_SETTLE_MS else CHANNEL_1_SETTLE_MS)
        val verified = reinforceConfirmed(channel, usage, volumePercent)
        if (verified.confirmed) {
            WjxFileLogger.i(TAG, "route verified channel=$channel usage=$usage ${verified.summary()}")
            return verified
        }
        WjxFileLogger.w(TAG, "route verify failed channel=$channel usage=$usage ${verified.summary()}")
        runCatching { disarm(channel) }
        return null
    }

    suspend fun armConfirmed(
        channel: ExteriorChannel,
        usage: Usage = Usage.Playback,
        volumePercent: Int = 100,
    ): ArmResult =
        routeMutex.withLock {
            val helperReady = withTimeoutOrNull(HELPER_READY_TIMEOUT_MS) {
                helperBootstrap.ensureCurrent()
            } ?: false
            if (!helperReady) {
                WjxFileLogger.w(TAG, "helper not ready, confirmed outside route blocked")
                return@withLock failedArmResult(channel)
            }
            val speakerOk = speakerController.acquire(
                channel,
                volumePercent = volumePercent,
                fallbackVolume = fallbackStreamVolume(usage),
                scaleFromMax = usage != Usage.AutoSound,
            )
            val lockStateOk = if (channel == ExteriorChannel.Channel1) {
                setLockSoundPlaybackState(true)
            } else {
                false
            }
            buildConfirmedResult(
                channel = channel,
                writeResults = writeAll(ExteriorRoutePlan.armWrites(channel)),
                speakerOk = speakerOk,
                lockStateOk = lockStateOk,
            ).also {
                WjxFileLogger.i(TAG, "armConfirmed channel=$channel usage=$usage volume=$volumePercent ${it.summary()}")
            }
        }

    suspend fun reinforceConfirmed(
        channel: ExteriorChannel,
        usage: Usage = Usage.Playback,
        volumePercent: Int = 100,
    ): ArmResult =
        routeMutex.withLock {
            val speakerOk = speakerController.reinforce(
                channel = channel,
                volumePercent = volumePercent,
                fallbackVolume = fallbackStreamVolume(usage),
                scaleFromMax = usage != Usage.AutoSound,
            )
            val lockStateOk = if (channel == ExteriorChannel.Channel1) {
                setLockSoundPlaybackState(true)
            } else {
                false
            }
            buildConfirmedResult(
                channel = channel,
                writeResults = writeAll(ExteriorRoutePlan.armWrites(channel)),
                speakerOk = speakerOk,
                lockStateOk = lockStateOk,
            ).also {
                WjxFileLogger.i(TAG, "reinforceConfirmed channel=$channel usage=$usage volume=$volumePercent ${it.summary()}")
            }
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
                writeAll(ExteriorRoutePlan.disarmWrites(channel))
                if (channel == ExteriorChannel.Channel1) {
                    setLockSoundPlaybackState(false)
                }
                speakerController.release(channel)
            }.onFailure {
                WjxFileLogger.w(TAG, "disarm failed: ${it.message}")
            }
        }
    }

    private suspend fun writeAll(writes: List<ExteriorRouteWrite>): Map<ExteriorRouteWrite, Boolean> {
        val results = LinkedHashMap<ExteriorRouteWrite, Boolean>()
        writes.distinct().forEach { write ->
            val writeOk = helperClient.write(dev = write.dev, fid = write.fid, value = write.value)
            WjxFileLogger.i(TAG, "fid dev=${write.dev} fid=${write.fid} value=${write.value} ok=$writeOk")
            results[write] = writeOk
        }
        return results
    }

    private fun buildConfirmedResult(
        channel: ExteriorChannel,
        writeResults: Map<ExteriorRouteWrite, Boolean>,
        speakerOk: Boolean,
        lockStateOk: Boolean,
    ): ArmResult {
        return ArmResult(
            channel = channel,
            confirmed = ExteriorRoutePlan.isStronglyConfirmed(
                channel = channel,
                writeResults = writeResults,
                speakerOk = speakerOk,
                lockStateOk = lockStateOk,
            ),
            writeResults = writeResults,
            speakerOk = speakerOk,
            lockStateOk = lockStateOk,
        )
    }

    private suspend fun setLockSoundPlaybackState(enabled: Boolean): Boolean {
        val helperOk = runCatching {
            helperClient.setLockSoundPlaybackState(enabled)
        }.getOrDefault(false)
        if (helperOk) {
            WjxFileLogger.i(TAG, "lock sound playback state enabled=$enabled source=helper ok=true")
            return true
        }
        val localOk = BydLockSoundPlaybackState.setEnabled(enabled)
        WjxFileLogger.i(TAG, "lock sound playback state enabled=$enabled source=local ok=$localOk")
        return localOk
    }

    private fun failedArmResult(channel: ExteriorChannel): ArmResult =
        ArmResult(
            channel = channel,
            confirmed = false,
            writeResults = emptyMap(),
            speakerOk = false,
            lockStateOk = false,
        )

    private fun fallbackStreamVolume(usage: Usage): Int =
        if (usage == Usage.Talk) {
            WjxExteriorSpeakerController.CHANNEL0_TALK_PRESET_VOLUME
        } else {
            WjxExteriorSpeakerController.CHANNEL0_PLAYBACK_PRESET_VOLUME
        }

    enum class Usage {
        Playback,
        Talk,
        AutoSound,
    }

    companion object {
        private const val TAG = "BydExteriorAudio"
        private const val HELPER_READY_TIMEOUT_MS = 12_000L
        private const val CHANNEL_0_SETTLE_MS = 300L
        private const val CHANNEL_1_SETTLE_MS = 120L
    }
}
