package com.byd.wjx.ui.lyrics

import com.byd.wjx.lyrics.LyricSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricDisplayStateTest {
    @Test
    fun `enabling requires activation before overlay permission`() {
        assertEquals(
            LyricModeChangeResult.ACTIVATION_REQUIRED,
            decideLyricModeChange(
                enabled = true,
                featureAllowed = false,
                overlayPermissionGranted = false,
            ),
        )
        assertEquals(
            LyricModeChangeResult.OVERLAY_PERMISSION_REQUIRED,
            decideLyricModeChange(
                enabled = true,
                featureAllowed = true,
                overlayPermissionGranted = false,
            ),
        )
        assertEquals(
            LyricModeChangeResult.APPLIED,
            decideLyricModeChange(
                enabled = true,
                featureAllowed = true,
                overlayPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `disabling remains available without activation or overlay permission`() {
        assertEquals(
            LyricModeChangeResult.APPLIED,
            decideLyricModeChange(
                enabled = false,
                featureAllowed = false,
                overlayPermissionGranted = false,
            ),
        )
    }

    @Test
    fun `panel state keeps persisted switches but gates active presentation`() {
        val adjustment = LyricAdjustmentState(
            lineCount = LyricSettings.LINE_COUNT_DEFAULT,
            fontScale = 1f,
            textColor = LyricSettings.DEFAULT_TEXT_COLOR,
            highlightColor = LyricSettings.DEFAULT_HIGHLIGHT_COLOR,
            fontStyleId = "soft",
            motionPresetId = "soft",
            syncOffset = 0f,
        )
        val state = LyricDisplayUiState(
            featureAllowed = false,
            desktopEnabled = true,
            clusterEnabled = true,
            desktopAdjustment = adjustment,
            clusterAdjustment = adjustment,
        )

        assertTrue(state.anyConfigured)
        assertFalse(state.panelState().desktopActive)
        assertFalse(state.panelState().clusterActive)
    }
}
