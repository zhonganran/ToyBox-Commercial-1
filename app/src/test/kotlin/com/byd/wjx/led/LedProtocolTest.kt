package com.byd.wjx.led

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LedProtocolTest {
    @Test
    fun `firmware preset command uses official Chinese remote-control packet`() {
        assertArrayEquals(
            byteArrayOf(0x06, 0x00, 0x07, 0x80.toByte(), 0x03, 0x01),
            LedProtocol.playScene(LedScene.LEFT_TURN),
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
            LedScene.KEEP_DISTANCE to 8,
        )

        expected.forEach { (scene, presetId) ->
            assertEquals(scene.name, presetId, scene.remotePresetId)
        }
    }

    @Test
    fun `brightness packet clamps to protocol range`() {
        assertArrayEquals(
            byteArrayOf(0x05, 0x00, 0x04, 0x80.toByte(), 0x64),
            LedProtocol.brightness(120),
        )
    }
}
