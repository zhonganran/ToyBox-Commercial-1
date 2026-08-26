package com.byd.wjx.led

import com.byd.wjx.led.ipixelfa.IpixelFaProtocol
import com.byd.wjx.led.ipixelfa.IpixelFaPreset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedProtocolTest {
    @Test
    fun `firmware preset command uses official Chinese remote-control packet`() {
        assertArrayEquals(
            byteArrayOf(0x06, 0x00, 0x07, 0x80.toByte(), 0x03, 0x01),
            IpixelFaProtocol.playPreset(IpixelFaPreset.TURN_LEFT),
        )
    }

    @Test
    fun `FA enters program mode immediately with every stored program slot`() {
        assertArrayEquals(
            byteArrayOf(
                0x0F,
                0x00,
                0x08,
                0x80.toByte(),
                0x09,
                0x00,
                0x01,
                0x02,
                0x03,
                0x04,
                0x05,
                0x06,
                0x07,
                0x08,
                0x09,
            ),
            IpixelFaProtocol.playProgramList(),
        )
    }

    @Test
    fun `scene preset ids match official iPixel remote order`() {
        assertEquals((1..20).toList(), IpixelFaPreset.entries.map(IpixelFaPreset::presetId))
        assertEquals("紧急情况", IpixelFaPreset.EMERGENCY.title)
        assertEquals("双闪危险报警", IpixelFaPreset.HAZARD_WARNING.title)
    }

    @Test
    fun `brightness packet clamps to protocol range`() {
        assertArrayEquals(
            byteArrayOf(0x05, 0x00, 0x04, 0x80.toByte(), 0x64),
            IpixelFaProtocol.brightness(120),
        )
    }

    @Test
    fun `FA accepts only its own service with its write characteristic`() {
        assertTrue(
            IpixelFaProtocol.acceptsGattProfile(
                serviceUuid = IpixelFaProtocol.serviceUuid,
                characteristicUuids = setOf(IpixelFaProtocol.writeUuid, IpixelFaProtocol.notifyUuid),
            )
        )
        assertTrue(
            !IpixelFaProtocol.acceptsGattProfile(
                serviceUuid = IpixelFaProtocol.serviceUuid,
                characteristicUuids = setOf(IpixelFaProtocol.notifyUuid),
            )
        )
    }

}
