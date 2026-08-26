package com.byd.wjx.data.charging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingCatchUpPoliciesTest {
    @Test
    fun smallSocRiseUsesTrustedAndPendingTiers() {
        assertTrue(shouldRejectCatchUpCharge(1, odometerMoved = false, chargePending = false))
        assertTrue(shouldRejectCatchUpCharge(2, odometerMoved = true, chargePending = false))
        assertFalse(shouldRejectCatchUpCharge(2, odometerMoved = true, chargePending = true))
        assertFalse(shouldRejectCatchUpCharge(3, odometerMoved = true, chargePending = false))
    }

    @Test
    fun energyResolverFallsBackWhenBmsCounterDiverges() {
        val sane = resolveCatchUpEnergy(14.0, 7.0, 10, 70.0, 100.0)
        val divergent = resolveCatchUpEnergy(30.0, 0.0, 10, 70.0, 100.0)

        assertEquals(7.0, sane.kwh, 0.001)
        assertEquals("autoservice_cap_delta", sane.source)
        assertEquals(7.0, divergent.kwh, 0.001)
        assertEquals("autoservice_soc_fallback", divergent.source)
    }

    @Test
    fun elapsedTimeUsesFallbackAndBounds() {
        assertEquals(1.0, catchUpElapsedHours(1_000L, 0L), 0.001)
        assertEquals(0.05, catchUpElapsedHours(1_000L, 999L), 0.001)
        assertEquals(16.0, catchUpElapsedHours(100_000_000L, 1L), 0.001)
    }
}
