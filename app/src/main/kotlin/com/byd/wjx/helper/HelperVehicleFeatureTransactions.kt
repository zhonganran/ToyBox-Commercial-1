package com.byd.wjx.helper

import android.os.Parcel
import com.byd.wjx.audio.BydLockSoundPlaybackState

internal fun HelperDaemonRuntime.handleVehicleFeatureTransaction(
    code: Int,
    data: Parcel,
    reply: Parcel?,
): Boolean = when (code) {
    HelperBinderProtocol.TX_HEADLIGHT_QUERY_SUPPORT -> headlightReply(reply, "support") {
        headlightClient.querySupport()
    }

    HelperBinderProtocol.TX_HEADLIGHT_GET_MODE -> headlightReply(reply, "get") {
        headlightClient.getMode()
    }

    HelperBinderProtocol.TX_HEADLIGHT_SET_MODE -> headlightReply(reply, "set") {
        headlightClient.setMode(data.readInt())
    }

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

    else -> error("vehicle feature transaction routed incorrectly: $code")
}

private inline fun HelperDaemonRuntime.headlightReply(
    reply: Parcel?,
    operation: String,
    block: () -> BydHeadlightReply,
): Boolean = runCatching {
    val result = block()
    reply?.writeInt(result.transportStatus)
    reply?.writeInt(result.serviceCode)
    reply?.writeInt(result.value)
    true
}.getOrElse {
    helperLog("HeadlightBinder", "$operation transaction failed: ${throwableSummary(it)}")
    reply?.writeInt(-1)
    reply?.writeInt(-1)
    reply?.writeInt(-1)
    true
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
