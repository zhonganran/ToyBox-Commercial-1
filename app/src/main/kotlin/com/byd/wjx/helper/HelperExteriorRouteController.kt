package com.byd.wjx.helper

import android.content.Context
import android.os.IBinder
import com.byd.wjx.audio.BydLockSoundPlaybackState
import com.byd.wjx.audio.ExteriorChannel
import com.byd.wjx.audio.ExteriorRoutePlan
import com.byd.wjx.audio.ExteriorRouteWrite
import com.byd.wjx.audio.WjxExteriorSpeakerController

internal class WjxHelperExteriorRouteController(
    private val svc: IBinder,
    private val autoIface: String,
    systemContext: Context?,
) {
    private val speakerController = systemContext?.let { WjxExteriorSpeakerController(it) }

    fun arm(channel: ExteriorChannel): Boolean {
        runCatching {
            speakerController?.acquire(
                channel,
                volumePercent = ROUTE_VOLUME_PERCENT,
                fallbackVolume = if (channel == ExteriorChannel.Channel0) {
                    WjxExteriorSpeakerController.CHANNEL0_PLAYBACK_PRESET_VOLUME
                } else {
                    -1
                },
            )
        }
        val fidOk = writeAny(ExteriorRoutePlan.armWrites(channel))
        if (channel == ExteriorChannel.Channel1) {
            BydLockSoundPlaybackState.setEnabled(true)
        }
        return fidOk
    }

    fun refresh(channel: ExteriorChannel): Boolean {
        val speakerOk = runCatching {
            speakerController?.reinforce(
                channel,
                volumePercent = ROUTE_VOLUME_PERCENT,
                fallbackVolume = if (channel == ExteriorChannel.Channel0) {
                    WjxExteriorSpeakerController.CHANNEL0_PLAYBACK_PRESET_VOLUME
                } else {
                    -1
                },
            ) == true
        }.getOrDefault(false)
        val fidOk = writeAny(ExteriorRoutePlan.armWrites(channel))
        val playbackStateOk = if (channel == ExteriorChannel.Channel1) {
            BydLockSoundPlaybackState.setEnabled(true)
        } else {
            false
        }
        helperLog(
            TAG,
            "route refresh channel=$channel speaker=$speakerOk fid=$fidOk playbackState=$playbackStateOk",
        )
        return speakerOk || fidOk || playbackStateOk
    }

    fun switch(from: ExteriorChannel, to: ExteriorChannel): ExteriorChannel? {
        if (from == to) return if (refresh(from)) from else null
        disarm(from)
        if (arm(to)) {
            helperLog(TAG, "route switched from=$from to=$to")
            return to
        }
        disarm(to)
        helperLog(TAG, "route switch failed from=$from to=$to; restoring $from")
        if (arm(from)) return from
        disarm(from)
        helperLog(TAG, "route restore failed channel=$from")
        return null
    }

    fun disarm(channel: ExteriorChannel) {
        runCatching {
            writeAny(ExteriorRoutePlan.disarmWrites(channel))
            if (channel == ExteriorChannel.Channel1) {
                BydLockSoundPlaybackState.setEnabled(false)
            }
            speakerController?.release(channel)
        }.onFailure {
            helperLog(TAG, "disarm failed channel=$channel ${throwableSummary(it)}")
        }
    }

    private fun writeAny(writes: List<ExteriorRouteWrite>): Boolean {
        var ok = false
        writes.distinct().forEach { write ->
            val status = autoserviceTransact(
                svc,
                autoIface,
                6,
                write.dev,
                write.fid,
                write.value,
                writeValue = true,
            ).first
            helperLog(
                TAG,
                "route write dev=${write.dev} fid=${write.fid} value=${write.value} status=$status",
            )
            ok = status >= 0 || ok
        }
        return ok
    }

    private companion object {
        private const val TAG = "LockSound"
        private const val ROUTE_VOLUME_PERCENT = 100
    }
}
