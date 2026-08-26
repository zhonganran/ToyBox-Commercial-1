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

    @Test
    fun `safety checks fail closed in vehicle order`() {
        assertEquals(
            "无法确认车速",
            autoDoorSafetyBlockReason(1, null, slideReady = 1, coverState = 1, defaultLimitKmh = 3f),
        )
        assertEquals(
            "开门要求车速低于 1km/h（当前 1.00km/h）",
            autoDoorSafetyBlockReason(1, 1f, slideReady = 1, coverState = 1, defaultLimitKmh = 3f),
        )
        assertEquals(
            "无法确认电滑门准备状态",
            autoDoorSafetyBlockReason(2, 0f, slideReady = null, coverState = 1, defaultLimitKmh = 3f),
        )
        assertEquals(
            "电滑门未就绪，官方要求 BODYWORK_ELEC_SLIDE_STATE=1",
            autoDoorSafetyBlockReason(2, 0f, slideReady = 0, coverState = 1, defaultLimitKmh = 3f),
        )
        assertEquals(
            "无法确认同侧充电口盖状态",
            autoDoorSafetyBlockReason(2, 0f, slideReady = 1, coverState = null, defaultLimitKmh = 3f),
        )
        assertEquals(
            "同侧充电口盖未关闭，官方会拦截",
            autoDoorSafetyBlockReason(2, 0f, slideReady = 1, coverState = 0, defaultLimitKmh = 3f),
        )
        assertEquals(
            null,
            autoDoorSafetyBlockReason(2, 0f, slideReady = 1, coverState = 1, defaultLimitKmh = 3f),
        )
    }
}
