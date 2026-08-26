package com.byd.wjx.ui.automation

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.automation.ScheduleSpec
import com.byd.wjx.data.automation.minuteToHHmm
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextMuted

/** Edits exact-time and time-range triggers, including their optional day-of-week filter. */
@Composable
internal fun ScheduleTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit,
) {
    val context = LocalContext.current
    val spec = remember(trigger.value) {
        ScheduleSpec.fromJson(trigger.value) ?: ScheduleSpec(8 * 60, 10 * 60, emptySet())
    }
    val isExact = spec.isExact

    fun push(newSpec: ScheduleSpec) {
        onUpdate(
            trigger.copy(
                value = newSpec.toJson(),
                displayName = scheduleDisplayName(context, newSpec),
            ),
        )
    }

    fun pickTime(currentMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            currentMinute / 60,
            currentMinute % 60,
            true,
        ).show()
    }

    val dayLabels = listOf(
        1 to stringResource(R.string.automation_day_mon),
        2 to stringResource(R.string.automation_day_tue),
        3 to stringResource(R.string.automation_day_wed),
        4 to stringResource(R.string.automation_day_thu),
        5 to stringResource(R.string.automation_day_fri),
        6 to stringResource(R.string.automation_day_sat),
        7 to stringResource(R.string.automation_day_sun),
    )

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = isExact,
                onClick = { if (!isExact) push(spec.copy(toMinute = spec.fromMinute)) },
                label = { Text(stringResource(R.string.automation_schedule_mode_exact), fontSize = 12.sp) },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = !isExact,
                onClick = {
                    if (isExact) push(spec.copy(toMinute = (spec.fromMinute + 120) % MINUTES_PER_DAY))
                },
                label = { Text(stringResource(R.string.automation_schedule_mode_range), fontSize = 12.sp) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isExact) {
                Text(stringResource(R.string.automation_schedule_time_label) + " ", fontSize = 12.sp, color = TextMuted)
                TimeChip(minuteToHHmm(spec.fromMinute)) {
                    pickTime(spec.fromMinute) { push(spec.copy(fromMinute = it, toMinute = it)) }
                }
            } else {
                Text(stringResource(R.string.automation_schedule_from) + " ", fontSize = 12.sp, color = TextMuted)
                TimeChip(minuteToHHmm(spec.fromMinute)) {
                    pickTime(spec.fromMinute) { push(spec.copy(fromMinute = it)) }
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.automation_schedule_to) + " ", fontSize = 12.sp, color = TextMuted)
                TimeChip(minuteToHHmm(spec.toMinute)) {
                    pickTime(spec.toMinute) { push(spec.copy(toMinute = it)) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.automation_schedule_days_label), fontSize = 11.sp, color = TextMuted)
        Spacer(Modifier.height(2.dp))
        Row {
            dayLabels.forEach { (day, label) ->
                DayChip(
                    label = label,
                    selected = day in spec.days,
                    onClick = {
                        val newDays = if (day in spec.days) spec.days - day else spec.days + day
                        push(spec.copy(days = newDays))
                    },
                )
                Spacer(Modifier.width(3.dp))
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        color = AccentGreen,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp, 6.dp),
    )
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) AccentGreen else TextMuted,
        modifier = Modifier
            .background(
                if (selected) AccentGreen.copy(alpha = 0.15f) else CardSurface,
                RoundedCornerShape(10.dp),
            )
            .border(1.dp, if (selected) AccentGreen else CardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    )
}

private fun scheduleDisplayName(context: Context, spec: ScheduleSpec): String {
    val time = if (spec.isExact) {
        minuteToHHmm(spec.fromMinute)
    } else {
        "${minuteToHHmm(spec.fromMinute)}-${minuteToHHmm(spec.toMinute)}"
    }
    val days = scheduleDaysShort(context, spec.days)
    return if (days.isEmpty()) time else "$time $days"
}

private fun scheduleDaysShort(context: Context, days: Set<Int>): String {
    if (days.isEmpty() || days.size == DAYS_PER_WEEK) return ""
    val labels = mapOf(
        1 to R.string.automation_day_mon,
        2 to R.string.automation_day_tue,
        3 to R.string.automation_day_wed,
        4 to R.string.automation_day_thu,
        5 to R.string.automation_day_fri,
        6 to R.string.automation_day_sat,
        7 to R.string.automation_day_sun,
    )
    return days.sorted().joinToString(",") { context.getString(labels.getValue(it)) }
}

private const val MINUTES_PER_DAY = 1440
private const val DAYS_PER_WEEK = 7
