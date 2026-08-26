package com.byd.wjx.ui.charges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.data.autoservice.ChargingReading
import com.byd.wjx.data.charging.AutoserviceChargingDetector
import com.byd.wjx.data.charging.ChargingTelemetryReader
import com.byd.wjx.data.charging.ChargingTelemetrySemantics
import com.byd.wjx.data.local.dao.BatterySnapshotDao
import com.byd.wjx.data.local.dao.ChargeSummary
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.domain.battery.BatteryStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChargesViewModel @Inject constructor(
    private val chargeRepository: ChargeRepository,
    private val batterySnapshotDao: BatterySnapshotDao,
    private val settingsRepository: SettingsRepository,
    private val batteryStateRepository: BatteryStateRepository,
    private val chargingTelemetryReader: ChargingTelemetryReader,
    private val groupBuilder: ChargesGroupBuilder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargesUiState())
    val uiState: StateFlow<ChargesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var liveTelemetryJob: Job? = null

    init {
        viewModelScope.launch {
            val nominal = settingsRepository.getBatteryCapacity()
            val home = settingsRepository.getHomeTariff()
            val dc = settingsRepository.getDcTariff()
            _uiState.update {
                it.copy(
                    nominalCapacityKwh = nominal,
                    homeTariff = home,
                    dcTariff = dc,
                )
            }
        }
        loadAutoserviceState()
        loadAll()
        loadLifetimeStats()
        loadHealthSeries()
    }

    fun setPeriod(period: ChargesPeriod) {
        _uiState.update {
            it.copy(period = period, expandedMonths = emptySet(), expandedDays = emptySet())
        }
        loadAll(period = period)
    }

    fun setTypeFilter(filter: ChargeTypeFilter) {
        _uiState.update {
            it.copy(typeFilter = filter, expandedMonths = emptySet(), expandedDays = emptySet())
        }
        loadAll(typeFilter = filter)
    }

    fun toggleMonth(yearMonth: String) {
        _uiState.update { state ->
            val expanded = state.expandedMonths.toMutableSet()
            if (yearMonth in expanded) expanded.remove(yearMonth) else expanded.add(yearMonth)
            state.copy(expandedMonths = expanded)
        }
    }

    fun toggleDay(date: String) {
        _uiState.update { state ->
            val expanded = state.expandedDays.toMutableSet()
            if (date in expanded) expanded.remove(date) else expanded.add(date)
            state.copy(expandedDays = expanded)
        }
    }

    /**
     * The charging page owns this poller. Leaving the page cancels it immediately, and the reader
     * only adds the six electrical/diagnostic FIDs after the base snapshot confirms an active
     * charging session.
     */
    fun setLiveTelemetryActive(active: Boolean) {
        if (!active) {
            liveTelemetryJob?.cancel()
            liveTelemetryJob = null
            _uiState.update { it.copy(liveCharging = null) }
            return
        }
        if (liveTelemetryJob?.isActive == true) return
        liveTelemetryJob = viewModelScope.launch {
            while (isActive) {
                val reading = runCatching { chargingTelemetryReader.read() }.getOrNull()
                val live = reading.takeIf(ChargingTelemetrySemantics::isChargingSession)
                _uiState.update { it.copy(liveCharging = live) }
                delay(
                    if (live != null) LIVE_CHARGING_REFRESH_MS
                    else IDLE_CHARGING_REFRESH_MS,
                )
            }
        }
    }

    private fun loadAll(
        period: ChargesPeriod = _uiState.value.period,
        typeFilter: ChargeTypeFilter = _uiState.value.typeFilter
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val (from, to) = ChargesTimeRanges.forPeriod(period)

            chargeRepository.getChargesByDateRange(from, to).collect { rawCharges ->
                val nonEmpty = rawCharges.filter { (it.kwhCharged ?: 0.0) >= 0.05 }
                val filtered = when (typeFilter) {
                    ChargeTypeFilter.ALL -> nonEmpty
                    // gunState is the raw autoservice value and on Leopard 3 returns
                    // sentinel -10011 (FID_GUN_CONNECT_STATE closed). Filter by `type`
                    // which the classifier resolves correctly (gun→observed power→heuristic).
                    ChargeTypeFilter.AC -> nonEmpty.filter { it.type == "AC" }
                    ChargeTypeFilter.DC -> nonEmpty.filter { it.type == "DC" }
                }

                val months = groupBuilder.build(filtered)

                val periodSummary = ChargeSummary(
                    sessionCount = filtered.size,
                    totalKwh = filtered.sumOf { it.kwhCharged ?: 0.0 },
                    totalCost = filtered.sumOf { it.cost ?: 0.0 }
                )

                val autoExpandMonth = months.firstOrNull()?.yearMonth
                val autoExpandDay = months.firstOrNull()?.days?.firstOrNull()?.date

                _uiState.update { s ->
                    s.copy(
                        months = months,
                        periodSummary = periodSummary,
                        expandedMonths = if (s.expandedMonths.isEmpty() && autoExpandMonth != null)
                            setOf(autoExpandMonth) else s.expandedMonths,
                        expandedDays = if (s.expandedDays.isEmpty() && autoExpandDay != null)
                            setOf(autoExpandDay) else s.expandedDays
                    )
                }
            }
        }
    }

    private fun loadAutoserviceState() {
        viewModelScope.launch {
            // Mark the initial gate before the slow Binder probe. This suppresses the
            // OnboardingEmptyState flash that was visible during the ~1-2 s Binder probe.
            _uiState.update { it.copy(initialAutoserviceCheckDone = true) }

            // Slow Binder probe: update connection, sentinel and BMS lifetime fields.
            val disconnectedDefault = com.byd.wjx.domain.battery.BatteryState(
                socNow = null, voltage12v = null, sohPercent = null,
                lifetimeKm = null, lifetimeKwh = null, autoserviceAvailable = false
            )
            val state = runCatching { batteryStateRepository.refresh() }
                .getOrDefault(disconnectedDefault)

            val connected = state.autoserviceAvailable
            val allSentinel = connected &&
                    state.socNow == null && state.lifetimeKm == null && state.lifetimeKwh == null

            _uiState.update {
                it.copy(
                    autoserviceConnected = connected,
                    autoserviceAllSentinel = allSentinel,
                    bmsLifetimeKm = state.lifetimeKm?.toDouble(),
                    bmsLifetimeKwh = state.lifetimeKwh?.toDouble(),
                    sohFactor = state.sohPercent?.toDouble()
                        ?.takeIf { soh -> soh in AutoserviceChargingDetector.SOH_SANITY_MIN..100.0 }
                        ?.div(100.0) ?: 1.0,
                )
            }
        }
    }

    private fun loadLifetimeStats() {
        viewModelScope.launch {
            val stats = runCatching { chargeRepository.getLifetimeStats() }.getOrNull()
                ?: return@launch
            val nominal = _uiState.value.nominalCapacityKwh
            val equiv = if (nominal > 0) stats.totalKwhAdded / nominal else 0.0
            _uiState.update {
                it.copy(
                    lifetimeAcKwh = stats.acKwh,
                    lifetimeDcKwh = stats.dcKwh,
                    lifetimeTotalKwh = stats.totalKwhAdded,
                    equivCycles = equiv
                )
            }
        }
    }

    private fun loadHealthSeries() {
        viewModelScope.launch {
            val snapshots = batterySnapshotDao.getAll().first().reversed()
            val soh = snapshots.mapNotNull { it.sohPercent?.toFloat() }
            val cap = snapshots.mapNotNull { it.calculatedCapacityKwh?.toFloat() }
            val cellDelta = snapshots.mapNotNull { it.cellDeltaV?.toFloat() }
            val batTemp = snapshots.mapNotNull { it.batTempAvg?.toFloat() }
            _uiState.update {
                it.copy(
                    sohSeries = soh,
                    capacitySeries = cap,
                    cellDeltaSeries = cellDelta,
                    batTempSeries = batTemp,
                )
            }
        }
    }

    fun onLongPressCharge(charge: ChargeEntity) {
        _uiState.update { it.copy(selectedChargeForAction = charge) }
    }

    fun onDismissActionSheet() {
        _uiState.update { it.copy(selectedChargeForAction = null) }
    }

    fun onEditCharge() {
        _uiState.update { it.copy(editingCharge = it.selectedChargeForAction, selectedChargeForAction = null) }
    }

    fun onDismissEdit() {
        _uiState.update { it.copy(editingCharge = null) }
    }

    fun onCreateNewCharge() {
        val now = System.currentTimeMillis()
        val draft = ChargeEntity(
            id = 0L,
            startTs = now,
            endTs = now + 3_600_000L,
            type = "AC",
            gunState = 2,
            detectionSource = "manual",
        )
        _uiState.update { it.copy(editingCharge = draft) }
    }

    fun onSaveEdit(updated: ChargeEntity) {
        viewModelScope.launch {
            if (updated.id == 0L) {
                chargeRepository.insertCharge(updated)
            } else {
                chargeRepository.updateCharge(updated)
            }
            _uiState.update { it.copy(editingCharge = null) }
            loadAll()
            loadLifetimeStats()
        }
    }

    fun onConfirmDeletePrompt() {
        _uiState.update { it.copy(deleteConfirmCharge = it.selectedChargeForAction, selectedChargeForAction = null) }
    }

    fun onDismissDeleteConfirm() {
        _uiState.update { it.copy(deleteConfirmCharge = null) }
    }

    fun onConfirmDelete() {
        viewModelScope.launch {
            val charge = _uiState.value.deleteConfirmCharge ?: return@launch
            chargeRepository.deleteCharge(charge)
            _uiState.update { it.copy(deleteConfirmCharge = null) }
            loadAll()
            loadLifetimeStats()
        }
    }

    private companion object {
        const val LIVE_CHARGING_REFRESH_MS = 2_000L
        const val IDLE_CHARGING_REFRESH_MS = 5_000L
    }
}
