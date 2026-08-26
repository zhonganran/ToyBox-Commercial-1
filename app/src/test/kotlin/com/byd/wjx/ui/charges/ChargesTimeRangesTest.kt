package com.byd.wjx.ui.charges

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargesTimeRangesTest {
    private val timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val now = timestamp(2026, Calendar.AUGUST, 26, 15, 30, 45)

    @Test
    fun `periods follow local day and calendar boundaries`() {
        assertEquals(
            timestamp(2026, Calendar.AUGUST, 26) to now,
            ChargesTimeRanges.forPeriod(ChargesPeriod.TODAY, now, timeZone),
        )
        assertEquals(
            timestamp(2026, Calendar.AUGUST, 19) to now,
            ChargesTimeRanges.forPeriod(ChargesPeriod.WEEK, now, timeZone),
        )
        assertEquals(
            timestamp(2026, Calendar.AUGUST, 1) to now,
            ChargesTimeRanges.forPeriod(ChargesPeriod.MONTH, now, timeZone),
        )
        assertEquals(
            timestamp(2026, Calendar.JANUARY, 1) to now,
            ChargesTimeRanges.forPeriod(ChargesPeriod.YEAR, now, timeZone),
        )
        assertEquals(0L to now, ChargesTimeRanges.forPeriod(ChargesPeriod.ALL, now, timeZone))
    }

    private fun timestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
    ): Long = Calendar.getInstance(timeZone).apply {
        clear()
        set(year, month, day, hour, minute, second)
    }.timeInMillis
}
