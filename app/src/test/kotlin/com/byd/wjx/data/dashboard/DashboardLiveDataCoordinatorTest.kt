package com.byd.wjx.data.dashboard

import com.byd.wjx.data.energy.EnergyFlowStatsRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TrackingRuntimeState
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.data.repository.VehicleDataFreshness
import com.byd.wjx.data.repository.VehicleState
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.domain.calculator.ConsumptionAggregator
import com.byd.wjx.domain.energy.EnergyFlowStats
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLiveDataCoordinatorTest {
    @Test
    fun `combines vehicle tracking metrics and live settings into one snapshot`() = runTest {
        val vehicleStateRepository = mockk<VehicleStateRepository>()
        every { vehicleStateRepository.state } returns MutableStateFlow(
            VehicleState(
                connected = true,
                freshness = VehicleDataFreshness.FRESH,
            ),
        )

        val trackingStateRepository = mockk<TrackingStateRepository>()
        every { trackingStateRepository.state } returns MutableStateFlow(
            TrackingRuntimeState(
                isRunning = true,
                sessionStartedAt = 1234L,
                tripDistanceKm = 4.5,
            ),
        )

        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.observeBatteryCapacity() } returns flowOf(72.9)
        every { settingsRepository.observeFuelTankCapacity() } returns flowOf(60.0)
        every { settingsRepository.observeTripCostTariff() } returns flowOf(0.6)
        every { settingsRepository.observeFuelPricePerLiter() } returns flowOf(8.2)

        val energyFlowRepository = mockk<EnergyFlowStatsRepository>()
        every { energyFlowRepository.state } returns MutableStateFlow(EnergyFlowStats())

        val snapshot = DashboardLiveDataCoordinator(
            vehicleStateRepository = vehicleStateRepository,
            trackingStateRepository = trackingStateRepository,
            consumptionAggregator = ConsumptionAggregator(),
            energyFlowStatsRepository = energyFlowRepository,
            settingsRepository = settingsRepository,
        ).snapshots().first()

        assertTrue(snapshot.connected)
        assertTrue(snapshot.running)
        assertEquals(1234L, snapshot.sessionStartedAt ?: 0L)
        assertEquals(4.5, snapshot.tripDistanceKm ?: 0.0, 0.001)
        assertEquals(72.9, snapshot.batteryCapacityKwh ?: 0.0, 0.001)
        assertEquals(60.0, snapshot.fuelTankCapacityLiters ?: 0.0, 0.001)
        assertEquals(0.6, snapshot.tripElectricTariff, 0.001)
        assertEquals(8.2, snapshot.fuelPricePerLiter, 0.001)
    }
}
