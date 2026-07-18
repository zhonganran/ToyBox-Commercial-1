package com.byd.wjx.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDoorSpeedRuleTest {
    @Test
    fun `opening is blocked from one kilometer per hour`() {
        assertFalse(isAutoDoorCommandBlockedBySpeed(commandValue = 1, speedKmh = 0.99f, defaultLimitKmh = 3f))
        assertTrue(isAutoDoorCommandBlockedBySpeed(commandValue = 1, speedKmh = 1f, defaultLimitKmh = 3f))
        assertTrue(isAutoDoorCommandBlockedBySpeed(commandValue = 1, speedKmh = -1.2f, defaultLimitKmh = 3f))
        assertEquals(1f, autoDoorSpeedLimitKmh(commandValue = 1, defaultLimitKmh = 3f))
    }

    @Test
    fun `closing keeps the existing three kilometer per hour limit`() {
        assertFalse(isAutoDoorCommandBlockedBySpeed(commandValue = 2, speedKmh = 2.99f, defaultLimitKmh = 3f))
        assertTrue(isAutoDoorCommandBlockedBySpeed(commandValue = 2, speedKmh = 3f, defaultLimitKmh = 3f))
        assertEquals(3f, autoDoorSpeedLimitKmh(commandValue = 2, defaultLimitKmh = 3f))
    }

    @Test
    fun `desktop toggle reverses a moving door and follows settled position`() {
        assertEquals(2, resolveAutoDoorToggleCommand(action = 2, percent = 45, fallbackCommand = 3))
        assertEquals(1, resolveAutoDoorToggleCommand(action = 3, percent = 45, fallbackCommand = 3))
        assertEquals(2, resolveAutoDoorToggleCommand(action = null, percent = 100, fallbackCommand = 1))
        assertEquals(1, resolveAutoDoorToggleCommand(action = null, percent = 2, fallbackCommand = 2))
        assertEquals(3, resolveAutoDoorToggleCommand(action = null, percent = 50, fallbackCommand = 3))
    }
}
