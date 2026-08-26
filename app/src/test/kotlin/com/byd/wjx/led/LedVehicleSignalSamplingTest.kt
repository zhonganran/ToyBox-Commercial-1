package com.byd.wjx.led

import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LedVehicleSignalSamplingTest {
    @Test
    fun `tiered samples merge over core signals`() {
        val accumulator = LedVehicleSignalAccumulator()
        accumulator.updateCore(diParsData(speed = 42, gear = 4, powerState = 3))
        accumulator.update(
            LedSignalTier.MOTION,
            diParsData(gear = 2).copy(turnSignalFlash = 4),
            sampledAt = 1_000L,
        )
        accumulator.update(
            LedSignalTier.BRAKE,
            diParsData(lightBrake = 1, lightReversing = 0, accelPedalDepth = 35)
                .copy(brakePedalDepth = 65),
            sampledAt = 1_000L,
        )
        accumulator.update(
            LedSignalTier.BODY,
            diParsData(doorFL = 1, doorFR = 0, doorRL = 0, doorRR = 1, trunk = 2)
                .copy(adasApaParkingStatus = 4),
            sampledAt = 1_000L,
        )
        accumulator.update(
            LedSignalTier.ADAS,
            diParsData(adasMnoaStatus = 7).copy(
                adasAccMode = 1,
                adasLccNcaStatus = 2,
                accCruiseState = 3,
            ),
            sampledAt = 1_000L,
        )

        val signals = accumulator.snapshot(now = 1_200L)

        assertEquals(42, signals.speed)
        assertEquals(2, signals.gear)
        assertEquals(3, signals.powerState)
        assertEquals(4, signals.turnSignalFlash)
        assertEquals(65, signals.brakePedalDepth)
        assertEquals(35, signals.accelPedalDepth)
        assertEquals(1, signals.doorFL)
        assertEquals(2, signals.trunk)
        assertEquals(4, signals.adasApaParkingStatus)
        assertEquals(1, signals.adasAccMode)
        assertEquals(2, signals.adasLccNcaStatus)
        assertEquals(3, signals.accCruiseState)
        assertEquals(7, signals.adasMnoaStatus)
    }

    @Test
    fun `each signal tier expires at its original freshness boundary`() {
        val accumulator = LedVehicleSignalAccumulator()
        accumulator.update(LedSignalTier.MOTION, diParsData().copy(turnSignalFlash = 2), 1_000L)
        accumulator.update(LedSignalTier.BRAKE, diParsData().copy(brakePedalDepth = 20), 1_000L)
        accumulator.update(LedSignalTier.BODY, diParsData(trunk = 1), 1_000L)
        accumulator.update(LedSignalTier.ADAS, diParsData().copy(adasAccMode = 5), 1_000L)

        assertEquals(2, accumulator.snapshot(1_350L).turnSignalFlash)
        assertNull(accumulator.snapshot(1_351L).turnSignalFlash)
        assertEquals(20, accumulator.snapshot(1_700L).brakePedalDepth)
        assertNull(accumulator.snapshot(1_701L).brakePedalDepth)
        assertEquals(1, accumulator.snapshot(2_500L).trunk)
        assertNull(accumulator.snapshot(2_501L).trunk)
        assertEquals(5, accumulator.snapshot(3_500L).adasAccMode)
        assertNull(accumulator.snapshot(3_501L).adasAccMode)
    }

    @Test
    fun `future samples and reset cannot leak stale values`() {
        val accumulator = LedVehicleSignalAccumulator()
        accumulator.updateCore(diParsData(speed = 9, gear = 4, powerState = 3))
        accumulator.update(LedSignalTier.MOTION, diParsData().copy(turnSignalFlash = 6), 2_000L)

        assertNull(accumulator.snapshot(1_999L).turnSignalFlash)

        accumulator.reset()
        val reset = accumulator.snapshot(2_000L)
        assertNull(reset.speed)
        assertNull(reset.gear)
        assertNull(reset.powerState)
        assertNull(reset.turnSignalFlash)
    }

    @Test
    fun `signal loop preserves inactive and active pacing`() = runTest {
        val accumulator = LedVehicleSignalAccumulator()
        var active = false
        var fetchCount = 0
        backgroundScope.launch {
            runLedSignalLoop(
                tag = "LedSignalTest",
                deviceLabel = "test",
                signalLabel = "motion",
                tier = LedSignalTier.MOTION,
                intervalMs = 100L,
                shouldPoll = { active },
                fetch = {
                    fetchCount += 1
                    VehicleSnapshot.synthetic(
                        data = diParsData().copy(turnSignalFlash = 2),
                        kind = VehicleSnapshotKind.ACTION,
                    )
                },
                accumulator = accumulator,
                onSample = {},
            )
        }

        runCurrent()
        active = true
        advanceTimeBy(499L)
        runCurrent()
        assertEquals(0, fetchCount)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, fetchCount)

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(2, fetchCount)
    }
}
