package com.byd.wjx.camera.blindspot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindSpotPreferencesTest {
    @Test
    fun `rear camera defaults preserve donor side calibration`() {
        val left = BlindSpotPreferences.defaultCalibration(BlindSpotSide.LEFT)
        val right = BlindSpotPreferences.defaultCalibration(BlindSpotSide.RIGHT)

        assertEquals(BlindSpotCrop(7, 19, 64, 61), left.crop(corrected = false))
        assertEquals(BlindSpotCrop(29, 19, 64, 61), right.crop(corrected = false))
        assertEquals(BlindSpotCrop(18, 19, 64, 61), left.crop(corrected = true))
        assertEquals(BlindSpotCrop(18, 19, 64, 61), right.crop(corrected = true))
        assertEquals(-30, left.rotationDegrees)
        assertEquals(30, right.rotationDegrees)
        assertTrue(left.mirrorHorizontally)
        assertTrue(right.mirrorHorizontally)
    }

    @Test
    fun `raw and corrected crops update independently`() {
        val initial = BlindSpotPreferences.defaultCalibration(BlindSpotSide.LEFT)
        val changed = initial.withCrop(
            corrected = true,
            crop = BlindSpotCrop(20, 20, 60, 60),
        )

        assertEquals(initial.crop(corrected = false), changed.crop(corrected = false))
        assertEquals(BlindSpotCrop(20, 20, 60, 60), changed.crop(corrected = true))
    }

    @Test
    fun `cluster fallback uses bounded center display geometry`() {
        val left = BlindSpotPreferences.defaultSideConfig(BlindSpotSide.LEFT)
        val fallback = blindSpotDisplayGeometry(left, useCluster = false)

        assertEquals(45, fallback.widthPct)
        assertEquals(50, fallback.heightPct)
        assertEquals(0, fallback.xPct)
        assertEquals(BlindSpotPreferences.POSITION_SCALE / 2, fallback.yPct)
        assertFalse(fallback == left)
        assertEquals(left, blindSpotDisplayGeometry(left, useCluster = true))
    }
}
