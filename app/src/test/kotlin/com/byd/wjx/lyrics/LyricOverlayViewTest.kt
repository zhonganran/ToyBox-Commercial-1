package com.byd.wjx.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricOverlayViewTest {
    @Test
    fun `horizontal position pins visible lyric bounds to both edges`() {
        val left = LyricOverlayView.horizontalCenterFor(
            containerWidth = 420,
            paddingLeft = 0,
            paddingRight = 0,
            textWidth = 180f,
            positionPct = 0f,
        )
        val center = LyricOverlayView.horizontalCenterFor(
            containerWidth = 420,
            paddingLeft = 0,
            paddingRight = 0,
            textWidth = 180f,
            positionPct = 50f,
        )
        val right = LyricOverlayView.horizontalCenterFor(
            containerWidth = 420,
            paddingLeft = 0,
            paddingRight = 0,
            textWidth = 180f,
            positionPct = 100f,
        )

        assertEquals(90f, left, 0.001f)
        assertEquals(210f, center, 0.001f)
        assertEquals(330f, right, 0.001f)
    }

    @Test
    fun `horizontal position clamps values outside the slider range`() {
        assertEquals(
            LyricOverlayView.horizontalCenterFor(420, 12, 8, 180f, 0f),
            LyricOverlayView.horizontalCenterFor(420, 12, 8, 180f, -50f),
            0.001f,
        )
        assertEquals(
            LyricOverlayView.horizontalCenterFor(420, 12, 8, 180f, 100f),
            LyricOverlayView.horizontalCenterFor(420, 12, 8, 180f, 150f),
            0.001f,
        )
    }
}
