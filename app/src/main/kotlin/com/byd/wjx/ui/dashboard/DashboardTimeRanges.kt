package com.byd.wjx.ui.dashboard

import java.util.Calendar
import java.util.TimeZone

internal object DashboardTimeRanges {
    fun forPeriod(
        period: DashboardPeriod,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Pair<Long, Long> = when (period) {
        DashboardPeriod.TODAY -> today(nowMs, timeZone)
        DashboardPeriod.WEEK -> rollingStart(nowMs, Calendar.DAY_OF_YEAR, -7, timeZone) to nowMs
        DashboardPeriod.MONTH -> rollingStart(nowMs, Calendar.DAY_OF_YEAR, -30, timeZone) to nowMs
        DashboardPeriod.YEAR -> rollingStart(nowMs, Calendar.YEAR, -1, timeZone) to nowMs
        DashboardPeriod.ALL -> 0L to nowMs
    }

    fun today(
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Pair<Long, Long> {
        val calendar = calendarAt(nowMs, timeZone)
        calendar.startOfDay()
        val start = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return start to calendar.timeInMillis
    }

    private fun rollingStart(
        nowMs: Long,
        field: Int,
        amount: Int,
        timeZone: TimeZone,
    ): Long = calendarAt(nowMs, timeZone).apply {
        add(field, amount)
        startOfDay()
    }.timeInMillis

    private fun calendarAt(nowMs: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }

    private fun Calendar.startOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
