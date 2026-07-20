package com.byd.wjx.ui.dashboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.byd.wjx.R
import com.byd.wjx.data.system.StorageVolumeMetric
import com.byd.wjx.data.system.SystemMetricsSnapshot
import com.byd.wjx.domain.calculator.Trend
import com.byd.wjx.domain.energy.EnergyFlowCategory
import com.byd.wjx.domain.energy.EnergyFlowTotals
import com.byd.wjx.ui.components.SocGauge
import com.byd.wjx.ui.components.consumptionColor
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.pro.ProStatusPill
import com.byd.wjx.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TRIP_DISTANCE_TREND_THRESHOLD_KM = 0.3

private fun formatDurationShort(context: Context, sessionStartedAt: Long?): String {
    if (sessionStartedAt == null) return "—"
    val elapsed = System.currentTimeMillis() - sessionStartedAt
    val totalMin = (elapsed / 60_000L).toInt().coerceAtLeast(0)
    val hours = totalMin / 60
    val minutes = totalMin % 60
    return if (hours > 0) {
        context.getString(R.string.dashboard_trip_duration_hours, hours, minutes)
    } else {
        context.getString(R.string.dashboard_trip_duration_minutes, minutes)
    }
}

private fun formatTripKm(context: Context, km: Double?): String {
    if (km == null || km.isNaN() || km.isInfinite() || km < 0.0) return "—"
    return context.getString(R.string.dashboard_trip_km_value, km)
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenTripMap: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DisposableEffect(viewModel) {
        viewModel.setPowertrainTelemetryActive(true)
        viewModel.setSystemMetricsActive(true)
        onDispose {
            viewModel.setPowertrainTelemetryActive(false)
            viewModel.setSystemMetricsActive(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ProPageHeader(
            eyebrow = "VEHICLE DATA",
            title = "车辆数据",
            subtitle = when {
                !state.isServiceRunning -> "后台服务未运行"
                !state.vehicleDataConnected -> "正在等待车辆数据"
                else -> "能耗、电池、行程与车辆状态"
            },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProStatusPill(text = "后台服务", active = state.isServiceRunning)
                    ProStatusPill(text = "车辆数据", active = state.vehicleDataConnected)
                }
            },
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT COLUMN — fill full height with SpaceBetween
            Box(modifier = Modifier.weight(0.4f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // TOP: SOC gauge + 4 compact driving stats around it.
                    // Two symmetric rows wrap the gauge:
                    //   row mid    — duration | odometer | inside temp
                    //   row bottom — trip km | range km + label | consumption + trend
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SocGauge(
                            soc = state.soc ?: 0,
                            modifier = Modifier.size(150.dp),
                            isCharging = state.isCharging,
                        )

                        // Live-ticking duration text, refreshed every 15 seconds.
                        var durationText by remember(context, state.sessionStartedAt) {
                            mutableStateOf(formatDurationShort(context, state.sessionStartedAt))
                        }
                        LaunchedEffect(context, state.sessionStartedAt) {
                            while (true) {
                                durationText = formatDurationShort(context, state.sessionStartedAt)
                                delay(15_000L)
                            }
                        }

                        // Row mid: schedule | odometer | inside temp
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                CornerStat(
                                    icon = Icons.Outlined.Schedule,
                                    text = durationText,
                                )
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (state.odometer != null) "%.1f km".format(state.odometer) else "— km",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                CornerStat(
                                    icon = Icons.Outlined.DirectionsCar,
                                    text = state.insideTemp?.let { "$it°" } ?: "—",
                                    iconLast = true,
                                )
                            }
                        }

                        // Row bottom: trip km | range km + label | consumption + trend
                        // Suppress the trend arrow within the first 300 m of a session.
                        val effectiveTrend = if (
                            state.sessionStartedAt != null &&
                            (state.tripDistanceKm ?: 0.0) < TRIP_DISTANCE_TREND_THRESHOLD_KM
                        ) Trend.NONE else state.consumptionTrend
                        val trendColor = when (effectiveTrend) {
                            Trend.DOWN -> AccentGreen
                            Trend.UP -> SocYellow
                            Trend.FLAT -> TextPrimary
                            Trend.NONE -> TextMuted
                        }
                        val rangeText = state.estimatedRangeKm?.let { "~${"%.0f".format(it)}" } ?: "—"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                CornerStat(
                                    icon = Icons.Outlined.Route,
                                    text = formatTripKm(context, state.tripDistanceKm),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(rangeText, color = AccentGreen, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.dashboard_unit_km), color = AccentGreen.copy(alpha = 0.7f), fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
                                }
                                Text(stringResource(R.string.dashboard_range_label), color = TextMuted, fontSize = 12.sp)
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (effectiveTrend != Trend.NONE) {
                                        Icon(
                                            imageVector = when (effectiveTrend) {
                                                Trend.DOWN -> Icons.AutoMirrored.Outlined.TrendingDown
                                                Trend.UP -> Icons.AutoMirrored.Outlined.TrendingUp
                                                else -> Icons.AutoMirrored.Outlined.TrendingFlat
                                            },
                                            contentDescription = null,
                                            tint = trendColor,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = state.consumption?.let { "%.1f".format(it) } ?: "—",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        // Number colour is decoupled from the trend: it always reflects
                                        // the consumption magnitude (consumptionColor). Trend direction is
                                        // conveyed solely by the arrow above, which hides on Trend.NONE.
                                        // Fixes the mid-trip "grey number" report where a momentary
                                        // Trend.NONE greyed out a number that was still updating.
                                        color = state.consumption?.let { consumptionColor(it) } ?: TextMuted,
                                    )
                                }
                            }
                        }
                    }

                    // Compact vehicle health cards: battery and idle drain.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Battery card: SoH | battery temperature | 12V supply.
                        val sohStatus = when {
                            (state.currentSoh ?: 100f) < 80f -> "critical"
                            (state.currentSoh ?: 100f) < 90f -> "warning"
                            else -> "ok"
                        }
                        val sohColor = when (sohStatus) {
                            "critical" -> SocRed; "warning" -> SocYellow; else -> AccentGreen
                        }
                        val tempColor = when (state.batteryHealthStatus) {
                            "critical" -> SocRed; "warning" -> SocYellow; else -> AccentGreen
                        }
                        val voltageColor = when (state.voltage12vStatus) {
                            "critical" -> SocRed; "warning" -> SocYellow; else -> AccentGreen
                        }
                        val worstColor = when {
                            sohStatus == "critical" ||
                                state.batteryHealthStatus == "critical" ||
                                state.voltage12vStatus == "critical" -> SocRed
                            sohStatus == "warning" ||
                                state.batteryHealthStatus == "warning" ||
                                state.voltage12vStatus == "warning" -> SocYellow
                            else -> AccentGreen
                        }
                        BatteryCompactCard(
                            sohText = state.currentSoh?.let { "%.0f%%".format(it) } ?: "—",
                            sohColor = sohColor,
                            tempText = state.avgBatTemp?.let { "${it}°" } ?: "—",
                            tempColor = tempColor,
                            voltageText = state.voltage12v?.let { stringResource(R.string.dashboard_voltage_value, it) } ?: "—",
                            voltageColor = voltageColor,
                            borderColor = worstColor,
                            onClick = { viewModel.toggleBatteryHealthExpanded() }
                        )
                        // Idle drain card — hidden in DiPlus mode (no zero-km records)
                        if (state.idleDrainAvailable) {
                            val idleTimeStr = if (state.idleDrainHours < 1.0)
                                stringResource(R.string.dashboard_idle_time_min, state.idleDrainHours * 60)
                            else stringResource(R.string.dashboard_idle_time_hours, state.idleDrainHours)
                            CompactCard(
                                leftValue = "%.1f".format(state.idleDrainKwhToday),
                                leftLabel = stringResource(R.string.dashboard_unit_kwh),
                                rightValue = idleTimeStr,
                                rightLabel = stringResource(R.string.dashboard_idle_drain_parking_label),
                                borderColor = when {
                                    state.idleDrainPercent > 5.0 -> SocRed
                                    state.idleDrainPercent > 2.0 -> SocYellow
                                    else -> AccentGreen
                                },
                                onClick = { viewModel.toggleIdleDrainExpanded() }
                            )
                        }
                        CompactCard(
                            leftValue = formatEnergyFlowKwh(state.sessionEnergyFlow.totalRegenKwh),
                            leftLabel = "本次回收 kWh",
                            rightValue = energyFlowCategoryLabel(state.energyFlowCategory),
                            rightLabel = state.energyState?.let { "能量状态 $it" } ?: "能量状态",
                            borderColor = AccentGreen,
                            hasData = state.energyState != null ||
                                state.sessionEnergyFlow.totalRecoveredKwh > 0.0,
                            onClick = { viewModel.toggleEnergyFlowExpanded() },
                        )
                        val systemMetrics = state.systemMetrics
                        CompactCard(
                            leftValue = systemMetrics?.cpuTotalUsagePercent?.let { "$it%" }
                                ?: if (state.systemMetricsLoading || state.systemMetricsSampleCount < 2) "采样中" else "--",
                            leftLabel = "CPU 总占用",
                            rightValue = systemMetrics?.memory?.let { "${it.usedPercent}%" }
                                ?: if (state.systemMetricsLoading) "读取中" else "--",
                            rightLabel = "内存占用",
                            borderColor = AccentBlue,
                            hasData = systemMetrics != null,
                            onClick = { viewModel.toggleSystemMetricsExpanded() },
                        )
                        CompactCard(
                            leftValue = state.frontMotorSpeed?.let(::formatRpm) ?: "—",
                            leftLabel = "前电机转速",
                            rightValue = state.rearMotorSpeed?.let(::formatRpm) ?: "—",
                            rightLabel = "后电机转速",
                            borderColor = AccentBlue,
                            hasData = state.frontMotorSpeed != null || state.rearMotorSpeed != null,
                            onClick = { viewModel.togglePowertrainDiagnosticsExpanded() },
                        )
                    }

                    // Pop-up dialogs
                    if (state.batteryHealthExpanded) {
                        val sohForColor = state.currentSoh ?: 100f
                        val color = when {
                            sohForColor < 80f ||
                                state.batteryHealthStatus == "critical" ||
                                state.voltage12vStatus == "critical" -> SocRed
                            sohForColor < 90f ||
                                state.batteryHealthStatus == "warning" ||
                                state.voltage12vStatus == "warning" -> SocYellow
                            else -> AccentGreen
                        }
                        com.byd.wjx.ui.battery.BatteryHealthDialog(
                            liveSoc = state.soc,
                            liveCellDelta = state.cellVoltageDelta,
                            liveCellVoltageMin = state.cellVoltageMin,
                            liveCellVoltageMax = state.cellVoltageMax,
                            liveBatTemp = state.avgBatTemp,
                            liveVoltage12v = state.voltage12v,
                            liveSoh = state.currentSoh,
                            liveLifetimeKm = state.currentLifetimeKm,
                            liveLifetimeKwh = state.currentLifetimeKwh,
                            borderColor = color,
                            onDismiss = { viewModel.toggleBatteryHealthExpanded() },
                        )
                    }
                    if (state.idleDrainExpanded) {
                        val color = when {
                            state.idleDrainPercent > 5.0 -> SocRed
                            state.idleDrainPercent > 2.0 -> SocYellow
                            else -> AccentGreen
                        }
                        CardDetailDialog(
                            title = stringResource(R.string.dashboard_idle_drain_title),
                            borderColor = color,
                            onDismiss = { viewModel.toggleIdleDrainExpanded() }
                        ) {
                            if (state.idleDrainRate > 0) {
                                DetailRow(stringResource(R.string.dashboard_idle_drain_rate_label), stringResource(R.string.dashboard_idle_drain_rate_value, state.idleDrainRate), color)
                            }
                            DetailRow(stringResource(R.string.dashboard_idle_drain_week_label), stringResource(R.string.dashboard_idle_drain_week_value, state.idleDrainKwhWeek), TextPrimary)
                            if (state.idleDrainKwhWeek > 0) {
                                DetailRow(stringResource(R.string.dashboard_idle_drain_avg_day_label), stringResource(R.string.dashboard_idle_drain_week_value, state.idleDrainKwhWeek / 7.0), TextPrimary)
                            }
                        }
                    }
                    if (state.powertrainDiagnosticsExpanded) {
                        CardDetailDialog(
                            title = "电驱与动力诊断",
                            borderColor = AccentBlue,
                            onDismiss = { viewModel.togglePowertrainDiagnosticsExpanded() },
                        ) {
                            DetailRow("前电机转速", state.frontMotorSpeed?.let(::formatRpm) ?: "--", TextPrimary)
                            DetailRow("后电机转速", state.rearMotorSpeed?.let(::formatRpm) ?: "--", TextPrimary)
                            DetailRow("前电机温度", state.frontMotorTemp?.let { "$it °C" } ?: "--", TextPrimary)
                            DetailRow("后电机温度", state.rearMotorTemp?.let { "$it °C" } ?: "--", TextPrimary)
                            DetailRow("前电机电流", state.frontMotorCurrent?.let { "%.1f A".format(it) } ?: "--", TextPrimary)
                            DetailRow("后电机电流", state.rearMotorCurrent?.let { "%.1f A".format(it) } ?: "--", TextPrimary)
                            DetailRow("发动机转速", state.engineRpm?.let(::formatRpm) ?: "--", TextPrimary)
                            DetailRow("冷却液温度", state.waterTemp?.let { "$it °C" } ?: "--", TextPrimary)
                        }
                    }
                    if (state.energyFlowExpanded) {
                        CardDetailDialog(
                            title = "能量回收与发动机发电",
                            borderColor = AccentGreen,
                            onDismiss = { viewModel.toggleEnergyFlowExpanded() },
                        ) {
                            DetailRow("当前分类", energyFlowCategoryLabel(state.energyFlowCategory), AccentGreen)
                            DetailRow("原始能量状态", state.energyState?.toString() ?: "--", TextPrimary)
                            EnergyFlowDetailRows("本次行程", state.sessionEnergyFlow)
                            EnergyFlowDetailRows("累计统计", state.lifetimeEnergyFlow)
                        }
                    }
                    if (state.systemMetricsExpanded) {
                        CardDetailDialog(
                            title = "车机系统监控",
                            borderColor = AccentBlue,
                            onDismiss = { viewModel.toggleSystemMetricsExpanded() },
                        ) {
                            SystemMetricsDetailRows(
                                snapshot = state.systemMetrics,
                                loading = state.systemMetricsLoading,
                                sampleReady = state.systemMetricsSampleCount >= 2,
                            )
                        }
                    }
                }
            }

            DashboardTripsPanel(
                state = state,
                onOpenTripMap = onOpenTripMap,
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun DashboardTripsPanel(
    state: DashboardUiState,
    onOpenTripMap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.dashboard_recent_trips_title),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (onOpenTripMap != null) {
                    Row(
                        modifier = Modifier.clickable(onClick = onOpenTripMap),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Route,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("行程地图", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    title = stringResource(R.string.dashboard_stat_distance),
                    value = stringResource(R.string.dashboard_stat_km_value, state.totalKmToday),
                    subtitle = stringResource(R.string.dashboard_trip_count, state.tripCount),
                    accentColor = AccentGreen,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = stringResource(R.string.dashboard_stat_energy),
                    value = stringResource(R.string.dashboard_stat_kwh_value, state.totalKwhToday),
                    subtitle = "均值 %.1f/100".format(state.avgConsumption),
                    accentColor = AccentBlue,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(color = CardBorder.copy(alpha = 0.45f))

            val recentTrips = state.recentTrips.take(4)
            if (recentTrips.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.dashboard_empty_no_trips),
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                recentTrips.forEachIndexed { index, trip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.width(82.dp)) {
                            Text(
                                text = formatDashboardTripTime(trip.startTs),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = trip.endTs?.let { dashboardTripDuration(it - trip.startTs) } ?: "进行中",
                                color = TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            text = trip.distanceKm?.let { "%.1f km".format(it) } ?: "-- km",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = trip.kwhPer100km?.let { "%.1f kWh/100km".format(it) } ?: "--",
                            color = trip.kwhPer100km?.let { consumptionColor(it) } ?: TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(116.dp),
                        )
                    }
                    if (index != recentTrips.lastIndex) {
                        HorizontalDivider(color = CardBorder.copy(alpha = 0.28f))
                    }
                }
            }
        }
    }
}

