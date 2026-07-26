package com.byd.wjx.ui.components

import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.ConsumptionBad
import com.byd.wjx.ui.theme.ConsumptionGood
import com.byd.wjx.ui.theme.ConsumptionMid
import com.byd.wjx.ui.theme.NavyMid
import com.byd.wjx.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConsumptionThresholds(val good: Double, val bad: Double) {
    companion object {
        val Default = ConsumptionThresholds(good = 20.0, bad = 30.0)
    }
}

val LocalConsumptionThresholds = compositionLocalOf { ConsumptionThresholds.Default }

fun consumptionColor(kwhPer100km: Double, good: Double, bad: Double): Color = when {
    kwhPer100km < good -> ConsumptionGood
    kwhPer100km <= bad -> ConsumptionMid
    else -> ConsumptionBad
}

@Composable
@ReadOnlyComposable
fun consumptionColor(kwhPer100km: Double): Color {
    val thresholds = LocalConsumptionThresholds.current
    return consumptionColor(kwhPer100km, thresholds.good, thresholds.bad)
}

fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

@Composable
fun bydSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = NavyMid,
    checkedTrackColor = AccentGreen,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = NavyMid,
    uncheckedTrackColor = TextMuted,
    uncheckedBorderColor = Color.Transparent,
)
