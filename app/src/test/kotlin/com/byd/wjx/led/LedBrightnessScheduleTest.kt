package com.byd.wjx.led

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedBrightnessScheduleTest {
    @Test
    fun `day range uses day brightness and other time uses night brightness`() {
        val schedule = LedBrightnessSchedule(
            enabled = true,
            dayStartMinute = 7 * 60,
            nightStartMinute = 19 * 60,
            dayBrightness = 80,
            nightBrightness = 20,
        )

        assertEquals(20, schedule.brightnessAt(6 * 60 + 59, 50))
        assertEquals(80, schedule.brightnessAt(7 * 60, 50))
        assertEquals(80, schedule.brightnessAt(18 * 60 + 59, 50))
        assertEquals(20, schedule.brightnessAt(19 * 60, 50))
    }

    @Test
    fun `cross midnight range remains supported`() {
        val schedule = LedBrightnessSchedule(
            enabled = true,
            dayStartMinute = 18 * 60,
            nightStartMinute = 6 * 60,
            dayBrightness = 90,
            nightBrightness = 10,
        )

        assertTrue(schedule.isDaytime(23 * 60))
        assertTrue(schedule.isDaytime(2 * 60))
        assertFalse(schedule.isDaytime(12 * 60))
    }

    @Test
    fun `disabled schedule returns manual brightness`() {
        val schedule = LedBrightnessSchedule(enabled = false, dayBrightness = 90, nightBrightness = 10)

        assertEquals(55, schedule.brightnessAt(12 * 60, 55))
    }

    @Test
    fun `wall clock minute follows supplied timezone`() {
        val utc = TimeZone.getTimeZone("UTC")
        val eightThirtyUtc = 30_600_000L

        assertEquals(8 * 60 + 30, currentMinuteOfDay(eightThirtyUtc, utc))
    }
}
