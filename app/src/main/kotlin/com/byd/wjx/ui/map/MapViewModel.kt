package com.byd.wjx.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class MapPeriod { DAY, WEEK, MONTH, YEAR, ALL }

data class TripRoute(
    val trip: TripEntity,
    val points: List<TripPointEntity>,
    val kwhPer100km: Double?
)

data class MapUiState(
    val tripRoutes: List<TripRoute> = emptyList(),
    val charges: List<ChargeEntity> = emptyList(),
    val isLoading: Boolean = true,
    val period: MapPeriod = MapPeriod.WEEK,
    val panelExpanded: Boolean = true,
    val totalKm: Double = 0.0,
    val totalKwh: Double = 0.0,
    val avgConsumption: Double = 0.0,
    val trips: List<TripEntity> = emptyList(),
    val selectedTripId: Long? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val chargeRepository: ChargeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadData()
    }

    fun setPeriod(period: MapPeriod) {
        _uiState.update { it.copy(period = period, selectedTripId = null, isLoading = true) }
        loadData()
    }

    fun togglePanel() {
        _uiState.update { it.copy(panelExpanded = !it.panelExpanded) }
    }

    fun selectTrip(tripId: Long?) {
        _uiState.update { it.copy(selectedTripId = tripId) }
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val period = _uiState.value.period
            val range = periodRange(period)
            val tripsFlow = if (range == null) {
                tripRepository.getAllTrips()
            } else {
                tripRepository.getTripsByDateRange(range.first, range.second)
            }
            val chargesFlow = if (range == null) {
                chargeRepository.getAllCharges()
            } else {
                chargeRepository.getChargesByDateRange(range.first, range.second)
            }

            combine(tripsFlow, chargesFlow) { trips, charges ->
                    val tripIds = trips.map { it.id }
                    val pointsByTrip = if (tripIds.isEmpty()) {
                        emptyMap()
                    } else {
                        tripRepository.getTripPointsByTripIds(tripIds)
                    }
                    val allRoutes = trips.mapNotNull { trip ->
                        val points = pointsByTrip[trip.id]
                        if (!points.isNullOrEmpty()) TripRoute(trip, points, trip.kwhPer100km) else null
                    }
                    val totalKm = trips.mapNotNull { it.distanceKm }.sum()
                    val totalKwh = trips.mapNotNull { it.kwhConsumed }.sum()
                    val avg = if (totalKm > 0) totalKwh / totalKm * 100.0 else 0.0
                    MapData(
                        routes = allRoutes,
                        trips = trips,
                        charges = charges,
                        totalKm = totalKm,
                        totalKwh = totalKwh,
                        avgConsumption = avg
                    )
            }.collect { data ->
                _uiState.update { state ->
                    val selectedTripId = state.selectedTripId?.takeIf { selectedId ->
                        data.routes.any { it.trip.id == selectedId }
                    }
                    state.copy(
                        tripRoutes = data.routes,
                        trips = data.trips,
                        charges = data.charges,
                        totalKm = data.totalKm,
                        totalKwh = data.totalKwh,
                        avgConsumption = data.avgConsumption,
                        selectedTripId = selectedTripId,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun periodRange(period: MapPeriod): Pair<Long, Long>? {
        if (period == MapPeriod.ALL) return null

        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        if (period != MapPeriod.DAY) {
            val days = when (period) {
                MapPeriod.DAY -> 0
                MapPeriod.WEEK -> 7
                MapPeriod.MONTH -> 30
                MapPeriod.YEAR -> 365
                MapPeriod.ALL -> 0
            }
            cal.add(Calendar.DAY_OF_YEAR, -days)
        }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return Pair(cal.timeInMillis, end)
    }
}

private data class MapData(
    val routes: List<TripRoute>,
    val trips: List<TripEntity>,
    val charges: List<ChargeEntity>,
    val totalKm: Double,
    val totalKwh: Double,
    val avgConsumption: Double
)
