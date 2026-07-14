package com.byd.wjx.data.repository

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeTrackingStateRepository(
    initial: TrackingRuntimeState = TrackingRuntimeState(),
) : TrackingStateRepository {
    override val isRunning = MutableStateFlow(initial.isRunning)
    override val rangeKm = MutableStateFlow(initial.rangeKm)
    override val sessionStartedAt = MutableStateFlow(initial.sessionStartedAt)
    override val tripDistanceKm = MutableStateFlow(initial.tripDistanceKm)
    override val lastLocation = MutableStateFlow<Location?>(initial.lastLocation)
    override val cameraActive = MutableStateFlow(initial.cameraActive)
    override val state: StateFlow<TrackingRuntimeState> = MutableStateFlow(initial)
}
