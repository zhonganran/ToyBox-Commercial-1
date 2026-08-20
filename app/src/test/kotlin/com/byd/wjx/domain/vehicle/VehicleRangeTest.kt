package com.byd.wjx.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleRangeTest {
    @Test
    fun `total uses every available vehicle reported range`() {
        assertEquals(890, totalVehicleRangeKm(188, 702))
        assertEquals(188, totalVehicleRangeKm(188, null))
        assertEquals(702, totalVehicleRangeKm(null, 702))
        assertNull(totalVehicleRangeKm(null, null))
    }
}