private fun formatDashboardTripTime(timestampMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMs))

private fun dashboardTripDuration(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}

private fun formatRpm(value: Int): String = "$value rpm"

private fun formatEnergyFlowKwh(value: Double): String = when {
    value >= 10.0 -> "%.1f".format(value)
    value >= 0.1 -> "%.2f".format(value)
    else -> "%.3f".format(value)
}

private fun energyFlowCategoryLabel(category: EnergyFlowCategory): String = when (category) {
    EnergyFlowCategory.EV_4WD_REGEN -> "纯电四驱回收"
    EnergyFlowCategory.EV_FWD_REGEN -> "纯电前驱回收"
    EnergyFlowCategory.EV_RWD_REGEN -> "纯电后驱回收"
    EnergyFlowCategory.HEV_REGEN -> "混动回收"
    EnergyFlowCategory.ASSIST_REGEN -> "辅助驱动回收"
    EnergyFlowCategory.OTHER_REGEN -> "其他回收"
    EnergyFlowCategory.ENGINE_GENERATION -> "发动机发电"
    EnergyFlowCategory.NONE -> "当前无回收"
}

@Composable
private fun ColumnScope.SystemMetricsDetailRows(
    snapshot: SystemMetricsSnapshot?,
    loading: Boolean,
    sampleReady: Boolean,
) {
    if (snapshot == null) {
        Text(
            text = if (loading) "正在读取车机系统信息…" else "当前系统节点不可读",
            color = TextMuted,
            fontSize = 13.sp,
        )
        return
    }

    SystemMetricsSection("处理器")
    DetailRow(
        "总占用",
        snapshot.cpuTotalUsagePercent?.let { "$it%" } ?: if (sampleReady) "--" else "采样中",
        AccentBlue,
    )
    DetailRow("核心数", "${snapshot.cpuCoreCount}", TextPrimary)
    snapshot.cpuModelName?.let { DetailRow("CPU 型号", it, TextPrimary) }
    val loadText = listOf(snapshot.loadAverage1, snapshot.loadAverage5, snapshot.loadAverage15)
        .joinToString(" / ") { it?.let { value -> "%.2f".format(value) } ?: "--" }
    DetailRow("系统负载 1/5/15", loadText, TextPrimary)
    snapshot.uptimeSeconds?.let { DetailRow("运行时间", formatUptime(it), TextPrimary) }
    snapshot.cpuCores.take(12).forEach { core ->
        val value = buildList {
            add(core.usagePercent?.let { "$it%" } ?: "--")
            core.frequencyMhz?.let { add("$it MHz") }
            core.governor?.let(::add)
        }.joinToString(" · ")
        DetailRow("CPU${core.index}", value, TextPrimary)
    }

    snapshot.memory?.let { memory ->
        SystemMetricsSection("内存")
        DetailRow("已用", "${memory.usedPercent}%", AccentBlue)
        DetailRow("可用 / 总量", "${memory.availableMb} / ${memory.totalMb} MB", TextPrimary)
        memory.cachedMb?.let { DetailRow("缓存", "$it MB", TextPrimary) }
        memory.buffersMb?.let { DetailRow("Buffers", "$it MB", TextPrimary) }
        memory.activeMb?.let { DetailRow("活跃", "$it MB", TextPrimary) }
        memory.inactiveMb?.let { DetailRow("非活跃", "$it MB", TextPrimary) }
        memory.dirtyMb?.let { DetailRow("待写回", "$it MB", TextPrimary) }
        memory.slabMb?.let { DetailRow("Slab", "$it MB", TextPrimary) }
        memory.swapTotalMb?.let { total ->
            DetailRow("Swap 可用 / 总量", "${memory.swapFreeMb ?: 0} / $total MB", TextPrimary)
        }
        memory.zramMb?.let { DetailRow("ZRAM", "$it MB", TextPrimary) }
        memory.cmaMb?.let { DetailRow("CMA", "$it MB", TextPrimary) }
    }

    if (snapshot.storageVolumes.isNotEmpty()) {
        SystemMetricsSection("存储空间")
        snapshot.storageVolumes.forEach { volume ->
            DetailRow(
                storageLabel(volume.label),
                formatStorageVolumeSummary(volume),
                TextPrimary,
            )
        }
    }

    if (snapshot.diskIo.isNotEmpty()) {
        SystemMetricsSection("磁盘 IO")
        snapshot.diskIo.take(8).forEach { disk ->
            val speed = if (disk.readMbPerSec != null || disk.writeMbPerSec != null) {
                "R %.2f / W %.2f MB/s".format(disk.readMbPerSec ?: 0.0, disk.writeMbPerSec ?: 0.0)
            } else if (!sampleReady) {
                "采样中"
            } else {
                "--"
            }
            DetailRow(disk.device, speed, TextPrimary)
            DetailRow("累计读 / 写", "${disk.readMb} / ${disk.writeMb} MB", TextMuted)
        }
    }

    if (snapshot.gpuReadings.isNotEmpty()) {
        SystemMetricsSection("GPU")
        snapshot.gpuReadings.forEach { DetailRow(it.label, it.value, TextPrimary) }
    }

    if (snapshot.thermalZones.isNotEmpty()) {
        SystemMetricsSection("温度")
        snapshot.thermalZones.take(16).forEach { reading ->
            val color = when {
                reading.celsius >= 90f -> SocRed
                reading.celsius >= 75f -> SocYellow
                else -> TextPrimary
            }
            DetailRow(reading.name, "%.1f °C".format(reading.celsius), color)
        }
    }

    SystemMetricsSection("设备")
    snapshot.device.socModel?.let { DetailRow("SoC", it, TextPrimary) }
    DetailRow("车机型号", snapshot.device.model.ifBlank { "--" }, TextPrimary)
    DetailRow("主板 / 硬件", "${snapshot.device.board} / ${snapshot.device.hardware}", TextPrimary)
    DetailRow("Android", "${snapshot.device.androidVersion} (API ${snapshot.device.apiLevel})", TextPrimary)
    snapshot.device.securityPatch.takeIf { it.isNotBlank() }?.let {
        DetailRow("安全补丁", it, TextPrimary)
    }
    DetailRow("ABI", snapshot.device.abis.ifBlank { "--" }, TextPrimary)

    SystemMetricsSection("当前应用进程")
    DetailRow("PID / 线程", "${snapshot.appProcess.pid} / ${snapshot.appProcess.threadCount ?: "--"}", TextPrimary)
    DetailRow("RSS / 虚拟内存", "${snapshot.appProcess.vmRssMb ?: "--"} / ${snapshot.appProcess.vmSizeMb ?: "--"} MB", TextPrimary)
    DetailRow("JVM 堆", "${snapshot.appProcess.jvmHeapUsedMb} / ${snapshot.appProcess.jvmHeapMaxMb} MB", TextPrimary)
    DetailRow("打开文件数", snapshot.appProcess.openFdCount?.toString() ?: "--", TextPrimary)
}

