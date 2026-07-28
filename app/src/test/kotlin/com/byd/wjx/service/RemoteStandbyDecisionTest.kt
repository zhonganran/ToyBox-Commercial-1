package com.byd.wjx.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStandbyDecisionTest {
    @Test
    fun `parked vehicle with healthy power enables remote standby`() {
        val decision = decideRemoteStandby(parked = true, socPercent = 65, voltage12v = 12.4)
        assertTrue(decision.enabled)
        assertEquals("parked_remote_audio", decision.reason)
    }

    @Test
    fun `powered vehicle arms remote standby for the next ACC off edge`() {
        val decision = decideRemoteStandby(parked = false, socPercent = 65, voltage12v = 14.1)
        assertTrue(decision.enabled)
        assertEquals("armed_for_acc_off", decision.reason)
    }

    @Test
    fun `low traction battery disables remote standby`() {
        val decision = decideRemoteStandby(parked = true, socPercent = 10, voltage12v = 12.3)
        assertFalse(decision.enabled)
        assertEquals("low_soc", decision.reason)
    }

    @Test
    fun `low 12v battery disables remote standby`() {
        val decision = decideRemoteStandby(parked = true, socPercent = 60, voltage12v = 11.6)
        assertFalse(decision.enabled)
        assertEquals("low_12v", decision.reason)
    }

    @Test
    fun `inactive authorization does not arm remote standby`() {
        val decision = decideRemoteStandby(
            parked = false,
            socPercent = 80,
            voltage12v = 14.2,
            authorized = false,
        )
        assertFalse(decision.enabled)
        assertEquals("authorization_inactive", decision.reason)
    }
}
