package com.byd.wjx.data.nativestack

/**
 * Canonical BYD enum semantics used after raw FID decoding.
 *
 * Values are deliberately not remapped. Every consumer sees the official vendor value and uses
 * these predicates instead of inventing a second, incompatible 0/1/2 encoding.
 */
internal object BydSignalSemantics {

    // BODYWORK_POWER_LEVEL
    const val POWER_OFF = 0
    const val POWER_ACC = 1
    const val POWER_ON = 2
    const val POWER_OK = 3
    const val POWER_FAKE_OK = 4

    fun isPowered(value: Int?): Boolean = value in POWER_ACC..POWER_FAKE_OK
    fun isDriveReady(value: Int?): Boolean = value == POWER_OK || value == POWER_FAKE_OK

    // CHARGING_BATTERRY_DEVICE_STATE
    const val CHARGING_READY = 0
    const val CHARGING = 1
    const val CHARGING_FINISHED = 2
    const val CHARGING_PAUSED = 13

    fun isCharging(value: Int?): Boolean = value == CHARGING || value == CHARGING_PAUSED

    // BODYWORK_BACKDOOR_CURRENT_POSITION
    const val TRUNK_OPEN = 1
    const val TRUNK_CLOSED = 2
    const val TRUNK_INTERMEDIATE = 3

    fun isTrunkNotClosed(value: Int?): Boolean =
        value == TRUNK_OPEN || value == TRUNK_INTERMEDIATE

    // CHARGING_LEFT/RIGHT_COVER_STATE
    const val COVER_CLOSED = 1
    const val COVER_OPEN = 2

    fun isCoverOpen(value: Int?): Boolean = value == COVER_OPEN

    // BYD per-door lock state
    const val DOOR_UNLOCKED = 1
    const val DOOR_LOCKED = 2

    fun isDoorLocked(value: Int?): Boolean = value == DOOR_LOCKED
}
