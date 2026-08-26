package com.byd.wjx.helper

internal const val LOCK_SOUND_DOOR_UNLOCKED = 1
internal const val LOCK_SOUND_DOOR_LOCKED = 2

internal data class SentryWarningReading(
    val level: Int?,
    val source: String,
)

internal data class LockSoundPowerSnapshot(
    val vehicleState: Int?,
    val bodyworkLevel: Int?,
    val sdkPower: Int?,
) {
    fun blocksPlayback(): Boolean = shouldBlockLockSoundForPower(vehicleState, bodyworkLevel, sdkPower)

    fun summary(): String =
        "vehicle=$vehicleState body=$bodyworkLevel sdk=$sdkPower blocked=${blocksPlayback()}"
}

internal data class LockSoundVehicleAccessSnapshot(
    val lockFrontLeft: Int?,
    val lockFrontRight: Int?,
    val lockRearLeft: Int?,
    val lockRearRight: Int?,
    val doorFrontLeftOpen: Boolean?,
    val doorFrontRightOpen: Boolean?,
    val doorRearLeftOpen: Boolean?,
    val doorRearRightOpen: Boolean?,
    val trunkOpen: Boolean?,
    val hoodOpen: Boolean?,
) {
    private val locks: List<Int?>
        get() = listOf(lockFrontLeft, lockFrontRight, lockRearLeft, lockRearRight)

    private val openings: List<Boolean?>
        get() = listOf(
            doorFrontLeftOpen,
            doorFrontRightOpen,
            doorRearLeftOpen,
            doorRearRightOpen,
            trunkOpen,
            hoodOpen,
        )

    fun accessDetected(): Boolean =
        locks.any { it == LOCK_SOUND_DOOR_UNLOCKED } || openings.any { it == true }

    fun secureForSentry(): Boolean =
        !accessDetected() &&
            locks.any { it == LOCK_SOUND_DOOR_LOCKED } &&
            (locks.any { it != null } || openings.any { it != null })

    fun summary(): String =
        "lock=$lockFrontLeft/$lockFrontRight/$lockRearLeft/$lockRearRight " +
            "door=$doorFrontLeftOpen/$doorFrontRightOpen/$doorRearLeftOpen/$doorRearRightOpen " +
            "trunk=$trunkOpen hood=$hoodOpen"
}

internal fun shouldBlockLockSoundForPower(
    vehicleState: Int?,
    bodyworkLevel: Int?,
    sdkPower: Int?,
): Boolean {
    val values = listOf(vehicleState, bodyworkLevel, sdkPower)
    if (values.any { it != null && it >= 1 }) return true
    return values.none { it == 0 }
}

internal fun normalizeVehicleAccessLockState(raw: Int?): Int? =
    when (raw) {
        0, 1 -> LOCK_SOUND_DOOR_UNLOCKED
        2 -> LOCK_SOUND_DOOR_LOCKED
        else -> null
    }

internal fun validateLockSoundDoorState(raw: Int?): Int? =
    when (raw) {
        1 -> LOCK_SOUND_DOOR_UNLOCKED
        2 -> LOCK_SOUND_DOOR_LOCKED
        else -> null
    }
