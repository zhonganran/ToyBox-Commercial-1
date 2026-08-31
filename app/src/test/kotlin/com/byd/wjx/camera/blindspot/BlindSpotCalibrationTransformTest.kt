package com.byd.wjx.camera.blindspot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BlindSpotCalibrationTransformTest {
    @Test
    fun `matching source and window produce identity transform`() {
        val values = values(
            sourceWidth = 1920f,
            sourceHeight = 1080f,
            calibration = BlindSpotPreferences.defaultCalibration(),
        )

        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            values,
            0.0001f,
        )
    }

    @Test
    fun `fill preserves source aspect and crops excess height`() {
        val values = values(
            sourceWidth = 1920f,
            sourceHeight = 1300f,
            calibration = BlindSpotPreferences.defaultCalibration(),
        )

        assertEquals(1f, values[0], 0.0001f)
        assertEquals(1300f / 1080f, values[4], 0.0001f)
        assertEquals((1080f - 1300f) / 2f, values[5], 0.0001f)
    }

    @Test
    fun `fit letterboxes wider output without distorting source`() {
        val values = values(
            sourceWidth = 1920f,
            sourceHeight = 1300f,
            calibration = BlindSpotPreferences.defaultCalibration().copy(
                scaleMode = BlindSpotScaleMode.FIT,
            ),
        )

        assertEquals(1080f / 1300f, values[0], 0.0001f)
        assertEquals(1f, values[4], 0.0001f)
        assertEquals((1920f - 1920f * 1080f / 1300f) / 2f, values[2], 0.001f)
    }

    @Test
    fun `horizontal mirror flips around window center`() {
        val values = values(
            sourceWidth = 1920f,
            sourceHeight = 1080f,
            calibration = BlindSpotPreferences.defaultCalibration().copy(
                mirrorHorizontally = true,
            ),
        )

        assertEquals(-1f, values[0], 0.0001f)
        assertEquals(1920f, values[2], 0.0001f)
    }

    @Test
    fun `invalid dimensions do not produce a matrix`() {
        assertNull(
            blindSpotTransformValues(
                0,
                1080,
                1920f,
                1300f,
                BlindSpotPreferences.defaultCalibration(),
            ),
        )
    }

    @Test
    fun `crop editor shows the full coordinate space without output transforms`() {
        val calibration = BlindSpotPreferences.defaultCalibration().copy(
            cropLeftPct = 12,
            cropTopPct = 8,
            cropWidthPct = 70,
            cropHeightPct = 75,
            correctedCropLeftPct = 20,
            correctedCropTopPct = 15,
            correctedCropWidthPct = 60,
            correctedCropHeightPct = 65,
            rotationDegrees = 25,
            mirrorHorizontally = true,
            scaleMode = BlindSpotScaleMode.FIT,
            dewarpEnabled = true,
            dewarpFovDegrees = 125,
            dewarpCenterXPct = 44,
            dewarpCenterYPct = 57,
        )

        val preview = blindSpotPreviewCalibration(
            calibration = calibration,
            editingCrop = true,
            corrected = true,
        )

        assertEquals(BlindSpotCrop(0, 0, 100, 100), preview.crop(corrected = true))
        assertEquals(calibration.crop(corrected = false), preview.crop(corrected = false))
        assertEquals(0, preview.rotationDegrees)
        assertFalse(preview.mirrorHorizontally)
        assertEquals(BlindSpotScaleMode.STRETCH, preview.scaleMode)
        assertEquals(125, preview.dewarpFovDegrees)
        assertEquals(44, preview.dewarpCenterXPct)
        assertEquals(57, preview.dewarpCenterYPct)
    }

    @Test
    fun `normal preview uses the persisted calibration unchanged`() {
        val calibration = BlindSpotPreferences.defaultCalibration()

        assertSame(
            calibration,
            blindSpotPreviewCalibration(calibration, editingCrop = false, corrected = false),
        )
    }

    private fun values(
        sourceWidth: Float,
        sourceHeight: Float,
        calibration: BlindSpotCalibration,
    ): FloatArray = checkNotNull(
        blindSpotTransformValues(1920, 1080, sourceWidth, sourceHeight, calibration),
    )
}
