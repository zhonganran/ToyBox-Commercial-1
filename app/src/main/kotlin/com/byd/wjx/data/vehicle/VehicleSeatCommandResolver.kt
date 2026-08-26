package com.byd.wjx.data.vehicle

internal data class VehicleSeatTarget(
    val group: SeatGroup,
    val level: Int,
)

/** Converts translated seat aliases and legacy values into the adaptive seat channel model. */
internal object VehicleSeatCommandResolver {
    fun resolve(actionName: String, value: Int): VehicleSeatTarget? {
        val group = when {
            actionName.startsWith("driver_seat_heat") -> SeatGroup.DRIVER_HEAT
            actionName.startsWith("driver_seat_vent") -> SeatGroup.DRIVER_VENT
            actionName.startsWith("passenger_seat_heat") -> SeatGroup.PASSENGER_HEAT
            actionName.startsWith("passenger_seat_vent") -> SeatGroup.PASSENGER_VENT
            else -> return null
        }
        val level = when {
            actionName.endsWith("_off") -> 0
            actionName.endsWith("_on") -> 1
            actionName.endsWith("_lvl2") -> 2
            actionName.endsWith("_lvl3") -> 3
            actionName.endsWith("_lvl4") -> 4
            actionName.endsWith("_lvl5") -> 5
            value == 1 -> 0
            value in 2..6 -> value - 1
            value in 0..5 -> value
            else -> return null
        }
        return VehicleSeatTarget(group, level.coerceIn(0, 5))
    }
}
