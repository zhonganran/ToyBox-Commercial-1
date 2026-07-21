package com.byd.wjx.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRangeEstimatorTest {
    @Test
    fun `dynamic electric estimate wins consumption fallback`() {
        val estimate = HomeRangeEstimator.estimate(
            dynamicElectricKm = 318.6,
            remainingBatteryKwh = 40.0,
            electricConsumptionPer100Km = 20.0,
            remainingFuelLiters = 30.0,
            fuelConsumptionPer100Km = 6.0,
        )

        assertEquals(319, estimate.electricKm)
        assertEquals(500, estimate.fuelKm)
        assertEquals(819, estimate.combinedKm)
    }

    @Test
    fun `estimates change with remaining energy and consumption`() {
        val first = HomeRangeEstimator.estimate(null, 36.0, 18.0, 24.0, 6.0)
        val second = HomeRangeEstimator.estimate(null, 32.0, 20.0, 20.0, 8.0)

        assertEquals(200, first.electricKm)
        assertEquals(400, first.fuelKm)
        assertEquals(160, second.electricKm)
        assertEquals(250, second.fuelKm)
    }

    @Test
    fun `invalid consumption never creates a fixed or unbounded estimate`() {
        val estimate = HomeRangeEstimator.estimate(null, 36.0, 0.0, 24.0, 100.0)

        assertNull(estimate.electricKm)
        assertNull(estimate.fuelKm)
        assertNull(estimate.combinedKm)
    }
}
