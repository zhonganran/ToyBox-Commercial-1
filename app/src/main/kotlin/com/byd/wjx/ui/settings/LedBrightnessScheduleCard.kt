package com.byd.wjx.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.led.LedBrightnessSchedule
import com.byd.wjx.led.formatMinuteOfDay
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun LedBrightnessScheduleCard(
    schedule: LedBrightnessSchedule,
    minBrightness: Int,
    maxBrightness: Int,
    brightnessText: (Int) -> String,
    onScheduleChange: (LedBrightnessSchedule) -> Unit,
) {
    val context = LocalContext.current
    var dayBrightness by remember(schedule.dayBrightness) {
        mutableFloatStateOf(schedule.dayBrightness.toFloat())
    }
    var nightBrightness by remember(schedule.nightBrightness) {
        mutableFloatStateOf(schedule.nightBrightness.toFloat())
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

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "日夜亮度联动",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        brightnessScheduleSummary(schedule, brightnessText),
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { onScheduleChange(schedule.copy(enabled = it)) },
                    colors = bydSwitchColors(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("白天时段", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                ScheduleTimeButton(formatMinuteOfDay(schedule.dayStartMinute)) {
                    pickTime(schedule.dayStartMinute) {
                        onScheduleChange(schedule.copy(dayStartMinute = it))
                    }
                }
                Text("至", color = TextSecondary, fontSize = 12.sp)
                ScheduleTimeButton(formatMinuteOfDay(schedule.nightStartMinute)) {
                    pickTime(schedule.nightStartMinute) {
                        onScheduleChange(schedule.copy(nightStartMinute = it))
                    }
                }
            }

            ScheduleBrightnessSlider(
                label = "白天亮度",
                value = dayBrightness,
                minBrightness = minBrightness,
                maxBrightness = maxBrightness,
                brightnessText = brightnessText,
                onValueChange = { dayBrightness = it },
                onValueChangeFinished = {
                    onScheduleChange(schedule.copy(dayBrightness = dayBrightness.toInt()))
                },
            )
            ScheduleBrightnessSlider(
                label = "夜间亮度",
                value = nightBrightness,
                minBrightness = minBrightness,
                maxBrightness = maxBrightness,
                brightnessText = brightnessText,
                onValueChange = { nightBrightness = it },
                onValueChangeFinished = {
                    onScheduleChange(schedule.copy(nightBrightness = nightBrightness.toInt()))
                },
            )
        }
    }
}

@Composable
private fun ScheduleTimeButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
        Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(5.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun ScheduleBrightnessSlider(
    label: String,
    value: Float,
    minBrightness: Int,
    maxBrightness: Int,
    brightnessText: (Int) -> String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(
                brightnessText(value.toInt()),
                color = AccentBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = minBrightness.toFloat()..maxBrightness.toFloat(),
            steps = (maxBrightness - minBrightness - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = AccentBlue,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = CardBorder,
            ),
        )
    }
}

private fun brightnessScheduleSummary(
    schedule: LedBrightnessSchedule,
    brightnessText: (Int) -> String,
): String = if (schedule.dayStartMinute == schedule.nightStartMinute) {
    "全天 ${brightnessText(schedule.dayBrightness)}"
} else {
    "${formatMinuteOfDay(schedule.dayStartMinute)}-${formatMinuteOfDay(schedule.nightStartMinute)} " +
        brightnessText(schedule.dayBrightness) + " · 其余时间 " + brightnessText(schedule.nightBrightness)
}
