package com.byd.wjx.ui.charges

import com.byd.wjx.data.autoservice.ChargingReading
import com.byd.wjx.data.local.dao.ChargeSummary
import com.byd.wjx.data.local.entity.ChargeEntity

enum class ChargesPeriod { TODAY, WEEK, MONTH, YEAR, ALL }

enum class ChargeTypeFilter { ALL, AC, DC }

data class ChargesMonthGroup(
    val yearMonth: String,
    val label: String,
    val totalKwh: Double,
    val sessionCount: Int,
    val totalCost: Double,
    val days: List<ChargesDayGroup>,
)

data class ChargesDayGroup(
    val date: String,
    val label: String,
    val dayOfWeek: String,
    val totalKwh: Double,
    val sessionCount: Int,
    val totalCost: Double,
    val charges: List<ChargeEntity>,
)

data class ChargesUiState(
    val period: ChargesPeriod = ChargesPeriod.MONTH,
    val typeFilter: ChargeTypeFilter = ChargeTypeFilter.ALL,
    val months: List<ChargesMonthGroup> = emptyList(),
    val expandedMonths: Set<String> = emptySet(),
    val expandedDays: Set<String> = emptySet(),
    val periodSummary: ChargeSummary = ChargeSummary(0, 0.0, 0.0),
    val currencySymbol: String = "元",
    val initialAutoserviceCheckDone: Boolean = false,
    val autoserviceConnected: Boolean = false,
    val autoserviceAllSentinel: Boolean = false,
    val lifetimeAcKwh: Double = 0.0,
    val lifetimeDcKwh: Double = 0.0,
    val lifetimeTotalKwh: Double = 0.0,
    val equivCycles: Double = 0.0,
    val nominalCapacityKwh: Double = 72.9,
    val sohFactor: Double = 1.0,
    val sohSeries: List<Float> = emptyList(),
    val capacitySeries: List<Float> = emptyList(),
    val bmsLifetimeKm: Double? = null,
    val bmsLifetimeKwh: Double? = null,
    val cellDeltaSeries: List<Float> = emptyList(),
    val batTempSeries: List<Float> = emptyList(),
    val selectedChargeForAction: ChargeEntity? = null,
    val editingCharge: ChargeEntity? = null,
    val deleteConfirmCharge: ChargeEntity? = null,
    val homeTariff: Double = 0.60,
    val dcTariff: Double = 1.20,
    val liveCharging: ChargingReading? = null,
) {
    val effectiveCapacityKwh: Double get() = nominalCapacityKwh * sohFactor
}
