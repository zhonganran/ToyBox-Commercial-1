package com.byd.wjx.ui.dashboard

import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.dashboard.DashboardLiveSnapshot
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.domain.calculator.ConsumptionState
import com.byd.wjx.domain.calculator.Trend
import com.byd.wjx.domain.energy.EnergyFlowCategory
import com.byd.wjx.domain.energy.EnergyFlowStats
import com.byd.wjx.domain.energy.EnergyFlowTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLiveStateMapperTest {

    @Test
    fun `maps vehicle values and calculated dashboard fields`() {
        val data = diParsData(
            soc = 50,
            speed = 42,
            mileage = 1234.5,
            power = -1.2,
            chargeGunState = ChargeGunState.AC,
            avgBatTemp = 30,
            voltage12v = 12.0,
            maxCellVoltage = 4.02,
            minCellVoltage = 3.96,
            driveMode = 2,
            workMode = 3,
        ).copy(
            fuelLevel = 40,
            elecDrivingRangeKm = 231,
            fuelDrivingRangeKm = 642,
            currentTripElecKwh = 5.0,
            currentTripFuelL = 1.5,
        )

        val mapped = DashboardLiveStateMapper.map(
            current = DashboardUiState(),
            snapshot = snapshot(data),
        )

        assertEquals(50, mapped.soc)
        assertEquals(42, mapped.speed)
        assertEquals(1234.5, mapped.odometer ?: 0.0, 0.001)
        assertEquals(231, mapped.electricRangeKm)
        assertEquals(642, mapped.fuelRangeKm)
        assertEquals(873, mapped.combinedRangeKm)
        assertEquals(36.45, mapped.batteryRemainingKwh ?: 0.0, 0.001)
        assertEquals(24.0, mapped.fuelRemainingLiters ?: 0.0, 0.001)
        assertEquals(0.06, mapped.cellVoltageDelta ?: 0.0, 0.001)
        assertEquals("warning", mapped.batteryHealthStatus)
        assertEquals("warning", mapped.voltage12vStatus)
        assertTrue(mapped.isCharging)
        assertEquals(3.0, mapped.currentTripElecCost ?: 0.0, 0.001)
        assertEquals(12.3, mapped.currentTripFuelCost ?: 0.0, 0.001)
        assertEquals(18.5, mapped.consumption ?: 0.0, 0.001)
        assertEquals(Trend.UP, mapped.consumptionTrend)
        assertEquals(EnergyFlowCategory.ENGINE_GENERATION, mapped.energyFlowCategory)
        assertEquals(1.2, mapped.sessionEnergyFlow.engineGenerationKwh, 0.001)
        assertEquals(2.4, mapped.lifetimeEnergyFlow.engineGenerationKwh, 0.001)
    }

    @Test
    fun `fresh full snapshot clears unavailable drive and work modes`() {
        val mapped = DashboardLiveStateMapper.map(
            current = DashboardUiState(driveMode = 2, workMode = 3),
            snapshot = snapshot(diParsData(soc = 79)),
        )

        assertNull(mapped.driveMode)
        assertNull(mapped.workMode)
    }

    @Test
    fun `missing vehicle data preserves last values but reports disconnected`() {
        val mapped = DashboardLiveStateMapper.map(
            current = DashboardUiState(soc = 80, driveMode = 2, vehicleDataConnected = true),
            snapshot = snapshot(data = null, connected = false, consumption = ConsumptionState(null, Trend.NONE)),
        )

        assertEquals(80, mapped.soc)
        assertEquals(2, mapped.driveMode)
        assertFalse(mapped.vehicleDataConnected)
        assertNull(mapped.consumption)
    }

    private fun snapshot(
        data: DiParsData?,
        connected: Boolean = true,
        consumption: ConsumptionState = ConsumptionState(18.5, Trend.UP),
    ) = DashboardLiveSnapshot(
        data = data,
        dataUpdatedAt = 123_456L,
        running = true,
        connected = connected,
        sessionStartedAt = 99L,
        tripDistanceKm = 12.5,
        consumption = consumption,
        energyFlow = EnergyFlowStats(
            currentCategory = EnergyFlowCategory.ENGINE_GENERATION,
            session = EnergyFlowTotals(engineGenerationKwh = 1.2),
            lifetime = EnergyFlowTotals(engineGenerationKwh = 2.4),
        ),
        batteryCapacityKwh = 72.9,
        fuelTankCapacityLiters = 60.0,
        tripElectricTariff = 0.6,
        fuelPricePerLiter = 8.2,
    )
}
