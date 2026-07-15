package com.byd.wjx.ui.charges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.R
import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.autoservice.ChargingReading
import com.byd.wjx.data.local.dao.ChargeSummary
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChargesScreen(
    onNavigateSettings: () -> Unit = {},
    viewModel: ChargesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setLiveTelemetryActive(true)
        onDispose { viewModel.setLiveTelemetryActive(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        ProPageHeader(
            eyebrow = "CHARGING",
            title = "充电记录",
            subtitle = "慢充、快充、电费与历史能耗",
            trailing = {
                Button(
                    onClick = { viewModel.onCreateNewCharge() },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.charges_add_button), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (!state.initialAutoserviceCheckDone) {
            Box(modifier = Modifier.fillMaxSize())
            return@Column
        }

        if (state.autoserviceAllSentinel) {
            SentinelEmptyState()
            return@Column
        }

        state.liveCharging?.let { reading ->
            LiveChargingCard(reading)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChargesChip(stringResource(R.string.dashboard_period_day), state.period == ChargesPeriod.TODAY) { viewModel.setPeriod(ChargesPeriod.TODAY) }
                ChargesChip(stringResource(R.string.dashboard_period_week), state.period == ChargesPeriod.WEEK) { viewModel.setPeriod(ChargesPeriod.WEEK) }
                ChargesChip(stringResource(R.string.dashboard_period_month), state.period == ChargesPeriod.MONTH) { viewModel.setPeriod(ChargesPeriod.MONTH) }
                ChargesChip(stringResource(R.string.dashboard_period_year), state.period == ChargesPeriod.YEAR) { viewModel.setPeriod(ChargesPeriod.YEAR) }
                ChargesChip(stringResource(R.string.dashboard_period_all), state.period == ChargesPeriod.ALL) { viewModel.setPeriod(ChargesPeriod.ALL) }
                Spacer(modifier = Modifier.width(12.dp))
                ChargesChip(stringResource(R.string.charges_filter_all), state.typeFilter == ChargeTypeFilter.ALL) { viewModel.setTypeFilter(ChargeTypeFilter.ALL) }
                ChargesChip("慢充", state.typeFilter == ChargeTypeFilter.AC) { viewModel.setTypeFilter(ChargeTypeFilter.AC) }
                ChargesChip("快充", state.typeFilter == ChargeTypeFilter.DC) { viewModel.setTypeFilter(ChargeTypeFilter.DC) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            if (state.months.isEmpty()) {
                Column(
                    modifier = Modifier.weight(0.65f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.charges_empty),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(0.65f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (month in state.months) {
                        item(key = "month_${month.yearMonth}") {
                            ChargesMonthHeader(
                                month = month,
                                expanded = month.yearMonth in state.expandedMonths,
                                currencySymbol = state.currencySymbol,
                                onClick = { viewModel.toggleMonth(month.yearMonth) }
                            )
                        }
                        if (month.yearMonth in state.expandedMonths) {
                            for (day in month.days) {
                                item(key = "day_${month.yearMonth}_${day.date}") {
                                    ChargesDayHeader(
                                        day = day,
                                        expanded = day.date in state.expandedDays,
                                        currencySymbol = state.currencySymbol,
                                        onClick = { viewModel.toggleDay(day.date) }
                                    )
                                }
                                if (day.date in state.expandedDays) {
                                    item(key = "cheader_${month.yearMonth}_${day.date}") {
                                        ChargesColumnHeaders(currencySymbol = state.currencySymbol)
                                    }
                                    for (charge in day.charges) {
                                        item(key = "charge_${charge.id}") {
                                            ChargeRow(
                                                charge = charge,
                                                currencySymbol = state.currencySymbol,
                                                onLongClick = { viewModel.onLongPressCharge(charge) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(CardBorder.copy(alpha = 0.5f))
            )

            ChargesStatsPanel(
                periodSummary = state.periodSummary,
                currencySymbol = state.currencySymbol,
                bmsLifetimeKm = state.bmsLifetimeKm,
                bmsLifetimeKwh = state.bmsLifetimeKwh,
                nominalCapacityKwh = state.nominalCapacityKwh,
                sohSeries = state.sohSeries,
                cellDeltaSeries = state.cellDeltaSeries,
                batTempSeries = state.batTempSeries,
                modifier = Modifier.weight(0.35f).fillMaxHeight()
            )
        }

        state.selectedChargeForAction?.let { charge ->
            ChargeActionSheet(
                charge = charge,
                onDismiss = { viewModel.onDismissActionSheet() },
                onEdit = { viewModel.onEditCharge() },
                onDeletePrompt = { viewModel.onConfirmDeletePrompt() },
            )
        }
        state.editingCharge?.let { charge ->
            ChargeEditDialog(
                charge = charge,
                homeTariff = state.homeTariff,
                dcTariff = state.dcTariff,
                batteryCapacityKwh = state.effectiveCapacityKwh,
                currencySymbol = state.currencySymbol,
                onDismiss = { viewModel.onDismissEdit() },
                onSave = { viewModel.onSaveEdit(it) },
            )
        }
        state.deleteConfirmCharge?.let { charge ->
            AlertDialog(
                onDismissRequest = { viewModel.onDismissDeleteConfirm() },
                title = { Text(stringResource(R.string.charges_delete_dialog_title)) },
                text = {
                    Text(stringResource(R.string.charges_delete_dialog_text, charge.kwhCharged?.let { "%.1f".format(it) } ?: "-"))
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onConfirmDelete() }) {
                        Text(stringResource(R.string.charges_action_delete), color = SocRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onDismissDeleteConfirm() }) {
                        Text(stringResource(R.string.settings_cancel_button))
                    }
                },
                containerColor = CardSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChargeActionSheet(
    charge: ChargeEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeletePrompt: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${formatChargeType(charge.type)} • ${charge.kwhCharged?.let { "%.1f".format(it) } ?: "—"} 千瓦时",
                color = TextSecondary, fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.charges_action_edit), color = TextPrimary, fontSize = 16.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDeletePrompt).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.charges_action_delete), color = SocRed, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─── Fallback states ──────────────────────────────────────────────────────────

@Composable
private fun SentinelEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .background(CardSurface, RoundedCornerShape(12.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 32.dp, vertical = 60.dp)
        ) {
            Text(
                text = stringResource(R.string.charges_sentinel_empty_text),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun LiveChargingCard(reading: ChargingReading) {
    val status = when (reading.bmsState) {
        1 -> "充电中"
        13 -> "充电暂停"
        else -> "充电枪已连接"
    }
    val type = when (reading.gunConnectState) {
        ChargeGunState.AC -> "交流慢充"
        ChargeGunState.DC, ChargeGunState.AC_DC -> "直流快充"
        else -> when (reading.chargingType) {
            2 -> "交流慢充"
            4, 5 -> "直流快充"
            else -> "充电"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, AccentGreen.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(modifier = Modifier.width(132.dp)) {
                Text("实时充电", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("$type · $status", color = AccentGreen, fontSize = 12.sp)
            }
            LiveChargingMetric(
                label = "功率",
                value = reading.chargePowerKw?.let { "%.1f kW".format(it) } ?: "—",
            )
            LiveChargingMetric(
                label = "电流",
                value = reading.chargeCurrentA?.let { "$it A" } ?: "—",
            )
            LiveChargingMetric(
                label = "电压",
                value = reading.chargeBatteryVoltV?.let { "$it V" } ?: "—",
            )
            LiveChargingMetric(
                label = "本次电量",
                value = reading.chargingCapacityKwh?.let { "%.2f kWh".format(it) } ?: "—",
            )
        }
        HorizontalDivider(color = CardBorder.copy(alpha = 0.45f), thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Spacer(modifier = Modifier.width(132.dp))
            LiveChargingMetric(
                label = "允许充电功率",
                value = reading.batteryMaxChargePowerKw?.let { "$it kW" } ?: "—",
            )
            LiveChargingMetric(
                label = "允许放电功率",
                value = reading.batteryMaxDischargePowerKw?.let { "$it kW" } ?: "—",
            )
            LiveChargingMetric(
                label = "绝缘电阻",
                value = reading.batteryInsulationKohm?.let { "$it kΩ" } ?: "—",
            )
            LiveChargingMetric(
                label = "停止 SOC",
                value = reading.chargeStopSoc?.let { "$it%" } ?: "—",
            )
        }
    }
}

@Composable
private fun LiveChargingMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(
            value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ─── List components ──────────────────────────────────────────────────────────

@Composable
private fun ChargesMonthHeader(
    month: ChargesMonthGroup,
    expanded: Boolean,
    currencySymbol: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(CardSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "▼" else "▶", color = AccentGreen, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(month.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${month.sessionCount}",
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp)
            )
            Text(
                stringResource(R.string.trips_kwh_decimal_value, month.totalKwh),
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(88.dp)
            )
            Text(
                "%.2f %s".format(month.totalCost, currencySymbol),
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp)
            )
        }
    }
}

@Composable
private fun ChargesDayHeader(
    day: ChargesDayGroup,
    expanded: Boolean,
    currencySymbol: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(CardSurface.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "▼" else "▶", color = AccentBlue, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(day.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${day.sessionCount}",
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp)
            )
            Text(
                stringResource(R.string.trips_kwh_decimal_value, day.totalKwh),
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(88.dp)
            )
            Text(
                "%.2f %s".format(day.totalCost, currencySymbol),
                color = TextSecondary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp)
            )
        }
    }
}

@Composable
private fun ChargesColumnHeaders(currencySymbol: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.charges_col_time), color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(100.dp))
        Text("电量", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(80.dp))
        Text(stringResource(R.string.charges_col_kwh), color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
        Text(currencySymbol.lowercase(), color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
    }
    HorizontalDivider(
        color = CardBorder.copy(alpha = 0.5f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 36.dp, end = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChargeRow(
    charge: ChargeEntity,
    currencySymbol: String,
    onLongClick: () -> Unit,
) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
    val startTime = timeFmt.format(Date(charge.startTs))

    val socText = when {
        charge.socStart != null && charge.socEnd != null -> "${charge.socStart}% → ${charge.socEnd}%"
        charge.socStart != null -> "${charge.socStart}% → —"
        else -> "—"
    }

    val kwh = charge.kwhCharged?.let { "%.1f".format(it) } ?: "—"
    val cost = charge.cost?.let { "%.2f".format(it) } ?: "—"

    val typeLabel = when (charge.gunState) {
        2 -> "慢充"
        3, 4 -> "快充"
        else -> formatChargeType(charge.type)
    }
    val typeColor = when (typeLabel) {
        "快充" -> AccentOrange
        else -> AccentBlue
    }

    Column(
        modifier = Modifier.combinedClickable(
            onClick = { /* no-op for regular click */ },
            onLongClick = onLongClick,
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type badge + time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(CardSurfaceElevated, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        typeLabel,
                        color = typeColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    startTime,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                socText,
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(80.dp)
            )
            Text(
                kwh,
                color = AccentGreen,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp)
            )
            Text(
                cost,
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp)
            )
        }
        HorizontalDivider(
            color = CardBorder.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 36.dp, end = 12.dp)
        )
    }
}

// ─── Stats panel ──────────────────────────────────────────────────────────────

@Composable
private fun ChargesStatsPanel(
    periodSummary: ChargeSummary,
    currencySymbol: String,
    bmsLifetimeKm: Double?,
    bmsLifetimeKwh: Double?,
    nominalCapacityKwh: Double,
    sohSeries: List<Float>,
    cellDeltaSeries: List<Float>,
    batTempSeries: List<Float>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel(stringResource(R.string.charges_stats_period_label))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            StatRow(stringResource(R.string.charges_stats_sessions), "${periodSummary.sessionCount}", TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            StatRow(stringResource(R.string.charges_col_kwh), "%.1f".format(periodSummary.totalKwh), AccentGreen)
            Spacer(modifier = Modifier.height(4.dp))
            StatRow(stringResource(R.string.charges_stats_cost), "%.2f %s".format(periodSummary.totalCost, currencySymbol), TextPrimary)
        }

        SectionLabel(stringResource(R.string.charges_stats_lifetime_label))
        if (bmsLifetimeKwh != null) {
            val equiv = if (nominalCapacityKwh > 0) bmsLifetimeKwh / nominalCapacityKwh else 0.0
            val avgPer100 = if (bmsLifetimeKm != null && bmsLifetimeKm > 0)
                bmsLifetimeKwh / bmsLifetimeKm * 100.0 else null
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                StatRow(stringResource(R.string.charges_stats_pumped_total), stringResource(R.string.charges_kwh_value, bmsLifetimeKwh), AccentGreen)
                Spacer(modifier = Modifier.height(4.dp))
                StatRow(stringResource(R.string.charges_stats_equiv_cycles), "%.1f".format(equiv), TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                StatRow(
                    stringResource(R.string.charges_stats_bms_mileage),
                    bmsLifetimeKm?.let { stringResource(R.string.trips_km_decimal_value, it) } ?: "-",
                    TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatRow(
                    stringResource(R.string.charges_stats_per100km),
                    avgPer100?.let { stringResource(R.string.trips_kwh_decimal_value, it) } ?: "-",
                    AccentBlue
                )
            }

            if (sohSeries.size >= 2) {
                MiniLineChart(
                    series = sohSeries,
                    title = "健康度（%）",
                    lineColor = AccentGreen,
                    valueFormat = { "%.0f".format(it) }
                )
            }
        } else {
            PlaceholderBlock(stringResource(R.string.charges_lifetime_waiting))
        }

        SectionLabel(stringResource(R.string.charges_stats_dynamics_label))
        if (cellDeltaSeries.size >= 3) {
            MiniLineChart(
                series = cellDeltaSeries,
                title = stringResource(R.string.charges_chart_cell_delta_title),
                lineColor = AccentBlue,
                valueFormat = { "%.3f".format(it) }
            )
        } else {
            ChartPlaceholder(stringResource(R.string.charges_chart_cell_delta_placeholder, cellDeltaSeries.size))
        }
        if (batTempSeries.size >= 3) {
            MiniLineChart(
                series = batTempSeries,
                title = stringResource(R.string.charges_chart_bat_temp_title),
                lineColor = AccentOrange,
                valueFormat = { "%.1f".format(it) }
            )
        } else {
            ChartPlaceholder(stringResource(R.string.charges_chart_bat_temp_placeholder, batTempSeries.size))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun PlaceholderBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(text, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ChartPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MiniLineChart(
    series: List<Float>,
    title: String,
    lineColor: Color,
    valueFormat: (Float) -> String = { "%.2f".format(it) }
) {
    val minVal = series.min()
    val maxVal = series.max()
    val isFlat = maxVal - minVal < 0.005f
    val latest = series.last()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        // Title + latest value on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextSecondary, fontSize = 11.sp)
            Text(
                valueFormat(latest),
                color = lineColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Y-axis labels: max on top, min on bottom. For a flat series we
            // collapse to one row centered vertically next to the line.
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .height(60.dp),
                verticalArrangement = if (isFlat) Arrangement.Center else Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                if (isFlat) {
                    Text(
                        valueFormat(latest),
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        valueFormat(maxVal),
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        valueFormat(minVal),
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            val chartGridColor = ChartGrid
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                val w = size.width
                val h = size.height
                val pad = 4.dp.toPx()

                // Top + bottom grid lines so the chart has a clear frame.
                drawLine(
                    color = chartGridColor,
                    start = Offset(pad, pad),
                    end = Offset(w - pad, pad),
                    strokeWidth = 0.5.dp.toPx()
                )
                drawLine(
                    color = chartGridColor,
                    start = Offset(pad, h - pad),
                    end = Offset(w - pad, h - pad),
                    strokeWidth = 0.5.dp.toPx()
                )

                val path = Path()
                series.forEachIndexed { index, value ->
                    val x = pad + (index.toFloat() / (series.size - 1)) * (w - pad * 2)
                    val y = if (isFlat) {
                        // Constant series: draw line at vertical center instead
                        // of pinning it to the bottom (range≈0 collapses y to h-pad).
                        h / 2f
                    } else {
                        val range = maxVal - minVal
                        pad + (1f - (value - minVal) / range) * (h - pad * 2)
                    }
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ─── Chip ─────────────────────────────────────────────────────────────────────

@Composable
private fun ChargesChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.height(44.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
            containerColor = CardSurface,
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected
        )
    )
}

private fun formatChargeType(type: String?): String = when (type) {
    "AC" -> "慢充"
    "DC" -> "快充"
    null -> "—"
    else -> type
}
