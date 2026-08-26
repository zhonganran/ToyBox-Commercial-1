package com.byd.wjx.data.vehicle

data class AutoDoorControlResult(
    val success: Boolean,
    val message: String,
)

data class AutoDoorSideSnapshot(
    val key: String,
    val label: String,
    val shortLabel: String,
    val action: Int?,
    val percent: Int?,
    val statusText: String,
    val nextCommand: Int,
    val nextCommandLabel: String,
)

internal enum class AutoDoorMotionDirection {
    OPENING,
    CLOSING,
}

internal data class AutoDoorDisplayDecision(
    val statusText: String,
    val nextCommand: Int,
    val rememberedMotion: AutoDoorMotionDirection?,
)

internal data class AutoDoorTarget(
    val key: String,
    val label: String,
    val writeFid: Int,
    val stateFid: Int,
    val positionFid: Int,
    val coverFid: Int,
    val maxSafeSpeedKmh: Float,
) {
    val shortLabel: String
        get() = label.removeSuffix("电滑门")
}

internal data class AutoDoorCommandReadings(
    val speedRaw: Long?,
    val speedKmh: Float?,
    val slideReadyRaw: Long?,
    val slideReady: Int?,
    val coverRaw: Long?,
    val coverState: Int?,
    val beforeState: Int?,
    val beforePosition: Int?,
)

internal data class AutoDoorStateReadings(
    val action: Int?,
    val percent: Int?,
)

internal data class AutoDoorWriteResult(
    val status: Int?,
    val elapsedMs: Long,
)
