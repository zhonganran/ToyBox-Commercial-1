package com.byd.wjx.data.nativestack

/**
 * Canonical BYD enum semantics used after raw FID decoding.
 *
 * Values are deliberately not remapped. Every consumer sees the official vendor value and uses
 * these predicates instead of inventing a second, incompatible 0/1/2 encoding.
 */
internal object BydSignalSemantics {

    // ENERGY_OPERATION / 0x2120000E
    const val DRIVE_ECONOMY = 1
    const val DRIVE_SPORT = 2
    const val DRIVE_COMFORT = 3
    const val DRIVE_SNOW = 4
    const val DRIVE_MUDDY = 5
    const val DRIVE_SAND = 6
    const val DRIVE_MOUNTAIN = 7
    const val DRIVE_RACING = 11

    fun isValidDriveMode(value: Int?): Boolean =
        value in DRIVE_ECONOMY..DRIVE_MOUNTAIN || value == DRIVE_RACING

    /**
     * AutoTark 3.7.2 combines ENERGY_OPERATION_MODE with the instrument mode indicator. The
     * indicator is meaningful only after leaving P; in P it can retain the previous terrain mode.
     */
    fun selectDriveMode(baseMode: Int?, indicatorMode: Int?, gear: Int?): Int? {
        val base = baseMode?.takeIf(::isValidDriveMode)
        if (gear !in DRIVE_MODE_INDICATOR_GEARS) return base
        val terrain = indicatorMode?.takeIf {
            it == DRIVE_SNOW || it == DRIVE_MUDDY || it == DRIVE_SAND ||
                it == DRIVE_MOUNTAIN || it == DRIVE_RACING
        }
        return terrain ?: base
    }

    // ENERGY_MODE_INSTRUMENT(_50)
    const val ENERGY_STOP = 0
    const val ENERGY_EV = 1
    const val ENERGY_FORCE_EV = 2
    const val ENERGY_HEV = 3
    const val ENERGY_FUEL = 4
    const val ENERGY_KEEP = 5

    fun isValidWorkMode(value: Int?): Boolean = value in ENERGY_STOP..ENERGY_KEEP

    /**
     * Newer platforms expose ENERGY_MODE_INSTRUMENT_50 while older platforms expose the legacy
     * ENERGY_MODE_INSTRUMENT FID. Unsupported FIDs commonly return a valid-looking zero, so a
     * non-zero mode wins a disagreement; otherwise the newer FID is authoritative.
     */
    fun selectWorkMode(newInstrument50: Int?, legacyInstrument: Int?): Int? {
        val newValue = newInstrument50?.takeIf(::isValidWorkMode)
        val legacyValue = legacyInstrument?.takeIf(::isValidWorkMode)
        return when {
            newValue == null -> legacyValue
            legacyValue == null || newValue == legacyValue -> newValue
            newValue != ENERGY_STOP -> newValue
            legacyValue != ENERGY_STOP -> legacyValue
            else -> newValue
        }
    }

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

    private val DRIVE_MODE_INDICATOR_GEARS = setOf(2, 3, 4)
}
