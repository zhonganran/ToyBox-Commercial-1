package com.byd.wjx.data.local

import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.repository.LastSessionRepository
import com.byd.wjx.data.trips.TripSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class HistoryImportModelsTest {
    @Test
    fun `energy trip mapping enriches contained session and computes metrics`() {
        val record = BydTripRecord(
            id = 7,
            startTimestamp = 100,
            endTimestamp = 160,
            duration = 60,
            tripKm = 2.0,
            electricityKwh = 0.4,
        )
        val session = LastSessionRepository.Snapshot(
            startSoc = 80,
            endSoc = 78,
            startTs = 90_000,
            endTs = 150_000,
            startFuelL = 10.0,
            maxFuelL = 12.0,
        )

        val mapping = mapEnergyTrip(record, session, fuelPricePerLiter = 7.5)

        assertEquals(20.0, mapping.trip.kwhPer100km!!, 0.0001)
        assertEquals(120.0, mapping.trip.avgSpeedKmh!!, 0.0001)
        assertEquals(80, mapping.trip.socStart)
        assertEquals(78, mapping.trip.socEnd)
        assertEquals(2.0, mapping.trip.fuelConsumedL!!, 0.0001)
        assertEquals(15.0, mapping.trip.fuelCost!!, 0.0001)
        assertEquals(TripSource.ENERGYDATA, mapping.trip.source)
        assertNull(mapping.idleDrain)
    }

    @Test
    fun `zero distance remains visible as trip and idle drain`() {
        val mapping = mapEnergyTrip(
            record = BydTripRecord(9, 200, 260, 60, 0.0, 0.25),
            session = null,
            fuelPricePerLiter = 7.5,
        )

        assertNull(mapping.trip.kwhPer100km)
        assertNull(mapping.trip.avgSpeedKmh)
        assertNotNull(mapping.idleDrain)
        assertEquals(0.25, mapping.idleDrain!!.kwhConsumed!!, 0.0001)
    }

    @Test
    fun `dedup merge preserves existing fuel outside session`() {
        val existing = TripEntity(
            id = 3,
            startTs = 1_000,
            fuelConsumedL = 1.5,
            fuelCost = 11.0,
        )
        val merged = mergeEnergyRecord(
            existing = existing,
            record = BydTripRecord(22, 1, 2, 60, 10.0, 2.0),
            session = null,
            fuelPricePerLiter = 8.0,
        )

        assertEquals(22L, merged.bydId)
        assertEquals(20.0, merged.kwhPer100km!!, 0.0001)
        assertEquals(1.5, merged.fuelConsumedL!!, 0.0001)
        assertEquals(11.0, merged.fuelCost!!, 0.0001)
    }
}
