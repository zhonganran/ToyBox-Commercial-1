package com.byd.wjx.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopOverlayVisibilityDecisionTest {
    @Test
    fun triggerChangeAlone_doesNotChangeVisibilityState() {
        val previous = DesktopOverlayVisibilityDecision(true, "desktop", "a11y")
        val next = DesktopOverlayVisibilityDecision(true, "desktop", "foreground")

        assertFalse(visibilityStateChanged(previous, next))
    }

    @Test
    fun visibleOrReasonChange_changesVisibilityState() {
        val previous = DesktopOverlayVisibilityDecision(true, "desktop", "a11y")

        assertTrue(
            visibilityStateChanged(
                previous,
                DesktopOverlayVisibilityDecision(false, "off_desktop", "foreground"),
            ),
        )
        assertTrue(
            visibilityStateChanged(
                previous,
                DesktopOverlayVisibilityDecision(true, "desktop_hold", "foreground"),
            ),
        )
    }
}
