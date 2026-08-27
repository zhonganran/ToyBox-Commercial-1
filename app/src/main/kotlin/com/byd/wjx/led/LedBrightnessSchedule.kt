package com.byd.wjx.led

import java.util.Calendar
import java.util.TimeZone

data class LedBrightnessSchedule(
    val enabled: Boolean = false,
    val dayStartMinute: Int = DEFAULT_DAY_START_MINUTE,
    val nightStartMinute: Int = DEFAULT_NIGHT_START_MINUTE,
    val dayBrightness: Int = 70,
    val nightBrightness: Int = 20,
) {
    fun normalized(minBrightness: Int, maxBrightness: Int): LedBrightnessSchedule = copy(
        dayStartMinute = dayStartMinute.coerceIn(0, MINUTES_PER_DAY - 1),
        nightStartMinute = nightStartMinute.coerceIn(0, MINUTES_PER_DAY - 1),
        dayBrightness = dayBrightness.coerceIn(minBrightness, maxBrightness),
        nightBrightness = nightBrightness.coerceIn(minBrightness, maxBrightness),
    )

    fun isDaytime(minuteOfDay: Int): Boolean {
        val minute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return when {
            dayStartMinute == nightStartMinute -> true
            dayStartMinute < nightStartMinute -> minute in dayStartMinute until nightStartMinute
            else -> minute >= dayStartMinute || minute < nightStartMinute
        }
    }

    fun brightnessAt(minuteOfDay: Int, manualBrightness: Int): Int = when {
        !enabled -> manualBrightness
        isDaytime(minuteOfDay) -> dayBrightness
        else -> nightBrightness
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val DEFAULT_DAY_START_MINUTE = 7 * 60
        const val DEFAULT_NIGHT_START_MINUTE = 19 * 60
    }
}

internal fun LedBrightnessSchedule.brightnessAt(
    nowMs: Long,
    manualBrightness: Int,
    timeZone: TimeZone = TimeZone.getDefault(),
): Int = brightnessAt(currentMinuteOfDay(nowMs, timeZone), manualBrightness)

internal fun currentMinuteOfDay(
    nowMs: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): Int = Calendar.getInstance(timeZone).run {
    timeInMillis = nowMs
    get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
}

internal fun formatMinuteOfDay(minuteOfDay: Int): String {
    val normalized = minuteOfDay.coerceIn(0, LedBrightnessSchedule.MINUTES_PER_DAY - 1)
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}
