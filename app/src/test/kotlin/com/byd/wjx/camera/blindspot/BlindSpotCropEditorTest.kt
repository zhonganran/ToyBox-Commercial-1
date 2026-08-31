package com.byd.wjx.camera.blindspot

import org.junit.Assert.assertEquals
import org.junit.Test

class BlindSpotCropEditorTest {
    @Test
    fun `move keeps crop inside the image`() {
        val crop = BlindSpotCrop(leftPct = 20, topPct = 25, widthPct = 60, heightPct = 50)

        assertEquals(
            BlindSpotCrop(40, 50, 60, 50),
            adjustBlindSpotCrop(crop, BlindSpotCropGesture.MOVE, 80, 80),
        )
        assertEquals(
            BlindSpotCrop(0, 0, 60, 50),
            adjustBlindSpotCrop(crop, BlindSpotCropGesture.MOVE, -80, -80),
        )
    }

    @Test
    fun `corner resize respects minimum size and image bounds`() {
        val crop = BlindSpotCrop(leftPct = 20, topPct = 20, widthPct = 60, heightPct = 60)

        assertEquals(
            BlindSpotCrop(70, 70, 10, 10),
            adjustBlindSpotCrop(crop, BlindSpotCropGesture.TOP_LEFT, 90, 90),
        )
        assertEquals(
            BlindSpotCrop(20, 20, 80, 80),
            adjustBlindSpotCrop(crop, BlindSpotCropGesture.BOTTOM_RIGHT, 90, 90),
        )
    }

    @Test
    fun `opposite edges stay fixed while resizing`() {
        val crop = BlindSpotCrop(leftPct = 25, topPct = 30, widthPct = 40, heightPct = 35)

        assertEquals(
            BlindSpotCrop(15, 30, 50, 45),
            adjustBlindSpotCrop(crop, BlindSpotCropGesture.BOTTOM_LEFT, -10, 10),
        )
        assertEquals(
            BlindSpotCrop(25, 20, 50, 45),
            adjustBlindSpotCrop(crop, BlindSpotCropGesture.TOP_RIGHT, 10, -10),
        )
    }
}
