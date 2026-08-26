package com.byd.wjx.led.mafu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MafuConnectionPolicyTest {
    private val enabled = MafuPreferenceSnapshot(
        enabled = true,
        deviceAddress = "AA:BB:CC:DD:EE:FF",
        deviceName = "YS-64X32",
    )

    @Test
    fun `reconnect is scheduled only for the enabled bound device`() {
        assertTrue(
            MafuReconnectPolicy.canSchedule(
                manualDisconnect = false,
                preferences = enabled,
                disconnectedAddress = "aa:bb:cc:dd:ee:ff",
            ),
        )
        assertFalse(MafuReconnectPolicy.canSchedule(true, enabled, enabled.deviceAddress))
        assertFalse(MafuReconnectPolicy.canSchedule(false, enabled.copy(enabled = false), enabled.deviceAddress))
        assertFalse(MafuReconnectPolicy.canSchedule(false, enabled.copy(deviceAddress = ""), enabled.deviceAddress))
        assertFalse(MafuReconnectPolicy.canSchedule(false, enabled, "11:22:33:44:55:66"))
    }

    @Test
    fun `pending reconnect stops when the binding changes`() {
        assertTrue(MafuReconnectPolicy.stillTargets(enabled, enabled.deviceAddress.lowercase()))
        assertFalse(
            MafuReconnectPolicy.stillTargets(
                enabled.copy(deviceAddress = "11:22:33:44:55:66"),
                enabled.deviceAddress,
            ),
        )
        assertFalse(MafuReconnectPolicy.stillTargets(enabled.copy(enabled = false), enabled.deviceAddress))
    }
}
