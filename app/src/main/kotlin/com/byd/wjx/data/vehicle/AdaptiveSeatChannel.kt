package com.byd.wjx.data.vehicle

enum class SeatGroup {
    DRIVER_HEAT,
    DRIVER_VENT,
    PASSENGER_HEAT,
    PASSENGER_VENT,
}

fun interface SeatWriter {
    suspend fun write(actionName: String, value: Int): WriteOutcome
}

/**
 * Seat heat/vent compatibility layer.
 *
 * Newer cars use dev=1000 switch+level writes. Some models only react to the
 * older dev=1001 single-fid channel. We probe once, remember the working channel,
 * and avoid retrying a known dead path on every automation action.
 */
class AdaptiveSeatChannel(
    private val writer: SeatWriter,
    private val store: SeatChannelStore,
) {
    private data class Actions(val switch: String, val level: String, val fallback: String)

    suspend fun actuate(group: SeatGroup, level: Int): Boolean {
        val actions = actionsFor(group)
        return when (store.winner()) {
            SeatChannel.PRIMARY -> primary(actions, level) == WriteOutcome.REAL
            SeatChannel.FALLBACK -> fallbackDirect(actions, level) == WriteOutcome.REAL
            SeatChannel.UNKNOWN -> probe(actions, level)
        }
    }

    private suspend fun probe(actions: Actions, level: Int): Boolean {
        val primary = primary(actions, level)
        if (primary == WriteOutcome.REAL) {
            store.setWinner(SeatChannel.PRIMARY)
            return true
        }
        if (primary == WriteOutcome.TRANSIENT) return false

        val fallback = fallbackProbe(actions, level)
        if (fallback == WriteOutcome.REAL) {
            store.setWinner(SeatChannel.FALLBACK)
            return true
        }
        return false
    }

    private suspend fun primary(actions: Actions, level: Int): WriteOutcome {
        val switchOutcome = writer.write(actions.switch, if (level == 0) 2 else 1)
        if (level > 0 && switchOutcome == WriteOutcome.REAL) {
            writer.write(actions.level, level.coerceIn(1, 5))
        }
        return switchOutcome
    }

    private suspend fun fallbackDirect(actions: Actions, level: Int): WriteOutcome =
        writer.write(actions.fallback, fallbackValue(level))

    private suspend fun fallbackProbe(actions: Actions, level: Int): WriteOutcome {
        val outcome = writer.write(actions.fallback, if (level == 0) 1 else 2)
        if (level > 1 && outcome == WriteOutcome.REAL) {
            writer.write(actions.fallback, fallbackValue(level))
        }
        return outcome
    }

    private fun fallbackValue(level: Int): Int =
        if (level == 0) 1 else (level + 1).coerceIn(2, 6)

    private fun actionsFor(group: SeatGroup): Actions = when (group) {
        SeatGroup.DRIVER_HEAT ->
            Actions("driver_seat_heat_switch", "driver_seat_heat_level", "driver_seat_heat_fallback")
        SeatGroup.DRIVER_VENT ->
            Actions("driver_seat_vent_switch", "driver_seat_vent_level", "driver_seat_vent_fallback")
        SeatGroup.PASSENGER_HEAT ->
            Actions("passenger_seat_heat_switch", "passenger_seat_heat_level", "passenger_seat_heat_fallback")
        SeatGroup.PASSENGER_VENT ->
            Actions("passenger_seat_vent_switch", "passenger_seat_vent_level", "passenger_seat_vent_fallback")
    }
}
