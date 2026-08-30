package com.byd.wjx.clustercard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClusterCardDayNightModeTest {
    @Test
    fun `vehicle value one is nighttime`() {
        assertEquals(true, ClusterCardDayNightMode.parseVehicleNightMode("1"))
    }

    @Test
    fun `vehicle value zero is daytime`() {
        assertEquals(false, ClusterCardDayNightMode.parseVehicleNightMode("0"))
    }

    @Test
    fun `textual vehicle values are accepted`() {
        assertEquals(false, ClusterCardDayNightMode.parseVehicleNightMode("daytime"))
        assertEquals(true, ClusterCardDayNightMode.parseVehicleNightMode("dark"))
    }

    @Test
    fun `unknown vehicle value falls back to android configuration`() {
        assertNull(ClusterCardDayNightMode.parseVehicleNightMode(null))
        assertNull(ClusterCardDayNightMode.parseVehicleNightMode("-2147483648"))
        assertNull(ClusterCardDayNightMode.parseVehicleNightMode("invalid"))
    }

    @Test
    fun `live light signal value one is nighttime`() {
        assertEquals(true, ClusterCardDayNightMode.parseLiveSignalNightMode(1))
    }

    @Test
    fun `live light signal value two is daytime`() {
        assertEquals(false, ClusterCardDayNightMode.parseLiveSignalNightMode(2))
    }

    @Test
    fun `invalid live light signal permits fallback`() {
        assertNull(ClusterCardDayNightMode.parseLiveSignalNightMode(null))
        assertNull(ClusterCardDayNightMode.parseLiveSignalNightMode(0))
        assertNull(ClusterCardDayNightMode.parseLiveSignalNightMode(Int.MIN_VALUE))
    }
}
