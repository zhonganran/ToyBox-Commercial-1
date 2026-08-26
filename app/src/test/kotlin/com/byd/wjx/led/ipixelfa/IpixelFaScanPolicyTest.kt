package com.byd.wjx.led.ipixelfa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class IpixelFaScanPolicyTest {
    @Test
    fun `advertised FA service is accepted without relying on the device name`() {
        assertTrue(
            IpixelFaScanPolicy.accepts(
                name = "",
                serviceUuids = setOf(IpixelFaProtocol.serviceUuid),
            ),
        )
        assertEquals("标准 FA 设备", IpixelFaScanPolicy.displayName(""))
    }

    @Test
    fun `name fallback is used only when the advertisement has no service list`() {
        assertTrue(IpixelFaScanPolicy.accepts("IPIXEL-01", emptySet()))
        assertTrue(IpixelFaScanPolicy.accepts("Rear Pixel", emptySet()))
        assertTrue(IpixelFaScanPolicy.accepts("Vehicle LED", emptySet()))
        assertFalse(IpixelFaScanPolicy.accepts("Unknown", emptySet()))

        val foreignService = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        assertFalse(IpixelFaScanPolicy.accepts("Vehicle LED", setOf(foreignService)))
    }
}
