package com.byd.wjx.ui.charges

import android.content.Context
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ChargeEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ChargesGroupBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun build(
        charges: List<ChargeEntity>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ChargesMonthGroup> {
        val locale = Locale.SIMPLIFIED_CHINESE
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayOfWeekFormat = SimpleDateFormat("EEE", locale)
        val monthLabelFormat = SimpleDateFormat("LLLL yyyy", locale)
        val todayKey = dayKeyFormat.format(Date(nowMs))
        val yesterdayKey = dayKeyFormat.format(
            Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, -1)
            }.time,
        )

        return charges
            .groupBy { monthKeyFormat.format(Date(it.startTs)) }
            .entries
            .sortedByDescending(Map.Entry<String, List<ChargeEntity>>::key)
            .map { (monthKey, monthCharges) ->
                val days = monthCharges
                    .groupBy { dayKeyFormat.format(Date(it.startTs)) }
                    .entries
                    .sortedByDescending(Map.Entry<String, List<ChargeEntity>>::key)
                    .map { (dayKey, dayCharges) ->
                        val firstDate = Date(dayCharges.first().startTs)
                        val dayOfWeek = dayOfWeekFormat.format(firstDate)
                        ChargesDayGroup(
                            date = dayKey,
                            label = when (dayKey) {
                                todayKey -> context.getString(R.string.charges_day_today, dayOfWeek)
                                yesterdayKey -> context.getString(R.string.charges_day_yesterday)
                                else -> "${SimpleDateFormat("dd MMM", locale).format(firstDate)} ($dayOfWeek)"
                            },
                            dayOfWeek = dayOfWeek,
                            totalKwh = dayCharges.sumOf { it.kwhCharged ?: 0.0 },
                            sessionCount = dayCharges.size,
                            totalCost = dayCharges.sumOf { it.cost ?: 0.0 },
                            charges = dayCharges,
                        )
                    }
                ChargesMonthGroup(
                    yearMonth = monthKey,
                    label = monthLabelFormat.format(Date(monthCharges.first().startTs))
                        .replaceFirstChar(Char::uppercase),
                    totalKwh = monthCharges.sumOf { it.kwhCharged ?: 0.0 },
                    sessionCount = monthCharges.size,
                    totalCost = monthCharges.sumOf { it.cost ?: 0.0 },
                    days = days,
                )
            }
    }
}
