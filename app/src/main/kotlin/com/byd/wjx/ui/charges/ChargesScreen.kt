package com.byd.wjx.ui.charges

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.R
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextSecondary

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
