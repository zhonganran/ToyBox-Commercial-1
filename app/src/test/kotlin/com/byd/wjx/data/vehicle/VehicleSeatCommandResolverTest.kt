package com.byd.wjx.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleSeatCommandResolverTest {
    @Test
    fun `translated aliases map to the expected seat group and level`() {
        assertEquals(
            VehicleSeatTarget(SeatGroup.DRIVER_HEAT, 0),
            VehicleSeatCommandResolver.resolve("driver_seat_heat_off", 1),
        )
        assertEquals(
            VehicleSeatTarget(SeatGroup.PASSENGER_VENT, 5),
            VehicleSeatCommandResolver.resolve("passenger_seat_vent_lvl5", 6),
        )
    }

    @Test
    fun `legacy numeric aliases retain zero to five level conversion`() {
        assertEquals(
            VehicleSeatTarget(SeatGroup.DRIVER_VENT, 0),
            VehicleSeatCommandResolver.resolve("driver_seat_vent", 1),
        )
        assertEquals(
            VehicleSeatTarget(SeatGroup.PASSENGER_HEAT, 4),
            VehicleSeatCommandResolver.resolve("passenger_seat_heat", 5),
        )
    }

    @Test
    fun `non seat and invalid seat commands are rejected`() {
        assertNull(VehicleSeatCommandResolver.resolve("window_driver_pos", 50))
        assertNull(VehicleSeatCommandResolver.resolve("driver_seat_heat", 99))
    }
}