@Composable
private fun SystemMetricsSection(title: String) {
    Text(
        text = title,
        color = AccentBlue,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

private fun storageLabel(label: String): String = when (label) {
    "data" -> "数据分区"
    "root" -> "根分区"
    "cache" -> "缓存分区"
    "external" -> "外部存储"
    else -> label
}

internal fun formatStorageVolumeSummary(volume: StorageVolumeMetric): String =
    "${volume.usedPercent}% · 可用 ${"%.1f".format(Locale.CHINA, volume.freeGb)} / " +
        "总 ${"%.1f".format(Locale.CHINA, volume.totalGb)} GB"

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400L
    val hours = seconds % 86_400L / 3_600L
    val minutes = seconds % 3_600L / 60L
    return when {
        days > 0 -> "${days}天 ${hours}小时"
        hours > 0 -> "${hours}小时 ${minutes}分"
        else -> "${minutes}分"
    }
}

@Composable
private fun ColumnScope.EnergyFlowDetailRows(title: String, totals: EnergyFlowTotals) {
    Text(
        text = title,
        color = AccentGreen,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
    DetailRow("回收合计", "${formatEnergyFlowKwh(totals.totalRegenKwh)} kWh", TextPrimary)
    DetailRow("纯电四驱", "${formatEnergyFlowKwh(totals.ev4wdRegenKwh)} kWh", TextPrimary)
    DetailRow("纯电前驱", "${formatEnergyFlowKwh(totals.evFwdRegenKwh)} kWh", TextPrimary)
    DetailRow("纯电后驱", "${formatEnergyFlowKwh(totals.evRwdRegenKwh)} kWh", TextPrimary)
    DetailRow("混动回收", "${formatEnergyFlowKwh(totals.hevRegenKwh)} kWh", TextPrimary)
    DetailRow("辅助驱动回收", "${formatEnergyFlowKwh(totals.assistRegenKwh)} kWh", TextPrimary)
    DetailRow("其他回收", "${formatEnergyFlowKwh(totals.otherRegenKwh)} kWh", TextPrimary)
    DetailRow("发动机发电", "${formatEnergyFlowKwh(totals.engineGenerationKwh)} kWh", TextPrimary)
}

// ============================================================================
// Compact card — same look for dashboard health summaries
// ============================================================================

@Composable
private fun CompactCard(
    leftValue: String,
    leftLabel: String,
    rightValue: String,
    rightLabel: String,
    borderColor: Color,
    hasData: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(leftValue, color = if (hasData) borderColor else TextMuted,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(leftLabel, color = TextMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rightValue, color = if (hasData) borderColor else TextMuted,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(rightLabel, color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BatteryCompactCard(
    sohText: String,
    sohColor: Color,
    tempText: String,
    tempColor: Color,
    voltageText: String,
    voltageColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BatteryCell(value = sohText, label = "健康度", color = sohColor)
            BatteryCell(value = tempText, label = stringResource(R.string.dashboard_battery_temp_label), color = tempColor)
            BatteryCell(value = voltageText, label = stringResource(R.string.dashboard_battery_voltage_label), color = voltageColor)
        }
    }
}

@Composable
private fun BatteryCell(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}

// ============================================================================
// Pop-up dialog for card details
// ============================================================================

@Composable
private fun CardDetailDialog(
    title: String? = null,
    borderColor: Color,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.CenterStart
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = androidx.compose.foundation.BorderStroke(2.dp, borderColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .padding(start = 22.dp, end = 16.dp)
                    .fillMaxWidth(0.4f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { }
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (title != null) {
                        Text(title, color = borderColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    content()
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String?, accentColor: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = modifier.height(64.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = TextSecondary, fontSize = 11.sp)
            Text(value, color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace)
            Text(subtitle ?: "", color = TextMuted, fontSize = 11.sp)
        }
    }
}

/**
 * Compact stat used in the top section of the dashboard left column.
 * Icons are muted gray and values use a monospace font.
 * Set iconLast=true for the right-aligned variant where the value comes before the icon.
 */
@Composable
private fun CornerStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconLast: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!iconLast) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
        )
        if (iconLast) {
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
