package com.byd.wjx.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.data.dashboard.DashboardLiveDataCoordinator
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.nativestack.OnDemandVehicleTelemetryReader
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.data.system.SystemMetricsReader
import com.byd.wjx.domain.insight.LocalInsightEngine
import com.byd.wjx.domain.insight.LocalInsightResult
import com.byd.wjx.domain.insight.LocalInsightStatsAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val chargeRepository: ChargeRepository,
    private val settingsRepository: SettingsRepository,
    private val idleDrainDao: IdleDrainDao,
    private val localInsightStatsAggregator: LocalInsightStatsAggregator,
    private val localInsightEngine: LocalInsightEngine,
    private val onDemandTelemetryReader: OnDemandVehicleTelemetryReader,
    private val systemMetricsReader: SystemMetricsReader,
    private val liveDataCoordinator: DashboardLiveDataCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var homeTelemetryJob: Job? = null
    private var powertrainTelemetryJob: Job? = null
    private var systemMetricsJob: Job? = null

    init {
        observeLiveData()
        observeLastTrip()
        observeRecentTrips()
        observeLatestCharge()
        loadPeriodSummary()
        loadLocalInsight()
    }

    fun setPeriod(period: DashboardPeriod) {
        _uiState.update { it.copy(period = period) }
        loadPeriodSummary()
    }

    fun setHomeTelemetryActive(active: Boolean) {
        if (!active) {
            homeTelemetryJob?.cancel()
            homeTelemetryJob = null
            return
        }
        if (homeTelemetryJob?.isActive == true) return
        homeTelemetryJob = viewModelScope.launch {
            var cycle = 0
            while (isActive) {
                if (cycle % TIRE_TELEMETRY_EVERY_HOME_CYCLES == 0) {
                    runCatching { onDemandTelemetryReader.readTireTelemetry() }
                        .getOrNull()
                        ?.let(::applyTireTelemetry)
                }
                cycle = (cycle + 1) % TIRE_TELEMETRY_EVERY_HOME_CYCLES
                delay(HOME_TELEMETRY_INTERVAL_MS)
            }
        }
    }

    fun setPowertrainTelemetryActive(active: Boolean) {
        if (!active) {
            powertrainTelemetryJob?.cancel()
            powertrainTelemetryJob = null
            return
        }
        if (powertrainTelemetryJob?.isActive == true) return
        powertrainTelemetryJob = viewModelScope.launch {
            while (isActive) {
                runCatching { onDemandTelemetryReader.readPowertrainTelemetry() }
                    .getOrNull()
                    ?.let { data ->
                        _uiState.update {
                            it.copy(
                                frontMotorSpeed = data.frontMotorSpeed ?: it.frontMotorSpeed,
                                rearMotorSpeed = data.rearMotorSpeed ?: it.rearMotorSpeed,
                                frontMotorTemp = data.frontMotorTemp ?: it.frontMotorTemp,
                                rearMotorTemp = data.rearMotorTemp ?: it.rearMotorTemp,
                                frontMotorCurrent = data.frontMotorCurrent ?: it.frontMotorCurrent,
                                rearMotorCurrent = data.rearMotorCurrent ?: it.rearMotorCurrent,
                                engineRpm = data.engineRpm ?: it.engineRpm,
                                waterTemp = data.waterTemp ?: it.waterTemp,
                            )
                        }
                    }
                delay(POWERTRAIN_TELEMETRY_INTERVAL_MS)
            }
        }
    }

    fun setSystemMetricsActive(active: Boolean) {
        if (!active) {
            systemMetricsJob?.cancel()
            systemMetricsJob = null
            return
        }
        if (systemMetricsJob?.isActive == true) return
        systemMetricsJob = viewModelScope.launch {
            while (isActive) {
                val snapshot = runCatching { systemMetricsReader.readSnapshot() }.getOrNull()
                _uiState.update { current ->
                    current.copy(
                        systemMetrics = snapshot ?: current.systemMetrics,
                        systemMetricsLoading = false,
                        systemMetricsSampleCount = if (snapshot != null) {
                            (current.systemMetricsSampleCount + 1).coerceAtMost(2)
                        } else {
                            current.systemMetricsSampleCount
                        },
                    )
                }
                delay(SYSTEM_METRICS_INTERVAL_MS)
            }
        }
    }

    private fun applyTireTelemetry(data: com.byd.wjx.data.remote.DiParsData) {
        _uiState.update {
            it.copy(
                tireTempFL = data.tireTempFL ?: it.tireTempFL,
                tireTempFR = data.tireTempFR ?: it.tireTempFR,
                tireTempRL = data.tireTempRL ?: it.tireTempRL,
                tireTempRR = data.tireTempRR ?: it.tireTempRR,
                tireStateFL = data.tireStateFL ?: it.tireStateFL,
                tireStateFR = data.tireStateFR ?: it.tireStateFR,
                tireStateRL = data.tireStateRL ?: it.tireStateRL,
                tireStateRR = data.tireStateRR ?: it.tireStateRR,
            )
        }
    }

    private fun observeLiveData() {
        viewModelScope.launch {
            liveDataCoordinator.snapshots().collect { snapshot ->
                _uiState.update { current -> DashboardLiveStateMapper.map(current, snapshot) }
            }
        }
    }

    /** Observe the most recent trip from the database. */
    private fun observeLastTrip() {
        viewModelScope.launch {
            tripRepository.getLastTrip().collect { trip ->
                _uiState.update { it.copy(lastTrip = trip) }
            }
        }
    }

    private fun observeRecentTrips() {
        viewModelScope.launch {
            tripRepository.getRecentTrips(7).collect { trips ->
                _uiState.update { it.copy(recentTrips = trips) }
            }
        }
    }

    private fun observeLatestCharge() {
        viewModelScope.launch {
            chargeRepository.getLastCharge().collect { charge ->
                _uiState.update { it.copy(latestCharge = charge) }
            }
        }
    }

    /** Loads trip totals for the selected period plus today's idle-drain summary. */
    private fun loadPeriodSummary() {
        viewModelScope.launch {
            val period = _uiState.value.period
            val (from, to) = DashboardTimeRanges.forPeriod(period)
            val summary = tripRepository.getPeriodSummary(from, to)
            val avg = if (summary.totalKm > 0) summary.totalKwh / summary.totalKm * 100.0 else 0.0

            // Idle drain always uses today
            val (dayStart, dayEnd) = DashboardTimeRanges.today()
            val idleDrain = idleDrainDao.getTodayDrainKwh(dayStart, dayEnd)
            val idleDrainHours = idleDrainDao.getTodayDrainHours(dayStart, dayEnd)
            val batteryCapacity = settingsRepository.getBatteryCapacity()
            val idleDrainPercent = if (batteryCapacity > 0) idleDrain / batteryCapacity * 100.0 else 0.0
            val idleDrainRate = if (idleDrainHours > 0) idleDrain / idleDrainHours else 0.0

            val weekStart = dayStart - 6 * 24 * 60 * 60 * 1000L
            val idleDrainWeek = idleDrainDao.getTodayDrainKwh(weekStart, dayEnd)
            val idleDrainHoursWeek = idleDrainDao.getTodayDrainHours(weekStart, dayEnd)

            _uiState.update {
                it.copy(
                    totalKm = summary.totalKm,
                    totalKwh = summary.totalKwh,
                    avgConsumption = avg,
                    totalCost = summary.totalCost,
                    tripCount = summary.tripCount,
                    totalKmToday = if (period == DashboardPeriod.TODAY) summary.totalKm else it.totalKmToday,
                    totalKwhToday = if (period == DashboardPeriod.TODAY) summary.totalKwh else it.totalKwhToday,
                    idleDrainKwhToday = idleDrain,
                    idleDrainPercent = idleDrainPercent,
                    idleDrainRate = idleDrainRate,
                    idleDrainHours = idleDrainHours,
                    idleDrainKwhWeek = idleDrainWeek,
                    idleDrainHoursWeek = idleDrainHoursWeek
                )
            }
        }
    }

    /** Refresh today's summary, can be called on pull-to-refresh or screen resume. */
    fun refresh() {
        loadPeriodSummary()
        loadLocalInsight()
    }

    private fun loadLocalInsight() {
        viewModelScope.launch {
            _uiState.update { it.copy(localInsightLoading = true) }
            val result = runCatching {
                localInsightEngine.analyze(localInsightStatsAggregator.build())
            }.getOrElse {
                LocalInsightResult(
                    hasEnoughData = false,
                    title = "本地洞察暂不可用",
                    summary = "读取本地行程、电池或充电记录时遇到异常，稍后刷新即可。",
                    metrics = emptyList(),
                    recommendations = listOf("该功能不依赖云端服务，异常只影响洞察卡片显示。"),
                    generatedAt = System.currentTimeMillis(),
                )
            }
            _uiState.update { it.copy(localInsight = result, localInsightLoading = false) }
        }
    }

    fun toggleBatteryHealthExpanded() {
        _uiState.update { it.copy(
            batteryHealthExpanded = !it.batteryHealthExpanded,
            idleDrainExpanded = false,
            powertrainDiagnosticsExpanded = false,
        ) }
    }

    fun toggleIdleDrainExpanded() {
        _uiState.update { it.copy(
            idleDrainExpanded = !it.idleDrainExpanded,
            batteryHealthExpanded = false,
            powertrainDiagnosticsExpanded = false,
        ) }
    }

    fun togglePowertrainDiagnosticsExpanded() {
        _uiState.update { it.copy(
            powertrainDiagnosticsExpanded = !it.powertrainDiagnosticsExpanded,
            batteryHealthExpanded = false,
            idleDrainExpanded = false,
        ) }
    }

    private companion object {
        const val HOME_TELEMETRY_INTERVAL_MS = 2_000L
        const val TIRE_TELEMETRY_EVERY_HOME_CYCLES = 5
        const val POWERTRAIN_TELEMETRY_INTERVAL_MS = 2_000L
        const val SYSTEM_METRICS_INTERVAL_MS = 5_000L
    }
}
