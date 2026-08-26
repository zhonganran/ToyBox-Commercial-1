package com.byd.wjx.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.SocYellow
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary

internal fun batteryStatusColor(state: DashboardUiState): Color = when {
    (state.currentSoh ?: 100f) < 80f || state.batteryHealthStatus == "critical" -> SocRed
    (state.currentSoh ?: 100f) < 90f || state.batteryHealthStatus == "warning" -> SocYellow
    else -> AccentGreen
}

internal fun voltage12vColor(state: DashboardUiState): Color = when (state.voltage12vStatus) {
    "critical" -> SocRed
    "warning" -> SocYellow
    else -> AccentGreen
}

internal fun openStateText(value: Int?): String = when (value) {
    0 -> "关闭"
    1 -> "打开"
    else -> "--"
}

internal fun trunkStateText(value: Int?): String = when {
    value == null -> "--"
    BydSignalSemantics.isTrunkNotClosed(value) -> "打开"
    else -> "关闭"
}

@Composable
internal fun doorColor(value: Int?): Color = when (value) {
    0 -> AccentGreen
    1 -> SocYellow
    else -> TextMuted
}

internal fun onOffText(value: Int?): String = when (value) {
    1 -> "开启"
    0, 2 -> "关闭"
    else -> "--"
}

@Composable
internal fun lightColor(value: Int?): Color = if (value == 1) SceneAmber else TextPrimary

internal fun seatbeltText(value: Int?): String = when (value) {
    1 -> "已系"
    0 -> "未系"
    else -> "--"
}

@Composable
internal fun safetyColor(value: Int?): Color = when (value) {
    1 -> AccentGreen
    0 -> SocYellow
    else -> TextMuted
}

internal fun turnSignalText(value: Int?): String = when (value) {
    2 -> "左转"
    4 -> "右转"
    6 -> "双闪"
    1, 0 -> "关闭"
    else -> "--"
}

internal fun apaStatusText(value: Int?): String = when (value) {
    null -> "--"
    0 -> "关闭"
    1 -> "搜索车位"
    2 -> "泊车中"
    3 -> "泊车完成"
    else -> "状态 $value"
}

internal fun accStatusText(state: DashboardUiState): String = when {
    state.accCruiseState == 2 || state.adasAccMode == 2 ->
        state.accCruisingSpeed?.let { "已激活 · $it km/h" } ?: "已激活"
    state.accCruiseState == 1 || state.adasAccMode == 1 -> "待命"
    state.accCruiseState == 0 || state.adasAccMode == 0 -> "关闭"
    else -> "--"
}

internal fun adasStateText(value: Int?): String = when (value) {
    2 -> "已激活"
    1 -> "待命"
    0 -> "关闭"
    else -> value?.let { "状态 $it" } ?: "--"
}

internal fun tireMetric(
    label: String,
    pressure: Int?,
    temp: Int?,
    state: Int?,
): HomeDetailMetric {
    val abnormal = state != null && state != 0
    return HomeDetailMetric(
        label = label,
        value = "${tireValue(pressure)} · ${tireTempValue(temp)}",
        color = if (abnormal) SocYellow else AccentGreen,
        supporting = state?.let { if (it == 0) "状态正常" else "状态码 $it" },
    )
}
