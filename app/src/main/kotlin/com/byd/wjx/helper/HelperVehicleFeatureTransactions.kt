package com.byd.wjx.helper

import android.os.Parcel
import com.byd.wjx.audio.BydLockSoundPlaybackState
import com.byd.wjx.data.vehicle.MirrorPosition

internal fun HelperDaemonRuntime.handleVehicleFeatureTransaction(
    code: Int,
    data: Parcel,
    reply: Parcel?,
): Boolean = when (code) {
    HelperBinderProtocol.TX_SYNC_LOCK_SOUND_CONFIG -> lockSoundReply(reply, "sync") {
        (if (lockSoundRuntime.syncConfig(data.readString().orEmpty())) 0 else -1) to 0
    }

    HelperBinderProtocol.TX_DEBUG_PLAY_LOCK_SOUND -> lockSoundReply(reply, "debug play") {
        val kind = data.readInt()
        val played = lockSoundRuntime.playEventForBinder(kind, "binder")
        (if (kind in 0..2) 0 else -2) to if (played) 1 else 0
    }

    HelperBinderProtocol.TX_STOP_LOCK_SOUND -> lockSoundReply(reply, "stop") {
        val kind = data.readInt()
        val stopped = lockSoundRuntime.stopEventForBinder(kind, "binder_stop")
        (if (kind in 0..2) 0 else -2) to if (stopped) 1 else 0
    }

    HelperBinderProtocol.TX_PLAY_SENTRY_EVENT -> lockSoundReply(reply, "sentry event") {
        val source = data.readString().orEmpty().ifBlank { "binder_sentry" }.take(200)
        0 to if (lockSoundRuntime.playSentryEventForBinder(source)) 1 else 0
    }

    HelperBinderProtocol.TX_SET_LOCK_SOUND_PLAYBACK_STATE -> runCatching {
        val enabledValue = data.readInt()
        require(enabledValue == 0 || enabledValue == 1) { "invalid enabled=$enabledValue" }
        val enabled = enabledValue == 1
        val ok = BydLockSoundPlaybackState.setEnabled(enabled)
        helperLog("ExteriorAudio", "lock sound playback state enabled=$enabled ok=$ok")
        reply?.writeInt(if (ok) 0 else -1)
        reply?.writeInt(0)
        true
    }.getOrElse {
        helperLog("ExteriorAudio", "lock sound playback state failed: ${throwableSummary(it)}")
        reply?.writeInt(-1)
        reply?.writeInt(0)
        true
    }

    HelperBinderProtocol.TX_READ_MIRROR_POSITION -> runCatching {
        val position = withAutoserviceTransactionLock {
            HelperMirrorPlatform.readPosition(systemContext)
        }
        reply?.writeInt(if (position != null) 0 else -2)
        reply?.writeFloat(position?.leftHorizontal ?: 0f)
        reply?.writeFloat(position?.leftVertical ?: 0f)
        reply?.writeFloat(position?.rightHorizontal ?: 0f)
        reply?.writeFloat(position?.rightVertical ?: 0f)
        true
    }.getOrElse {
        helperLog("MirrorPosition", "read failed: ${throwableSummary(it)}")
        reply?.writeInt(-1)
        repeat(4) { reply?.writeFloat(0f) }
        true
    }

    HelperBinderProtocol.TX_APPLY_MIRROR_POSITION -> runCatching {
        val position = MirrorPosition(
            leftHorizontal = data.readFloat(),
            leftVertical = data.readFloat(),
            rightHorizontal = data.readFloat(),
            rightVertical = data.readFloat(),
        )
        require(position.isSane()) { "invalid mirror position" }
        val applied = withAutoserviceTransactionLock {
            HelperMirrorPlatform.applyPosition(systemContext, position)
        }
        reply?.writeInt(if (applied) 0 else -2)
        reply?.writeInt(0)
        true
    }.getOrElse {
        helperLog("MirrorPosition", "apply failed: ${throwableSummary(it)}")
        reply?.writeInt(-1)
        reply?.writeInt(0)
        true
    }

    else -> error("vehicle feature transaction routed incorrectly: $code")
}

private inline fun lockSoundReply(
    reply: Parcel?,
    operation: String,
    block: () -> Pair<Int, Int>,
): Boolean = runCatching {
    val (status, value) = block()
    reply?.writeInt(status)
    reply?.writeInt(value)
    true
}.getOrElse {
    helperLog("LockSound", "$operation failed: ${it.javaClass.simpleName}:${it.message.orEmpty()}")
    reply?.writeInt(-1)
    reply?.writeInt(0)
    true
}
