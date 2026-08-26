package com.byd.wjx.ui.dashboard

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardTimeRangesTest {
    private val timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val now = timestamp(2026, Calendar.AUGUST, 26, 15, 30, 45, 123)

    @Test
    fun `today covers the full local calendar day`() {
        assertEquals(
            timestamp(2026, Calendar.AUGUST, 26, 0, 0, 0, 0) to
                timestamp(2026, Calendar.AUGUST, 26, 23, 59, 59, 999),
            DashboardTimeRanges.today(now, timeZone),
        )
    }

    @Test
    fun `rolling periods preserve the existing calendar semantics`() {
        assertEquals(
            timestamp(2026, Calendar.AUGUST, 19, 0, 0, 0, 0) to now,
            DashboardTimeRanges.forPeriod(DashboardPeriod.WEEK, now, timeZone),
        )
        assertEquals(
            timestamp(2026, Calendar.JULY, 27, 0, 0, 0, 0) to now,
            DashboardTimeRanges.forPeriod(DashboardPeriod.MONTH, now, timeZone),
        )
        assertEquals(
            timestamp(2025, Calendar.AUGUST, 26, 0, 0, 0, 0) to now,
            DashboardTimeRanges.forPeriod(DashboardPeriod.YEAR, now, timeZone),
        )
        assertEquals(0L to now, DashboardTimeRanges.forPeriod(DashboardPeriod.ALL, now, timeZone))
    }

    private fun timestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int,
    ): Long = Calendar.getInstance(timeZone).apply {
        clear()
        set(year, month, day, hour, minute, second)
        set(Calendar.MILLISECOND, millis)
    }.timeInMillis
}
