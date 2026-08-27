package com.byd.wjx.ui.map

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.data.dashcam.DashcamFolderRepository
import com.byd.wjx.data.dashcam.DashcamVideoClip
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isLoading: Boolean = true,
    val period: MapPeriod = MapPeriod.WEEK,
    val totalKm: Double = 0.0,
    val totalKwh: Double = 0.0,
    val totalFuelL: Double = 0.0,
    val totalElectricCost: Double = 0.0,
    val totalFuelCost: Double = 0.0,
    val totalDurationMs: Long = 0L,
    val avgConsumption: Double = 0.0,
    val avgFuelConsumption: Double = 0.0,
    val trips: List<TripEntity> = emptyList(),
    val selectedTripId: Long? = null,
    val dashcamFolderName: String? = null,
    val dashcamVideoCount: Int = 0,
    val dashcamVideosByTrip: Map<Long, List<DashcamVideoClip>> = emptyMap(),
    val dashcamScanning: Boolean = false,
    val dashcamError: String? = null,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val dashcamFolderRepository: DashcamFolderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var latestDashcamClips: List<DashcamVideoClip> = emptyList()

    init {
        viewModelScope.launch {
            dashcamFolderRepository.state.collect { library ->
                latestDashcamClips = library.clips
                _uiState.update { state ->
                    state.copy(
                        dashcamFolderName = library.folderName,
                        dashcamVideoCount = library.clips.size,
                        dashcamVideosByTrip = DashcamTripMatcher.match(state.trips, library.clips),
                        dashcamScanning = library.scanning,
                        dashcamError = library.error,
                    )
                }
            }
        }
        loadData()
        refreshDashcamVideos()
    }

    fun setPeriod(period: MapPeriod) {
        _uiState.update { it.copy(period = period, selectedTripId = null, isLoading = true) }
        loadData()
    }

    fun selectTrip(tripId: Long?) {
        _uiState.update { it.copy(selectedTripId = tripId) }
    }

    fun selectDashcamFolder(uri: Uri) {
        viewModelScope.launch { dashcamFolderRepository.selectFolder(uri) }
    }

    fun refreshDashcamVideos() {
        viewModelScope.launch { dashcamFolderRepository.refresh() }
    }

    fun clearDashcamFolder() {
        viewModelScope.launch { dashcamFolderRepository.clearFolder() }
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
            tripsFlow.collect { trips ->
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
                val totalFuelL = trips.mapNotNull { it.fuelConsumedL }.sum()
                val totalElectricCost = trips.mapNotNull { it.cost }.sum()
                val totalFuelCost = trips.mapNotNull { it.fuelCost }.sum()
                val totalDurationMs = trips.sumOf { trip ->
                    ((trip.endTs ?: System.currentTimeMillis()) - trip.startTs).coerceAtLeast(0L)
                }
                val avg = if (totalKm > 0) totalKwh / totalKm * 100.0 else 0.0
                val avgFuel = if (totalKm > 0) totalFuelL / totalKm * 100.0 else 0.0
                val data = MapData(
                    routes = allRoutes,
                    trips = trips,
                    totalKm = totalKm,
                    totalKwh = totalKwh,
                    totalFuelL = totalFuelL,
                    totalElectricCost = totalElectricCost,
                    totalFuelCost = totalFuelCost,
                    totalDurationMs = totalDurationMs,
                    avgConsumption = avg,
                    avgFuelConsumption = avgFuel,
                )
                _uiState.update { state ->
                    val selectedTripId = state.selectedTripId?.takeIf { selectedId ->
                        data.routes.any { it.trip.id == selectedId }
                    }
                    state.copy(
                        tripRoutes = data.routes,
                        trips = data.trips,
                        totalKm = data.totalKm,
                        totalKwh = data.totalKwh,
                        totalFuelL = data.totalFuelL,
                        totalElectricCost = data.totalElectricCost,
                        totalFuelCost = data.totalFuelCost,
                        totalDurationMs = data.totalDurationMs,
                        avgConsumption = data.avgConsumption,
                        avgFuelConsumption = data.avgFuelConsumption,
                        selectedTripId = selectedTripId,
                        dashcamVideosByTrip = DashcamTripMatcher.match(data.trips, latestDashcamClips),
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
    val totalKm: Double,
    val totalKwh: Double,
    val totalFuelL: Double,
    val totalElectricCost: Double,
    val totalFuelCost: Double,
    val totalDurationMs: Long,
    val avgConsumption: Double,
    val avgFuelConsumption: Double,
)
