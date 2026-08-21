package com.byd.wjx.led

import com.byd.wjx.led.a950.A950Preset
import com.byd.wjx.led.a950.A950Protocol
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
    fun `FA restores the stored program list instead of selecting preset six`() {
        assertArrayEquals(
            byteArrayOf(0x04, 0x00, 0x03, 0x80.toByte()),
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
                serviceUuid = A950Protocol.serviceUuid,
                characteristicUuids = setOf(IpixelFaProtocol.writeUuid),
            )
        )
        assertTrue(
            !IpixelFaProtocol.acceptsGattProfile(
                serviceUuid = IpixelFaProtocol.serviceUuid,
                characteristicUuids = setOf(IpixelFaProtocol.notifyUuid),
            )
        )
    }

    @Test
    fun `A950 remote slots use Vivid zero based command codes`() {
        assertEquals((0..19).toList(), A950Preset.entries.map(A950Preset::commandCode))
        assertEquals((1..20).toList(), A950Preset.entries.map(A950Preset::slotNumber))
        assertEquals("请让我先走", A950Preset.LET_ME_GO_FIRST.title)
        assertEquals("开门请注意", A950Preset.OPEN_DOOR_CAREFULLY.title)
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x01, 0x05, 0x02, 0x00, 0xAC.toByte()),
            A950Protocol.playPreset(A950Preset.LEFT_TURN),
        )
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x01, 0x05, 0xFF.toByte(), 0x01, 0xAA.toByte()),
            A950Protocol.enableRemoteControlPacket,
        )
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x01, 0x05, 0xFF.toByte(), 0x00, 0xA9.toByte()),
            A950Protocol.resumeProgramListPacket,
        )
    }

    @Test
    fun `A950 initialization and keepalive packets match firmware protocol`() {
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x01, 0x08, 0x01, 0xAE.toByte()),
            A950Protocol.prepareUploadPacket,
        )
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x01, 0x01, 0x54, 0xFA.toByte()),
            A950Protocol.initializePacket,
        )
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x81.toByte(), 0x01, 0x01, 0x27),
            A950Protocol.keepalivePacket,
        )
    }

}
