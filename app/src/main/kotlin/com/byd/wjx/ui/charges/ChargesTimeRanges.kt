package com.byd.wjx.ui.charges

import java.util.Calendar
import java.util.TimeZone

internal object ChargesTimeRanges {
    fun forPeriod(
        period: ChargesPeriod,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Pair<Long, Long> {
        if (period == ChargesPeriod.ALL) return 0L to nowMs
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }
        when (period) {
            ChargesPeriod.TODAY -> Unit
            ChargesPeriod.WEEK -> calendar.add(Calendar.DAY_OF_YEAR, -7)
            ChargesPeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            ChargesPeriod.YEAR -> calendar.set(Calendar.DAY_OF_YEAR, 1)
            ChargesPeriod.ALL -> error("handled above")
        }
        calendar.startOfDay()
        return calendar.timeInMillis to nowMs
    }

    private fun Calendar.startOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
