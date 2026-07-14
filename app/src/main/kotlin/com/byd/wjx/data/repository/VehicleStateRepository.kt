package com.byd.wjx.data.repository

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

enum class VehicleDataFreshness {
    UNAVAILABLE,
    FRESH,
    STALE,
    DISCONNECTED,
}

/**
 * Canonical latest vehicle FID state. Session, range and location state are owned by
 * TrackingStateRepository so vehicle sampling remains independent from service lifecycle details.
 */
data class VehicleState(
    val fullSnapshot: VehicleSnapshot? = null,
    val actionSnapshot: VehicleSnapshot? = null,
    val connected: Boolean = false,
    val freshness: VehicleDataFreshness = VehicleDataFreshness.UNAVAILABLE,
) {
    val data: DiParsData? get() = fullSnapshot?.data
    val actionData: DiParsData? get() = actionSnapshot?.data
    val lastUpdatedAtMs: Long? get() = fullSnapshot?.readCompletedAtMs

    fun freshnessAt(
        nowMs: Long,
        staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
    ): VehicleDataFreshness {
        val snapshot = fullSnapshot ?: return VehicleDataFreshness.UNAVAILABLE
        if (!connected) return VehicleDataFreshness.DISCONNECTED
        val ageMs = (nowMs - snapshot.readCompletedAtMs).coerceAtLeast(0L)
        return if (ageMs <= staleAfterMs) {
            VehicleDataFreshness.FRESH
        } else {
            VehicleDataFreshness.STALE
        }
    }

    companion object {
        // The slowest normal cadence is 30 s while idle. Leave enough margin for one
        // delayed tick without treating a valid parked snapshot as stale.
        const val DEFAULT_STALE_AFTER_MS = 65_000L
    }
}

interface VehicleStateRepository {
    val fullSnapshot: StateFlow<VehicleSnapshot?>
    val actionSnapshot: StateFlow<VehicleSnapshot?>
    val connected: StateFlow<Boolean>
    val freshness: StateFlow<VehicleDataFreshness>
    val state: StateFlow<VehicleState>
}

class DefaultVehicleStateRepository(
    override val fullSnapshot: StateFlow<VehicleSnapshot?>,
    override val actionSnapshot: StateFlow<VehicleSnapshot?>,
    override val connected: StateFlow<Boolean>,
    applicationScope: CoroutineScope,
) : VehicleStateRepository {

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(FRESHNESS_TICK_MS)
        }
    }

    override val freshness: StateFlow<VehicleDataFreshness> = combine(
        fullSnapshot,
        connected,
        clock,
    ) { full, isConnected, nowMs ->
        VehicleState(
            fullSnapshot = full,
            connected = isConnected,
        ).freshnessAt(nowMs)
    }.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = VehicleState(
            fullSnapshot = fullSnapshot.value,
            connected = connected.value,
        ).freshnessAt(System.currentTimeMillis()),
    )

    override val state: StateFlow<VehicleState> = combine(
        fullSnapshot,
        actionSnapshot,
        connected,
        freshness,
    ) { full, action, isConnected, dataFreshness ->
        VehicleState(
            fullSnapshot = full,
            actionSnapshot = action,
            connected = isConnected,
            freshness = dataFreshness,
        )
    }.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = VehicleState(
            fullSnapshot = fullSnapshot.value,
            actionSnapshot = actionSnapshot.value,
            connected = connected.value,
            freshness = freshness.value,
        ),
    )

    private companion object {
        const val FRESHNESS_TICK_MS = 5_000L
    }
}
