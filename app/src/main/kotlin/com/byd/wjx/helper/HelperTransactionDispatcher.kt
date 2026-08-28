package com.byd.wjx.helper

import android.os.Parcel

internal enum class HelperTransactionGroup {
    VEHICLE_IO,
    PROJECTION,
    SYSTEM,
    VEHICLE_FEATURE,
}

internal fun helperTransactionGroup(code: Int): HelperTransactionGroup? = when (code) {
    HelperBinderProtocol.TX_PING,
    HelperBinderProtocol.TX_READ,
    HelperBinderProtocol.TX_READ_BATCH,
    HelperBinderProtocol.TX_WRITE,
    -> HelperTransactionGroup.VEHICLE_IO

    HelperBinderProtocol.TX_CREATE_VIRTUAL_DISPLAY,
    HelperBinderProtocol.TX_RELEASE_VIRTUAL_DISPLAY,
    HelperBinderProtocol.TX_LAUNCH_APP,
    HelperBinderProtocol.TX_GET_TASK_ID,
    HelperBinderProtocol.TX_MOVE_TASK_TO_DISPLAY,
    HelperBinderProtocol.TX_SET_TASK_BOUNDS,
    HelperBinderProtocol.TX_SET_FOCUSED_TASK,
    HelperBinderProtocol.TX_SET_TASK_WINDOWING_MODE,
    HelperBinderProtocol.TX_GRANT_OVERLAY_PERMISSION,
    HelperBinderProtocol.TX_LAUNCH_AND_FORCE,
    HelperBinderProtocol.TX_ENABLE_ACCESSIBILITY,
    -> HelperTransactionGroup.PROJECTION

    HelperBinderProtocol.TX_READ_VEHICLE_VIN,
    HelperBinderProtocol.TX_SENTRY_SHELL,
    HelperBinderProtocol.TX_EXEC_SHELL,
    -> HelperTransactionGroup.SYSTEM

    HelperBinderProtocol.TX_SYNC_LOCK_SOUND_CONFIG,
    HelperBinderProtocol.TX_DEBUG_PLAY_LOCK_SOUND,
    HelperBinderProtocol.TX_STOP_LOCK_SOUND,
    HelperBinderProtocol.TX_PLAY_SENTRY_EVENT,
    HelperBinderProtocol.TX_SET_LOCK_SOUND_PLAYBACK_STATE,
    -> HelperTransactionGroup.VEHICLE_FEATURE

    else -> null
}

internal class HelperTransactionDispatcher(
    private val runtime: HelperDaemonRuntime,
) {
    fun dispatch(code: Int, data: Parcel, reply: Parcel?): Boolean? =
        when (helperTransactionGroup(code)) {
            HelperTransactionGroup.VEHICLE_IO -> runtime.handleVehicleIoTransaction(code, data, reply)
            HelperTransactionGroup.PROJECTION -> runtime.handleProjectionTransaction(code, data, reply)
            HelperTransactionGroup.SYSTEM -> runtime.handleSystemTransaction(code, data, reply)
            HelperTransactionGroup.VEHICLE_FEATURE ->
                runtime.handleVehicleFeatureTransaction(code, data, reply)
            null -> null
        }
}
