package com.byd.wjx.ui.home

import androidx.compose.ui.graphics.Color
import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.domain.insight.LocalInsightTone
import com.byd.wjx.ui.dashboard.DashboardUiState
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.SceneAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun rangeProgress(value: Int?, maxValue: Int): Float =
    ((value ?: 0).toFloat() / maxValue).coerceIn(0f, 1f)

internal fun tireValue(value: Int?): String =
    value?.takeIf { it in 80..420 }?.let { "$it kPa" } ?: "-- kPa"

internal fun tireTempValue(value: Int?): String =
    value?.takeIf { it in -50..150 }?.let { "$it°C" } ?: "--°C"

internal fun tempText(value: Int?): String = value?.let { "$it°C" } ?: "--"

internal fun maxMinTempText(state: DashboardUiState): String {
    val maxText = state.maxBatTemp?.let { "$it" } ?: "--"
    val minText = state.minBatTemp?.let { "$it" } ?: "--"
    return "$maxText / $minText°C"
}

internal fun openCloseText(value: Int?): String = when (value) {
    BydSignalSemantics.COVER_CLOSED -> "关闭"
    BydSignalSemantics.COVER_OPEN -> "打开"
    else -> "--"
}

internal fun chargeGunText(state: DashboardUiState): String = when {
    state.isCharging -> "充电中"
    ChargeGunState.isConnected(state.chargeGunState) -> "已插枪"
    ChargeGunState.isDisconnected(state.chargeGunState) -> "未插枪"
    else -> "--"
}

internal fun formatCellVoltageDelta(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { "%.3f V".format(Locale.CHINA, it) } ?: "--"

internal fun powertrainRpmText(value: Int?): String =
    value?.let { "%,d rpm".format(Locale.CHINA, it) } ?: "-- rpm"

internal fun tripDurationText(startedAt: Long?): String =
    startedAt?.takeIf { it > 0L }?.let { formatDuration(System.currentTimeMillis() - it) } ?: "--"

internal fun chargeDurationText(charge: ChargeEntity): String =
    formatDuration((charge.endTs ?: System.currentTimeMillis()) - charge.startTs)

internal fun formatDuration(durationMs: Long): String {
    if (durationMs < 0L) return "--"
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L -> "${hours}小时${minutes}分"
        else -> "${minutes}分"
    }
}

internal fun formatChargeDate(timestamp: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(timestamp))

internal fun chargeTypeText(charge: ChargeEntity): String = when {
    charge.endTs == null -> "充电中"
    charge.type.equals("AC", ignoreCase = true) -> "交流慢充"
    charge.type.equals("DC", ignoreCase = true) -> "直流快充"
    else -> "充电完成"
}

internal fun chargeEnergyText(charge: ChargeEntity): String =
    (charge.kwhCharged ?: charge.kwhChargedSoc)?.let { "%.1f kWh".format(Locale.CHINA, it) } ?: "--"

internal fun insightToneColor(tone: LocalInsightTone): Color = when (tone) {
    LocalInsightTone.GOOD -> AccentGreen
    LocalInsightTone.INFO -> AccentBlue
    LocalInsightTone.WARNING -> SceneAmber
    LocalInsightTone.CRITICAL -> Color(0xFFFF5A68)
}

internal fun batteryHint(state: DashboardUiState): String = when {
    state.currentSoh == null -> "等待电池健康数据"
    state.currentSoh >= 95f -> "电池状态优秀"
    state.currentSoh >= 88f -> "电池状态良好"
    else -> "建议关注电池状态"
}

internal fun doorSummary(state: DashboardUiState): String {
    val values = listOf(state.doorFL, state.doorFR, state.doorRL, state.doorRR, state.trunk, state.hood)
    if (values.all { it == null }) return "--"
    val sideDoorOrHoodOpen = listOf(
        state.doorFL,
        state.doorFR,
        state.doorRL,
        state.doorRR,
        state.hood,
    ).any { it == 1 }
    return if (sideDoorOrHoodOpen || BydSignalSemantics.isTrunkNotClosed(state.trunk)) {
        "有开启"
    } else {
        "全关"
    }
}

internal fun lockSummary(state: DashboardUiState): String {
    val values = listOf(state.lockFL, state.lockFR, state.lockRL, state.lockRR)
    if (values.all { it == null }) return "--"
    return if (values.all(BydSignalSemantics::isDoorLocked)) "已锁" else "未锁"
}

internal fun acMainText(state: DashboardUiState): String = when (state.acStatus) {
    1 -> state.acTemp?.let { "$it°C" } ?: "开启"
    0 -> "关闭"
    else -> "--"
}

internal fun fanText(state: DashboardUiState): String = state.fanLevel?.let { "${it}挡" } ?: "--"

internal fun circText(value: Int?): String = when (value) {
    1 -> "开启"
    0 -> "外循环"
    else -> "--"
}

internal fun lightsActive(state: DashboardUiState): Boolean =
    listOf(state.lightLow, state.lightHigh, state.drl, state.hazardLights).any { it == 1 }

internal fun lightMainText(state: DashboardUiState): String = when {
    state.lightHigh == 1 -> "远光"
    state.lightLow == 1 -> "近光"
    state.drl == 1 -> "日行灯"
    state.hazardLights == 1 -> "双闪"
    listOf(state.lightLow, state.lightHigh, state.drl, state.hazardLights).all { it == null } -> "--"
    else -> "关闭"
}

internal fun gearText(gear: Int?): String = when (gear) {
    1 -> "P"
    2 -> "R"
    3 -> "N"
    4 -> "D"
    else -> gear?.toString() ?: "--"
}

internal fun driveModeText(mode: Int?): String = when (mode) {
    BydSignalSemantics.DRIVE_ECONOMY -> "经济模式"
    BydSignalSemantics.DRIVE_SPORT -> "运动模式"
    BydSignalSemantics.DRIVE_COMFORT -> "舒适模式"
    BydSignalSemantics.DRIVE_SNOW -> "雪地模式"
    BydSignalSemantics.DRIVE_MUDDY -> "泥地模式"
    BydSignalSemantics.DRIVE_SAND -> "沙地模式"
    BydSignalSemantics.DRIVE_MOUNTAIN -> "山地模式"
    BydSignalSemantics.DRIVE_RACING -> "竞速模式"
    else -> mode?.let { "模式 $it" } ?: "--"
}

internal fun workModeText(mode: Int?): String = when (mode) {
    BydSignalSemantics.ENERGY_STOP -> "停止"
    BydSignalSemantics.ENERGY_EV -> "EV"
    BydSignalSemantics.ENERGY_FORCE_EV -> "强制 EV"
    BydSignalSemantics.ENERGY_HEV -> "HEV"
    BydSignalSemantics.ENERGY_FUEL -> "燃油"
    BydSignalSemantics.ENERGY_KEEP -> "保持"
    else -> mode?.let { "模式 $it" } ?: "--"
}
