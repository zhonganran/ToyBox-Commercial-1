package com.byd.wjx.data.vehicle

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSeatChannelTest {

    @Test
    fun `primary real action is remembered`() = runTest {
        val store = InMemorySeatChannelStore()
        val writes = mutableListOf<Pair<String, Int>>()
        val channel = AdaptiveSeatChannel(
            writer = SeatWriter { action, value ->
                writes += action to value
                WriteOutcome.REAL
            },
            store = store,
        )

        assertTrue(channel.actuate(SeatGroup.DRIVER_HEAT, 2))

        assertEquals(SeatChannel.PRIMARY, store.winner())
        assertEquals(
            listOf("driver_seat_heat_switch" to 1, "driver_seat_heat_level" to 2),
            writes,
        )
    }

    @Test
    fun `primary noop probes fallback and remembers fallback`() = runTest {
        val store = InMemorySeatChannelStore()
        val writes = mutableListOf<Pair<String, Int>>()
        val channel = AdaptiveSeatChannel(
            writer = SeatWriter { action, value ->
                writes += action to value
                if (action.endsWith("_switch")) WriteOutcome.NOOP else WriteOutcome.REAL
            },
            store = store,
        )

        assertTrue(channel.actuate(SeatGroup.PASSENGER_VENT, 3))

        assertEquals(SeatChannel.FALLBACK, store.winner())
        assertEquals(
            listOf(
                "passenger_seat_vent_switch" to 1,
                "passenger_seat_vent_fallback" to 2,
                "passenger_seat_vent_fallback" to 4,
            ),
            writes,
        )
    }

    @Test
    fun `transient primary does not learn fallback`() = runTest {
        val store = InMemorySeatChannelStore()
        val channel = AdaptiveSeatChannel(
            writer = SeatWriter { _, _ -> WriteOutcome.TRANSIENT },
            store = store,
        )

        val ok = channel.actuate(SeatGroup.DRIVER_VENT, 1)

        assertTrue(!ok)
        assertEquals(SeatChannel.UNKNOWN, store.winner())
    }
}
