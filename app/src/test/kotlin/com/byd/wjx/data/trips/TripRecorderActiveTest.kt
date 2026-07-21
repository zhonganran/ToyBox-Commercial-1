package com.byd.wjx.data.trips

import com.byd.wjx.data.local.EnergyDataReader
import com.byd.wjx.data.local.dao.LastStateDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.remote.diParsData
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorderActiveTest {

    private fun setup(): Triple<TripRecorder, TripDao, LastStateDao> {
        val tripDao = mockk<TripDao>(relaxed = true)
        val lastState = mockk<LastStateDao>(relaxed = true)
        val energy = mockk<EnergyDataReader> { every { isAvailable() } returns false }
        val clock = mutableListOf(1_000L, 2_000L, 3_000L, 4_000L)
        val recorder = TripRecorder(
            tripDao, lastState, energy,
            batteryCapacityKwh = { 72.9 },
            now = { clock.removeAt(0) },
        )
        return Triple(recorder, tripDao, lastState)
    }

    @Test fun `ACC then OK opens trip, OK to ACC closes with one row + writes last_state`() = runTest {
        val (rec, tripDao, lastState) = setup()
        rec.consume(diParsData(powerState = 1, soc = 80, mileage = 100.0))  // ACC
        rec.consume(diParsData(powerState = 3, soc = 80, mileage = 100.0))  // OK -> open(now=1000)
        rec.consume(diParsData(powerState = 3, soc = 70, mileage = 110.0))  // driving (no insert)
        rec.consume(diParsData(powerState = 1, soc = 70, mileage = 110.0))  // ACC -> close(now=2000)

        val captured = slot<TripEntity>()
        coVerify(exactly = 1) { tripDao.insert(capture(captured)) }
        val t = captured.captured
        assertEquals(1_000L, t.startTs)
        assertEquals(2_000L, t.endTs)
        assertEquals(80, t.socStart)
        assertEquals(70, t.socEnd)
        assertEquals(10.0, t.distanceKm!!, 0.001)
        assertEquals(72.9 * 0.10, t.kwhConsumed!!, 0.001)
        assertEquals(72.9, t.kwhPer100km!!, 0.001)  // 7.29 kWh / 10 km * 100
        assertEquals(TripSource.NATIVE_POLLING, t.source)
        coVerify(exactly = 1) { lastState.openTrip(startTs = 1_000L, startSoc = 80, startMileage = 100.0, startTotalElec = null, now = 1_000L) }
        coVerify(exactly = 1) { lastState.clearOpenTrip() }
    }

    @Test fun `totalElec delta gives consumption even when SOC unchanged`() = runTest {
        val (rec, tripDao, lastState) = setup()
        rec.consume(diParsData(powerState = 3, soc = 80, mileage = 100.0, totalElecConsumption = 1000.0))  // open
        rec.consume(diParsData(powerState = 1, soc = 80, mileage = 103.0, totalElecConsumption = 1000.4))  // close: 3 km, SOC flat
        val captured = slot<TripEntity>()
        coVerify(exactly = 1) { tripDao.insert(capture(captured)) }
        val t = captured.captured
        assertEquals(0.4, t.kwhConsumed!!, 0.001)
        assertEquals(0.4 / 3.0 * 100.0, t.kwhPer100km!!, 0.001)
        coVerify(exactly = 1) { lastState.openTrip(startTs = 1_000L, startSoc = 80, startMileage = 100.0, startTotalElec = 1000.0, now = 1_000L) }
    }

    @Test fun `totalElec delta preferred over coarse SOC delta`() = runTest {
        val (rec, tripDao, _) = setup()
        rec.consume(diParsData(powerState = 3, soc = 80, mileage = 100.0, totalElecConsumption = 2000.0))
        rec.consume(diParsData(powerState = 1, soc = 79, mileage = 105.0, totalElecConsumption = 2000.5))
        val captured = slot<TripEntity>()
        coVerify(exactly = 1) { tripDao.insert(capture(captured)) }
        assertEquals(0.5, captured.captured.kwhConsumed!!, 0.001)  // 0.5 from counter, not 0.729 from 1% SOC
    }

    @Test fun `negative totalElec delta falls back to SOC delta`() = runTest {
        val (rec, tripDao, _) = setup()
        rec.consume(diParsData(powerState = 3, soc = 80, mileage = 100.0, totalElecConsumption = 1000.0))
        rec.consume(diParsData(powerState = 1, soc = 70, mileage = 110.0, totalElecConsumption = 999.0))  // counter reset
        val captured = slot<TripEntity>()
        coVerify(exactly = 1) { tripDao.insert(capture(captured)) }
        assertEquals(72.9 * 0.10, captured.captured.kwhConsumed!!, 0.001)
    }

    @Test fun `consecutive OK ticks do not double-open`() = runTest {
        val (rec, tripDao, lastState) = setup()
        rec.consume(diParsData(powerState = 3, soc = 80, mileage = 100.0))
        rec.consume(diParsData(powerState = 3, soc = 79, mileage = 101.0))
        rec.consume(diParsData(powerState = 3, soc = 78, mileage = 102.0))
        rec.consume(diParsData(powerState = 1, soc = 78, mileage = 102.0))
        coVerify(exactly = 1) { tripDao.insert(any()) }
        coVerify(exactly = 1) { lastState.openTrip(any(), any(), any(), any(), any()) }
    }

    @Test fun `zero distance leaves kwhPer100km null even with consumption`() = runTest {
        val (rec, tripDao, _) = setup()
        rec.consume(diParsData(powerState = 3, soc = 80, mileage = 100.0))  // open
        rec.consume(diParsData(powerState = 1, soc = 70, mileage = 100.0))  // close: 0 km, 10% drop
        val captured = slot<TripEntity>()
        coVerify(exactly = 1) { tripDao.insert(capture(captured)) }
        val t = captured.captured
        assertEquals(0.0, t.distanceKm!!, 0.001)
        assertEquals(72.9 * 0.10, t.kwhConsumed!!, 0.001)
        assertNull(t.kwhPer100km)  // distance == 0 -> no per-100km figure
    }

    @Test fun `no SOC drop leaves both consumption fields null`() = runTest {
        val (rec, tripDao, _) = setup()
        rec.consume(diParsData(powerState = 3, soc = 80, mileage = 100.0))  // open
        rec.consume(diParsData(powerState = 1, soc = 80, mileage = 110.0))  // close: 10 km, no drop
        val captured = slot<TripEntity>()
        coVerify(exactly = 1) { tripDao.insert(capture(captured)) }
        val t = captured.captured
        assertNull(t.kwhConsumed)
        assertNull(t.kwhPer100km)
    }

    @Test fun `OK to OFF also closes (powerState 3 to 0)`() = runTest {
        val (rec, tripDao, lastState) = setup()
        rec.consume(diParsData(powerState = 3, soc = 80, mileage = 100.0))  // OK -> open
        rec.consume(diParsData(powerState = 0, soc = 75, mileage = 105.0))  // OFF -> close
        coVerify(exactly = 1) { tripDao.insert(any()) }
        coVerify(exactly = 1) { lastState.clearOpenTrip() }
    }

    @Test fun `trip stores electric and fuel usage with separate costs`() = runTest {
        val tripDao = mockk<TripDao>(relaxed = true)
        val lastState = mockk<LastStateDao>(relaxed = true)
        val energy = mockk<EnergyDataReader> { every { isAvailable() } returns false }
        val clock = mutableListOf(1_000L, 2_000L)
        val recorder = TripRecorder(
            tripDao = tripDao,
            lastStateDao = lastState,
            energyDataReader = energy,
            batteryCapacityKwh = { 72.9 },
            tripElectricTariff = { 0.6 },
            fuelPricePerLiter = { 8.0 },
            now = { clock.removeAt(0) },
        )

        recorder.consume(
            diParsData(
                powerState = 3,
                soc = 80,
                mileage = 100.0,
                totalElecConsumption = 1_000.0,
            ).copy(currentTripFuelL = 0.4),
        )
        recorder.consume(
            diParsData(
                powerState = 3,
                soc = 80,
                mileage = 103.0,
                totalElecConsumption = 1_000.3,
            ).copy(currentTripFuelL = 1.1),
        )
        recorder.consume(
            diParsData(
                powerState = 1,
                soc = 80,
                mileage = 105.0,
                totalElecConsumption = 1_000.5,
            ).copy(currentTripFuelL = 0.0),
        )

        val captured = slot<TripEntity>()
        coVerify(exactly = 1) { tripDao.insert(capture(captured)) }
        assertEquals(0.5, captured.captured.kwhConsumed ?: 0.0, 0.001)
        assertEquals(0.3, captured.captured.cost ?: 0.0, 0.001)
        assertEquals(0.7, captured.captured.fuelConsumedL ?: 0.0, 0.001)
        assertEquals(5.6, captured.captured.fuelCost ?: 0.0, 0.001)
    }
}
