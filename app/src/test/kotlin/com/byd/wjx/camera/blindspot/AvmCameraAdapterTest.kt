package com.byd.wjx.camera.blindspot

import org.junit.Assert.assertEquals
import org.junit.Test

class AvmCameraAdapterTest {
    @Test
    fun `aggregate panorama dimensions are divided into one camera channel`() {
        assertEquals(
            AvmPreviewSize(1920, 1300),
            avmPerChannelPreviewSize("pano_h", 7680, 1300),
        )
        assertEquals(
            AvmPreviewSize(960, 650),
            avmPerChannelPreviewSize("pano_l", 3840, 650),
        )
    }

    @Test
    fun `already per channel and non panorama dimensions stay unchanged`() {
        assertEquals(
            AvmPreviewSize(1920, 1300),
            avmPerChannelPreviewSize("pano_h", 1920, 1300),
        )
        assertEquals(
            AvmPreviewSize(1280, 720),
            avmPerChannelPreviewSize("apa", 1280, 720),
        )
    }

    @Test
    fun `invalid camera dimensions use the calibrated channel fallback`() {
        assertEquals(
            AvmPreviewSize(1920, 1300),
            avmPerChannelPreviewSize("pano_h", 0, -1),
        )
    }
}
