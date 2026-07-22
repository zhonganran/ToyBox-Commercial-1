package com.byd.wjx.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricVisibilityPolicyTest {
    @Test
    fun `camera and electronic mirror keep desktop lyrics visible`() {
        assertTrue(
            shouldKeepDesktopLyricsVisible(
                allowOnOtherApps = false,
                homeForeground = false,
                cameraActive = true,
                electronicMirrorActive = false,
            ),
        )
        assertTrue(
            shouldKeepDesktopLyricsVisible(
                allowOnOtherApps = false,
                homeForeground = false,
                cameraActive = false,
                electronicMirrorActive = true,
            ),
        )
    }

    @Test
    fun `ordinary non-home app still respects desktop lyric setting`() {
        assertFalse(
            shouldKeepDesktopLyricsVisible(
                allowOnOtherApps = false,
                homeForeground = false,
                cameraActive = false,
                electronicMirrorActive = false,
            ),
        )
    }
}
