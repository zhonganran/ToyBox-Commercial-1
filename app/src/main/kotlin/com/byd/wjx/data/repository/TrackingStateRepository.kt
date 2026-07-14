package com.byd.wjx.data.repository

import android.location.Location
import com.byd.wjx.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TrackingRuntimeState(
    val isRunning: Boolean = false,
    val rangeKm: Double? = null,
    val sessionStartedAt: Long? = null,
    val tripDistanceKm: Double? = null,
    val lastLocation: Location? = null,
    val cameraActive: Boolean = false,
)

interface TrackingStateRepository {
    val isRunning: StateFlow<Boolean>
    val rangeKm: StateFlow<Double?>
    val sessionStartedAt: StateFlow<Long?>
    val tripDistanceKm: StateFlow<Double?>
    val lastLocation: StateFlow<Location?>
    val cameraActive: StateFlow<Boolean>
    val state: StateFlow<TrackingRuntimeState>
}

/**
 * Process-level owner for state produced by TrackingService but consumed elsewhere.
 * Vehicle FID snapshots belong to VehicleStateRepository and are intentionally separate.
 */
@Singleton
class TrackingStateStore @Inject constructor(
    @ApplicationScope applicationScope: CoroutineScope,
) : TrackingStateRepository {
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _rangeKm = MutableStateFlow<Double?>(null)
    override val rangeKm: StateFlow<Double?> = _rangeKm.asStateFlow()

    private val _sessionStartedAt = MutableStateFlow<Long?>(null)
    override val sessionStartedAt: StateFlow<Long?> = _sessionStartedAt.asStateFlow()

    private val _tripDistanceKm = MutableStateFlow<Double?>(null)
    override val tripDistanceKm: StateFlow<Double?> = _tripDistanceKm.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    override val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    private val _cameraActive = MutableStateFlow(false)
    override val cameraActive: StateFlow<Boolean> = _cameraActive.asStateFlow()

    private val tripState = combine(_sessionStartedAt, _tripDistanceKm) { startedAt, distanceKm ->
        startedAt to distanceKm
    }

    override val state: StateFlow<TrackingRuntimeState> = combine(
        _isRunning,
        _rangeKm,
        tripState,
        _lastLocation,
        _cameraActive,
    ) { running, range, trip, location, camera ->
        TrackingRuntimeState(
            isRunning = running,
            rangeKm = range,
            sessionStartedAt = trip.first,
            tripDistanceKm = trip.second,
            lastLocation = location,
            cameraActive = camera,
        )
    }.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = TrackingRuntimeState(),
    )

    internal fun setRunning(value: Boolean) {
        _isRunning.value = value
    }

    internal fun setRangeKm(value: Double?) {
        _rangeKm.value = value
    }

    internal fun setSessionStartedAt(value: Long?) {
        _sessionStartedAt.value = value
    }

    internal fun setTripDistanceKm(value: Double?) {
        _tripDistanceKm.value = value
    }

    internal fun setLastLocation(value: Location?) {
        _lastLocation.value = value?.let(::Location)
    }

    internal fun setCameraActive(value: Boolean) {
        _cameraActive.value = value
    }
}
