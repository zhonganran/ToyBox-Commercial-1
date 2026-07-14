package com.byd.wjx.domain.calculator

import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.domain.tracker.DriveSessionTransition
import com.byd.wjx.domain.tracker.DriveSessionUpdate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DrivingMetricsCoordinatorTest {

    @Test
    fun `sample pipeline updates buffers consumption and range`() = runTest {
        val odometer = mockk<OdometerConsumptionBuffer>(relaxed = true)
        val live = mockk<LiveTripBuffer>(relaxed = true)
        val interpolator = mockk<SocInterpolator>(relaxed = true)
        val range = mockk<RangeCalculator>()
        coEvery { odometer.recentAvgConsumption() } returns 18.0
        coEvery { odometer.shortAvgConsumption() } returns 17.0
        coEvery { range.estimate(70, 1_050.0) } returns 390.0
        val aggregator = ConsumptionAggregator()
        val coordinator = DrivingMetricsCoordinator(
            odometerBuffer = odometer,
            liveTripBuffer = live,
            socInterpolator = interpolator,
            rangeCalculator = range,
            consumptionAggregator = aggregator,
        )
        val data = diParsData(
            soc = 70,
            mileage = 110.0,
            totalElecConsumption = 1_050.0,
        )
        val session = DriveSessionUpdate(
            sessionStartedAt = 1_000L,
            tripDistanceKm = 10.0,
            tripEnergyKwh = 1.5,
            lastTripAverage = 16.0,
            transition = DriveSessionTransition.NONE,
        )

        val result = coordinator.onSample(
            nowMs = 2_000L,
            data = data,
            session = session,
        )

        assertEquals(390.0, result.rangeKm)
        assertEquals(10.0, result.tripDistanceKm)
        assertEquals(15.0, result.displayConsumption)
        coVerify {
            odometer.onSample(
                mileage = 110.0,
                totalElec = 1_050.0,
                socPercent = 70,
                sessionId = 1_000L,
            )
        }
        coVerify { live.onSample(110.0, 1_050.0, 1_000L) }
        verify { interpolator.onSample(70, 1_050.0, 1_000L) }
    }
}
