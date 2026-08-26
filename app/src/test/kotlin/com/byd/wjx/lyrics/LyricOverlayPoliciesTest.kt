package com.byd.wjx.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricOverlayPoliciesTest {
    @Test
    fun `frame direction recognizes forward and backward lyric movement`() {
        val current = LyricFrame(previous = "before", current = "now", next = "after")

        assertEquals(1, lyricAnimationDirection(current, LyricFrame(current = "after")))
        assertEquals(-1, lyricAnimationDirection(current, LyricFrame(current = "before")))
        assertEquals(0, lyricAnimationDirection(current, LyricFrame(current = "elsewhere")))
    }

    @Test
    fun `transition requires adjacent nonblank frames and enabled motion`() {
        val current = LyricFrame(current = "now", next = "after")
        val next = LyricFrame(current = "after")

        assertTrue(shouldAnimateLyricTransition(current, next, 1, 300L, animate = true))
        assertFalse(shouldAnimateLyricTransition(current, next, 1, 0L, animate = true))
        assertFalse(shouldAnimateLyricTransition(current, next, 0, 300L, animate = true))
        assertFalse(shouldAnimateLyricTransition(current, next, 1, 300L, animate = false))
    }

    @Test
    fun `visible offsets stay bounded and include the entering transition row`() {
        assertEquals(setOf(0), lyricVisibleOffsets(-5))
        assertEquals(setOf(-2, -1, 0, 1, 2), lyricVisibleOffsets(99))
        assertEquals(setOf(-1, 0, 1, 2), lyricTransitionOffsets(3, true, 1))
        assertEquals(setOf(-2, -1, 0, 1), lyricTransitionOffsets(3, true, -1))
    }

    @Test
    fun `easing clamps input and preserves endpoints`() {
        assertEquals(0f, lyricEase(-1f, "sine"), 0.001f)
        assertEquals(1f, lyricEase(2f, "sine"), 0.001f)
    }
}
