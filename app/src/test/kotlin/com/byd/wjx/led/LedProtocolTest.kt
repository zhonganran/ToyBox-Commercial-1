package com.byd.wjx.led

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedProtocolTest {
    @Test
    fun `firmware preset command uses official Chinese remote-control packet`() {
        assertArrayEquals(
            byteArrayOf(0x06, 0x00, 0x07, 0x80.toByte(), 0x03, 0x01),
            IPixelFaLedProtocol.playScene(LedScene.LEFT_TURN),
        )
    }

    @Test
    fun `scene preset ids match official iPixel remote order`() {
        val expected = mapOf(
            LedScene.DEFAULT to 6,
            LedScene.LEFT_TURN to 3,
            LedScene.RIGHT_TURN to 5,
            LedScene.TEMPORARY_STOP to 4,
            LedScene.REVERSE_PARKING to 11,
            LedScene.AUTOPILOT to 14,
            LedScene.EXIT_VEHICLE to 19,
            LedScene.HAZARD to 20,
            LedScene.BRAKE_WARNING to 8,
        )

        expected.forEach { (scene, presetId) ->
            assertEquals(scene.name, presetId, IPixelFaLedProtocol.presetId(scene))
        }
    }

    @Test
    fun `brightness packet clamps to protocol range`() {
        assertArrayEquals(
            byteArrayOf(0x05, 0x00, 0x04, 0x80.toByte(), 0x64),
            IPixelFaLedProtocol.brightness(120),
        )
    }

    @Test
    fun `A950 scene command uses semantic device mapping and checksum`() {
        val expected = mapOf(
            LedScene.DEFAULT to 5,
            LedScene.LEFT_TURN to 2,
            LedScene.RIGHT_TURN to 3,
            LedScene.TEMPORARY_STOP to 4,
            LedScene.REVERSE_PARKING to 10,
            LedScene.AUTOPILOT to 13,
            LedScene.EXIT_VEHICLE to 18,
            LedScene.HAZARD to 19,
            LedScene.BRAKE_WARNING to 0,
        )

        expected.forEach { (scene, presetId) ->
            assertEquals(scene.name, presetId, A950LedProtocol.presetId(scene))
        }
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x01, 0x05, 0x02, 0xAC.toByte()),
            A950LedProtocol.playScene(LedScene.LEFT_TURN),
        )
    }

    @Test
    fun `A950 initialization and keepalive packets match firmware protocol`() {
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x01, 0x08, 0x01, 0xAE.toByte()),
            A950LedProtocol.prepareUploadPacket,
        )
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x01, 0x01, 0x54, 0xFA.toByte()),
            A950LedProtocol.initializePacket,
        )
        assertArrayEquals(
            byteArrayOf(0x4E, 0x52, 0x04, 0x81.toByte(), 0x01, 0x01, 0x27),
            A950LedProtocol.keepalivePacket,
        )
    }

    @Test
    fun `protocol detection prefers an expected protocol that is actually available`() {
        assertEquals(
            LedProtocolKind.A950,
            detectLedProtocol(
                expected = LedProtocolKind.A950,
                available = LedProtocolKind.entries.toSet(),
            ),
        )
    }

    @Test
    fun `protocol detection automatically accepts one available protocol`() {
        assertEquals(
            LedProtocolKind.IPIXEL_FA,
            detectLedProtocol(expected = null, available = setOf(LedProtocolKind.IPIXEL_FA)),
        )
    }

    @Test
    fun `protocol detection rejects missing or ambiguous services`() {
        assertNull(detectLedProtocol(expected = null, available = emptySet()))
        assertNull(detectLedProtocol(expected = null, available = LedProtocolKind.entries.toSet()))
    }
}
