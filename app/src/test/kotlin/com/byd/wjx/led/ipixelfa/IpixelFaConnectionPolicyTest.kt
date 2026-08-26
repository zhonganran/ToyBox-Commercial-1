package com.byd.wjx.led.ipixelfa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpixelFaConnectionPolicyTest {
    private val enabled = IpixelFaPreferenceSnapshot(
        enabled = true,
        deviceAddress = "FA:00:11:22:33:44",
        deviceName = "IPIXEL FA",
    )

    @Test
    fun `reconnect delay follows the original capped backoff`() {
        assertEquals(1_000L, IpixelFaReconnectPolicy.delayForAttempt(0))
        assertEquals(2_000L, IpixelFaReconnectPolicy.delayForAttempt(1))
        assertEquals(5_000L, IpixelFaReconnectPolicy.delayForAttempt(2))
        assertEquals(10_000L, IpixelFaReconnectPolicy.delayForAttempt(3))
        assertEquals(30_000L, IpixelFaReconnectPolicy.delayForAttempt(4))
        assertEquals(30_000L, IpixelFaReconnectPolicy.delayForAttempt(99))
    }

    @Test
    fun `reconnect is allowed only for the enabled bound device`() {
        assertTrue(IpixelFaReconnectPolicy.canSchedule(false, enabled, enabled.deviceAddress.lowercase()))
        assertFalse(IpixelFaReconnectPolicy.canSchedule(true, enabled, enabled.deviceAddress))
        assertFalse(IpixelFaReconnectPolicy.canSchedule(false, enabled.copy(enabled = false), enabled.deviceAddress))
        assertFalse(IpixelFaReconnectPolicy.canSchedule(false, enabled.copy(deviceAddress = ""), enabled.deviceAddress))
        assertFalse(IpixelFaReconnectPolicy.canSchedule(false, enabled, "FA:99:88:77:66:55"))
    }

    @Test
    fun `pending reconnect stops after disabling or rebinding`() {
        assertTrue(IpixelFaReconnectPolicy.stillTargets(enabled, enabled.deviceAddress.lowercase()))
        assertFalse(IpixelFaReconnectPolicy.stillTargets(enabled.copy(enabled = false), enabled.deviceAddress))
        assertFalse(
            IpixelFaReconnectPolicy.stillTargets(
                enabled.copy(deviceAddress = "FA:99:88:77:66:55"),
                enabled.deviceAddress,
            ),
        )
    }
}
