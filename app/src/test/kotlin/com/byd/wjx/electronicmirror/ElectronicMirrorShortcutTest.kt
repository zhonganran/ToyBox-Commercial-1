package com.byd.wjx.electronicmirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorShortcutTest {
    @Test
    fun `first shortcut press enables and shows streaming rear mirror`() {
        val next = ElectronicMirrorConfig(
            streamingRearEnabled = false,
            streamingRearVisible = false,
        ).toggledStreamingRearFromShortcut()

        assertTrue(next.streamingRearEnabled)
        assertTrue(next.streamingRearVisible)
    }

    @Test
    fun `following shortcut presses alternate visible state`() {
        val visible = ElectronicMirrorConfig(
            streamingRearEnabled = true,
            streamingRearVisible = true,
        )

        val hidden = visible.toggledStreamingRearFromShortcut()
        assertTrue(hidden.streamingRearEnabled)
        assertFalse(hidden.streamingRearVisible)

        val shownAgain = hidden.toggledStreamingRearFromShortcut()
        assertTrue(shownAgain.streamingRearVisible)
    }
}
