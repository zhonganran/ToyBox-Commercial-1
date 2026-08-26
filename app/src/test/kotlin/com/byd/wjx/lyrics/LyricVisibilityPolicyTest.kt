package com.byd.wjx.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricVisibilityPolicyTest {
    @Test
    fun `camera keeps desktop lyrics visible`() {
        assertTrue(
            shouldKeepDesktopLyricsVisible(
                allowOnOtherApps = false,
                homeForeground = false,
                cameraActive = true,
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
            ),
        )
    }

    @Test
    fun `activation lock hides both lyric targets`() {
        val targets = resolveLyricOverlayTargets(
            lyricsAllowed = false,
            desktopEnabled = true,
            clusterEnabled = true,
            allowOnOtherApps = true,
            homeForeground = true,
            cameraActive = true,
        )

        assertFalse(targets.desktop)
        assertFalse(targets.cluster)
        assertFalse(targets.any)
    }

    @Test
    fun `cluster visibility is independent from desktop foreground policy`() {
        val targets = resolveLyricOverlayTargets(
            lyricsAllowed = true,
            desktopEnabled = true,
            clusterEnabled = true,
            allowOnOtherApps = false,
            homeForeground = false,
            cameraActive = false,
        )

        assertFalse(targets.desktop)
        assertTrue(targets.cluster)
        assertTrue(targets.any)
    }

    @Test
    fun `permission bootstrap retry is bounded`() {
        assertEquals(5_000L, LyricPermissionRetryPolicy.delayAfterFailure(1))
        assertEquals(15_000L, LyricPermissionRetryPolicy.delayAfterFailure(2))
        assertEquals(30_000L, LyricPermissionRetryPolicy.delayAfterFailure(3))
        assertNull(LyricPermissionRetryPolicy.delayAfterFailure(4))
    }
}
