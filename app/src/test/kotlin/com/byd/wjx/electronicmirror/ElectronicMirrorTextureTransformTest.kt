package com.byd.wjx.electronicmirror

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorTextureTransformTest {
    @Test
    fun `default source quad preserves the original texture`() {
        val source = electronicMirrorSourceQuad(
            ElectronicMirrorCalibration.DEFAULT,
            width = 1280f,
            height = 480f,
        )

        assertArrayEquals(
            floatArrayOf(0f, 0f, 1280f, 0f, 1280f, 480f, 0f, 480f),
            source,
            0.001f,
        )
    }

    @Test
    fun `positive yaw pans crop toward the right without leaving the frame`() {
        val source = electronicMirrorSourceQuad(
            ElectronicMirrorCalibration(yawDegrees = 15),
            width = 1000f,
            height = 1000f,
        )

        assertTrue(source[0] > 0f)
        assertTrue(source[2] <= 1000f)
        assertTrue(source[2] > source[0])
        assertInsideFrame(source, 1000f, 1000f)
    }

    @Test
    fun `negative pitch pans crop upward and roll stays inside frame`() {
        val source = electronicMirrorSourceQuad(
            ElectronicMirrorCalibration(pitchDegrees = -15, rollDegrees = 15),
            width = 1280f,
            height = 480f,
        )

        assertInsideFrame(source, 1280f, 480f)
        assertTrue(source[1] < source[7])
        assertTrue(source[3] < source[5])
    }

    private fun assertInsideFrame(source: FloatArray, width: Float, height: Float) {
        source.forEachIndexed { index, value ->
            if (index % 2 == 0) assertTrue(value in 0f..width)
            else assertTrue(value in 0f..height)
        }
    }
}
