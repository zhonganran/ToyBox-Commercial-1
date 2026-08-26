package com.byd.wjx.data.dashboard

import com.byd.wjx.data.energy.EnergyFlowStatsRepository
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.data.repository.VehicleDataFreshness
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.domain.calculator.ConsumptionAggregator
import com.byd.wjx.domain.calculator.ConsumptionState
import com.byd.wjx.domain.energy.EnergyFlowStats
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * The repository-facing snapshot consumed by the dashboard state mapper.
 *
 * This type deliberately contains no Compose or UI state. It is the stable boundary between
 * vehicle/tracking/settings streams and the dashboard presentation model.
 */
data class DashboardLiveSnapshot(
    val data: DiParsData?,
    val dataUpdatedAt: Long?,
    val running: Boolean,
    val connected: Boolean,
    val sessionStartedAt: Long?,
    val tripDistanceKm: Double?,
    val consumption: ConsumptionState,
    val energyFlow: EnergyFlowStats,
    val batteryCapacityKwh: Double?,
    val fuelTankCapacityLiters: Double?,
    val tripElectricTariff: Double,
    val fuelPricePerLiter: Double,
)

/** Combines the live streams needed by the dashboard without knowing anything about Compose. */
@Reusable
class DashboardLiveDataCoordinator @Inject constructor(
    private val vehicleStateRepository: VehicleStateRepository,
    private val trackingStateRepository: TrackingStateRepository,
    private val consumptionAggregator: ConsumptionAggregator,
    private val energyFlowStatsRepository: EnergyFlowStatsRepository,
    private val settingsRepository: SettingsRepository,
) {
    fun snapshots(): Flow<DashboardLiveSnapshot> {
        val liveMetrics = combine(
            consumptionAggregator.state,
            energyFlowStatsRepository.state,
        ) { consumption, energyFlow ->
            consumption to energyFlow
        }
        val liveSettings = combine(
            settingsRepository.observeBatteryCapacity(),
            settingsRepository.observeFuelTankCapacity(),
            settingsRepository.observeTripCostTariff(),
            settingsRepository.observeFuelPricePerLiter(),
        ) { batteryCapacity, fuelTankCapacity, tripElectricTariff, fuelPricePerLiter ->
            LiveSettings(
                batteryCapacityKwh = batteryCapacity,
                fuelTankCapacityLiters = fuelTankCapacity,
                tripElectricTariff = tripElectricTariff,
                fuelPricePerLiter = fuelPricePerLiter,
            )
        }
        return combine(
            vehicleStateRepository.state,
            trackingStateRepository.state,
            liveMetrics,
            liveSettings,
        ) { vehicleState, trackingState, metrics, settings ->
            DashboardLiveSnapshot(
                data = vehicleState.data,
                dataUpdatedAt = vehicleState.lastUpdatedAtMs,
                connected = vehicleState.connected &&
                    vehicleState.freshness == VehicleDataFreshness.FRESH,
                running = trackingState.isRunning,
                sessionStartedAt = trackingState.sessionStartedAt,
                tripDistanceKm = trackingState.tripDistanceKm,
                consumption = metrics.first,
                energyFlow = metrics.second,
                batteryCapacityKwh = settings.batteryCapacityKwh,
                fuelTankCapacityLiters = settings.fuelTankCapacityLiters,
                tripElectricTariff = settings.tripElectricTariff,
                fuelPricePerLiter = settings.fuelPricePerLiter,
            )
        }
    }

    private data class LiveSettings(
        val batteryCapacityKwh: Double?,
        val fuelTankCapacityLiters: Double?,
        val tripElectricTariff: Double,
        val fuelPricePerLiter: Double,
    )
}
