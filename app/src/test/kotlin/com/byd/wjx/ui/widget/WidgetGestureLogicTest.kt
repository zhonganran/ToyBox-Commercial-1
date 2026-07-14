package com.byd.wjx.ui.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGestureLogicTest {

    @Test
    fun `left zone uses supplied fraction`() {
        assertTrue(WidgetGestureLogic.isLeftZone(eventX = 99f, viewWidth = 300, fraction = 1f / 3f))
        assertFalse(WidgetGestureLogic.isLeftZone(eventX = 101f, viewWidth = 300, fraction = 1f / 3f))
    }

    @Test
    fun `double tap window requires a previous tap`() {
        assertFalse(WidgetGestureLogic.isWithinDoubleTapWindow(prevUpMs = 0, nowMs = 1000, windowMs = 260))
        assertTrue(WidgetGestureLogic.isWithinDoubleTapWindow(prevUpMs = 1000, nowMs = 1260, windowMs = 260))
        assertFalse(WidgetGestureLogic.isWithinDoubleTapWindow(prevUpMs = 1000, nowMs = 1261, windowMs = 260))
    }
}
