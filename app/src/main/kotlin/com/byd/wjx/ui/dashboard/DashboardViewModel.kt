package com.byd.wjx.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.data.repository.VehicleDataFreshness
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.domain.battery.BatteryStateRepository
import com.byd.wjx.domain.calculator.ConsumptionAggregator
import com.byd.wjx.domain.calculator.ConsumptionState
import com.byd.wjx.domain.calculator.Trend
import com.byd.wjx.domain.insight.LocalInsightEngine
import com.byd.wjx.domain.insight.LocalInsightResult
import com.byd.wjx.domain.insight.LocalInsightStatsAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * UI state for the Dashboard screen.
 * Combines repository-backed live vehicle and tracking state with aggregated
 * trip/charge statistics from the database.
 */
enum class DashboardPeriod { TODAY, WEEK, MONTH, YEAR, ALL }

data class DashboardUiState(
    val soc: Int? = null,
    val odometer: Double? = null,
    val speed: Int? = null,
    val power: Double? = null,
    val period: DashboardPeriod = DashboardPeriod.WEEK,
    val totalKm: Double = 0.0,
    val totalKwh: Double = 0.0,
    val avgConsumption: Double = 0.0,
    val totalCost: Double = 0.0,
    val tripCount: Int = 0,
    // Legacy aliases for backward compat
    val totalKmToday: Double = 0.0,
    val totalKwhToday: Double = 0.0,
    val idleDrainKwhToday: Double = 0.0,
    val lastTrip: TripEntity? = null,
    val recentTrips: List<TripEntity> = emptyList(),
    val isServiceRunning: Boolean = false,
    val currencySymbol: String = "元",
    val avgBatTemp: Int? = null,
    val maxBatTemp: Int? = null,
    val minBatTemp: Int? = null,
    val cellVoltageMin: Double? = null,
    val cellVoltageMax: Double? = null,
    val cellVoltageDelta: Double? = null,
    val voltage12v: Double? = null,
    val exteriorTemp: Int? = null,
    val batteryHealthStatus: String = "ok",
    val voltage12vStatus: String = "ok",
    val idleDrainPercent: Double = 0.0,
    val idleDrainRate: Double = 0.0,
    val idleDrainHours: Double = 0.0,
    val batteryHealthExpanded: Boolean = false,
    val idleDrainExpanded: Boolean = false,
    val idleDrainKwhWeek: Double = 0.0,
    val idleDrainHoursWeek: Double = 0.0,
    val estimatedRangeKm: Double? = null,
    val elecDrivingRangeKm: Int? = null,
    val fuelDrivingRangeKm: Int? = null,
    val combinedRangeKm: Int? = null,
    val fuelLevel: Int? = null,
    val elecConPer100km: Double? = null,
    val fuelConPer100km: Double? = null,
    val currentTripElecKwh: Double? = null,
    val currentTripFuelL: Double? = null,
    val vehicleDataConnected: Boolean = true,
    val lastVehicleDataAt: Long? = null,
    val idleDrainAvailable: Boolean = true,
    val adbConnected: Boolean? = null,
    val currentSoh: Float? = null,
    val currentLifetimeKm: Float? = null,
    val currentLifetimeKwh: Float? = null,
    // Widget-style stats around SOC ring (mirror FloatingWidgetView bindings).
    val insideTemp: Int? = null,
    val tripDistanceKm: Double? = null,
    val sessionStartedAt: Long? = null,
    val consumption: Double? = null,
    val consumptionTrend: Trend = Trend.NONE,
    val isCharging: Boolean = false,
    val gear: Int? = null,
    val powerState: Int? = null,
    val driveMode: Int? = null,
    val workMode: Int? = null,
    val chargeGunState: Int? = null,
    val chargingStatus: Int? = null,
    val chargingCapCover: Int? = null,
    val fuelCap: Int? = null,
    val acStatus: Int? = null,
    val acTemp: Int? = null,
    val fanLevel: Int? = null,
    val acCirc: Int? = null,
    val doorFL: Int? = null,
    val doorFR: Int? = null,
    val doorRL: Int? = null,
    val doorRR: Int? = null,
    val windowFL: Int? = null,
    val windowFR: Int? = null,
    val windowRL: Int? = null,
    val windowRR: Int? = null,
    val sunroof: Int? = null,
    val trunk: Int? = null,
    val hood: Int? = null,
    val lockFL: Int? = null,
    val lockFR: Int? = null,
    val lockRL: Int? = null,
    val lockRR: Int? = null,
    val seatbeltFL: Int? = null,
    val seatbeltFR: Int? = null,
    val seatbeltRL: Int? = null,
    val seatbeltRR: Int? = null,
    val tirePressFL: Int? = null,
    val tirePressFR: Int? = null,
    val tirePressRL: Int? = null,
    val tirePressRR: Int? = null,
    val lightLow: Int? = null,
    val lightHigh: Int? = null,
    val drl: Int? = null,
    val hazardLights: Int? = null,
    val turnSignalFlash: Int? = null,
    val adasApaParkingStatus: Int? = null,
    val adasAccMode: Int? = null,
    val adasLccNcaStatus: Int? = null,
    val adasTjaState: Int? = null,
    val laneKeepState: Int? = null,
    val accCruiseState: Int? = null,
    val accCruisingSpeed: Int? = null,
    val localInsight: LocalInsightResult = LocalInsightResult.initial(),
    val localInsightLoading: Boolean = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    private val idleDrainDao: IdleDrainDao,
    private val batteryStateRepository: BatteryStateRepository,
    private val vehicleStateRepository: VehicleStateRepository,
    private val trackingStateRepository: TrackingStateRepository,
    private val consumptionAggregator: ConsumptionAggregator,
    private val localInsightStatsAggregator: LocalInsightStatsAggregator,
    private val localInsightEngine: LocalInsightEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeLiveData()
        observeLastTrip()
        observeRecentTrips()
        loadCurrency()
        loadPeriodSummary()
        loadLocalInsight()
        viewModelScope.launch { loadAutoserviceFlag() }
    }

    fun setPeriod(period: DashboardPeriod) {
        _uiState.update { it.copy(period = period) }
        loadPeriodSummary()
    }

    /**
     * Collect live vehicle fields and tracking-session state from their repositories.
     */
    private data class LiveSnapshot(
        val data: com.byd.wjx.data.remote.DiParsData?,
        val dataUpdatedAt: Long?,
        val running: Boolean,
        val connected: Boolean,
        val rangeKm: Double?,
        val sessionStartedAt: Long?,
        val tripDistanceKm: Double?,
        val consumption: ConsumptionState,
    )

    private fun observeLiveData() {
        viewModelScope.launch {
            combine(
                vehicleStateRepository.state,
                trackingStateRepository.state,
                consumptionAggregator.state,
            ) { vehicleState, trackingState, consumption ->
                LiveSnapshot(
                    data = vehicleState.data,
                    dataUpdatedAt = vehicleState.lastUpdatedAtMs,
                    connected = vehicleState.connected &&
                        vehicleState.freshness == VehicleDataFreshness.FRESH,
                    running = trackingState.isRunning,
                    rangeKm = trackingState.rangeKm,
                    sessionStartedAt = trackingState.sessionStartedAt,
                    tripDistanceKm = trackingState.tripDistanceKm,
                    consumption = consumption,
                )
            }.collect { snapshot ->
                val data = snapshot.data
                val running = snapshot.running
                val connected = snapshot.connected
                val rangeKm = snapshot.rangeKm

                _uiState.update { current ->
                    val newSoc = data?.soc ?: current.soc
                    val elecRange = data?.elecDrivingRangeKm ?: current.elecDrivingRangeKm
                    val fuelRange = data?.fuelDrivingRangeKm ?: current.fuelDrivingRangeKm
                    val combinedRange = if (elecRange != null && fuelRange != null) elecRange + fuelRange else null
                    current.copy(
                        soc = newSoc,
                        speed = data?.speed ?: current.speed,
                        power = data?.power ?: current.power,
                        odometer = data?.mileage ?: current.odometer,
                        isServiceRunning = running,
                        avgBatTemp = data?.avgBatTemp ?: current.avgBatTemp,
                        maxBatTemp = data?.maxBatTemp ?: current.maxBatTemp,
                        minBatTemp = data?.minBatTemp ?: current.minBatTemp,
                        cellVoltageMin = data?.minCellVoltage ?: current.cellVoltageMin,
                        cellVoltageMax = data?.maxCellVoltage ?: current.cellVoltageMax,
                        cellVoltageDelta = if (data?.maxCellVoltage != null && data.minCellVoltage != null)
                            data.maxCellVoltage - data.minCellVoltage else current.cellVoltageDelta,
                        voltage12v = data?.voltage12v ?: current.voltage12v,
                        exteriorTemp = data?.exteriorTemp ?: current.exteriorTemp,
                        batteryHealthStatus = calculateBatteryStatus(data, current),
                        voltage12vStatus = calculate12vStatus(data?.voltage12v ?: current.voltage12v),
                        estimatedRangeKm = rangeKm ?: current.estimatedRangeKm,
                        elecDrivingRangeKm = elecRange,
                        fuelDrivingRangeKm = fuelRange,
                        combinedRangeKm = combinedRange,
                        fuelLevel = data?.fuelLevel ?: current.fuelLevel,
                        elecConPer100km = data?.elecConPer100km ?: current.elecConPer100km,
                        fuelConPer100km = data?.fuelConPer100km ?: current.fuelConPer100km,
                        currentTripElecKwh = data?.currentTripElecKwh ?: current.currentTripElecKwh,
                        currentTripFuelL = data?.currentTripFuelL ?: current.currentTripFuelL,
                        vehicleDataConnected = connected,
                        lastVehicleDataAt = snapshot.dataUpdatedAt ?: current.lastVehicleDataAt,
                        insideTemp = data?.insideTemp ?: current.insideTemp,
                        tripDistanceKm = snapshot.tripDistanceKm,
                        sessionStartedAt = snapshot.sessionStartedAt,
                        consumption = snapshot.consumption.displayValue,
                        consumptionTrend = snapshot.consumption.trend,
                        // Prefer the explicit BMS charging status, then fall back to
                        // gun-connected + negative power. Regen has gun=0, so it is filtered.
                        isCharging = data?.let(::isChargingSample) ?: current.isCharging,
                        gear = data?.gear ?: current.gear,
                        powerState = data?.powerState ?: current.powerState,
                        driveMode = data?.driveMode ?: current.driveMode,
                        workMode = data?.workMode ?: current.workMode,
                        chargeGunState = data?.chargeGunState ?: current.chargeGunState,
                        chargingStatus = data?.chargingStatus ?: current.chargingStatus,
                        chargingCapCover = data?.chargingCapCover ?: current.chargingCapCover,
                        fuelCap = data?.fuelCap ?: current.fuelCap,
                        acStatus = data?.acStatus ?: current.acStatus,
                        acTemp = data?.acTemp ?: current.acTemp,
                        fanLevel = data?.fanLevel ?: current.fanLevel,
                        acCirc = data?.acCirc ?: current.acCirc,
                        doorFL = data?.doorFL ?: current.doorFL,
                        doorFR = data?.doorFR ?: current.doorFR,
                        doorRL = data?.doorRL ?: current.doorRL,
                        doorRR = data?.doorRR ?: current.doorRR,
                        windowFL = data?.windowFL ?: current.windowFL,
                        windowFR = data?.windowFR ?: current.windowFR,
                        windowRL = data?.windowRL ?: current.windowRL,
                        windowRR = data?.windowRR ?: current.windowRR,
                        sunroof = data?.sunroof ?: current.sunroof,
                        trunk = data?.trunk ?: current.trunk,
                        hood = data?.hood ?: current.hood,
                        lockFL = data?.lockFL ?: current.lockFL,
                        lockFR = data?.lockFR ?: current.lockFR,
                        lockRL = data?.lockRL ?: current.lockRL,
                        lockRR = data?.lockRR ?: current.lockRR,
                        seatbeltFL = data?.seatbeltFL ?: current.seatbeltFL,
                        seatbeltFR = data?.seatbeltFR ?: current.seatbeltFR,
                        seatbeltRL = data?.seatbeltRL ?: current.seatbeltRL,
                        seatbeltRR = data?.seatbeltRR ?: current.seatbeltRR,
                        tirePressFL = data?.tirePressFL ?: current.tirePressFL,
                        tirePressFR = data?.tirePressFR ?: current.tirePressFR,
                        tirePressRL = data?.tirePressRL ?: current.tirePressRL,
                        tirePressRR = data?.tirePressRR ?: current.tirePressRR,
                        lightLow = data?.lightLow ?: current.lightLow,
                        lightHigh = data?.lightHigh ?: current.lightHigh,
                        drl = data?.drl ?: current.drl,
                        hazardLights = data?.hazardLights ?: current.hazardLights,
                        turnSignalFlash = data?.turnSignalFlash ?: current.turnSignalFlash,
                        adasApaParkingStatus = data?.adasApaParkingStatus ?: current.adasApaParkingStatus,
                        adasAccMode = data?.adasAccMode ?: current.adasAccMode,
                        adasLccNcaStatus = data?.adasLccNcaStatus ?: current.adasLccNcaStatus,
                        adasTjaState = data?.adasTjaState ?: current.adasTjaState,
                        laneKeepState = data?.laneKeepState ?: current.laneKeepState,
                        accCruiseState = data?.accCruiseState ?: current.accCruiseState,
                        accCruisingSpeed = data?.accCruisingSpeed ?: current.accCruisingSpeed,
                    )
                }
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

    /**
     * Load today's trip summary (total km, total kWh, trip count)
     * using Calendar to compute start/end of the current day in millis.
     */
    private fun loadCurrency() {
        viewModelScope.launch {
            val symbol = settingsRepository.getCurrencySymbol()
            val dataSource = settingsRepository.getDataSource()
            _uiState.update {
                it.copy(
                    currencySymbol = symbol,
                    idleDrainAvailable = dataSource == SettingsRepository.DataSource.ENERGYDATA
                )
            }
        }
    }

    private fun loadPeriodSummary() {
        viewModelScope.launch {
            val period = _uiState.value.period
            val (from, to) = periodRange(period)
            val summary = tripRepository.getPeriodSummary(from, to)
            val avg = if (summary.totalKm > 0) summary.totalKwh / summary.totalKm * 100.0 else 0.0

            // Idle drain always uses today
            val (dayStart, dayEnd) = todayRange()
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

    private fun periodRange(period: DashboardPeriod): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        return when (period) {
            DashboardPeriod.TODAY -> todayRange()
            DashboardPeriod.WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            DashboardPeriod.MONTH -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            DashboardPeriod.YEAR -> {
                cal.add(Calendar.YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            DashboardPeriod.ALL -> 0L to now
        }
    }

    /** Returns start and end timestamps (millis) for today. */
    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val dayEnd = cal.timeInMillis

        return Pair(dayStart, dayEnd)
    }

    /** Refresh today's summary, can be called on pull-to-refresh or screen resume. */
    fun refresh() {
        loadPeriodSummary()
        loadLocalInsight()
        viewModelScope.launch { loadAutoserviceFlag() }
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

    private suspend fun loadAutoserviceFlag() {
        val state = runCatching { batteryStateRepository.refresh() }.getOrNull()
        _uiState.update {
            if (state == null) {
                it.copy(adbConnected = false)
            } else {
                it.copy(
                    adbConnected = state.autoserviceAvailable,
                    currentSoh = state.sohPercent,
                    currentLifetimeKm = state.lifetimeKm,
                    currentLifetimeKwh = state.lifetimeKwh,
                )
            }
        }
    }

    fun toggleBatteryHealthExpanded() {
        _uiState.update { it.copy(
            batteryHealthExpanded = !it.batteryHealthExpanded,
            idleDrainExpanded = false
        ) }
    }

    fun toggleIdleDrainExpanded() {
        _uiState.update { it.copy(
            idleDrainExpanded = !it.idleDrainExpanded,
            batteryHealthExpanded = false
        ) }
    }

    private fun calculateBatteryStatus(
        data: com.byd.wjx.data.remote.DiParsData?,
        current: DashboardUiState
    ): String {
        val maxV = data?.maxCellVoltage ?: current.cellVoltageMax
        val minV = data?.minCellVoltage ?: current.cellVoltageMin
        val delta = if (maxV != null && minV != null) maxV - minV else null
        val temp = data?.avgBatTemp ?: current.avgBatTemp
        if (delta == null && temp == null) return current.batteryHealthStatus
        return when {
            (delta != null && delta > 0.10) || (temp != null && (temp < 5 || temp > 50)) -> "critical"
            (delta != null && delta > 0.05) || (temp != null && (temp < 5 || temp > 45)) -> "warning"
            else -> "ok"
        }
    }

    private fun calculate12vStatus(voltage: Double?): String {
        if (voltage == null) return "ok"
        return when {
            voltage < 11.8 -> "critical"
            voltage < 12.4 -> "warning"
            else -> "ok"
        }
    }

    private fun isChargingSample(data: com.byd.wjx.data.remote.DiParsData): Boolean {
        if (data.chargingStatus == 2) return true
        return ChargeGunState.isConnected(data.chargeGunState) && (data.power ?: 0.0) < -0.3
    }
}
