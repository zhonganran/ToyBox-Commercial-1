package com.byd.wjx.data.local

import com.byd.wjx.data.trips.TripSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiPlusDbReaderTest {
    @Test
    fun `maps seconds timestamps and system trip metrics`() {
        val entity = DiPlusTripRecord(
            timeStart = 1_700_000_000L,
            timeEnd = 1_700_000_600L,
            mileageKm = 12.5,
            travelTime = 600.0,
            avgSpeedKmh = 75.0,
            socStart = 80.0,
            socEnd = 76.0,
            electricityStartKwh = 1_000.0,
            electricityEndKwh = 1_002.5,
            odometerStartKm = 10_000.0,
            odometerEndKm = 10_012.5,
        ).toTripEntity()

        assertNotNull(entity)
        entity!!
        assertEquals(1_700_000_000_000L, entity.startTs)
        assertEquals(1_700_000_600_000L, entity.endTs)
        assertEquals(12.5, entity.distanceKm!!, 0.0001)
        assertEquals(2.5, entity.kwhConsumed!!, 0.0001)
        assertEquals(20.0, entity.kwhPer100km!!, 0.0001)
        assertEquals(80, entity.socStart)
        assertEquals(76, entity.socEnd)
        assertEquals(TripSource.DIPLUS, entity.source)
    }

    @Test
    fun `falls back to odometer distance and travel duration`() {
        val entity = DiPlusTripRecord(
            timeStart = 1_700_000_000_000L,
            timeEnd = 0L,
            mileageKm = 0.0,
            travelTime = 900.0,
            avgSpeedKmh = 40.0,
            socStart = 55.4,
            socEnd = 53.6,
            electricityStartKwh = 500.0,
            electricityEndKwh = 501.2,
            odometerStartKm = 20_000.0,
            odometerEndKm = 20_010.0,
        ).toTripEntity()

        assertNotNull(entity)
        entity!!
        assertEquals(1_700_000_900_000L, entity.endTs)
        assertEquals(10.0, entity.distanceKm!!, 0.0001)
        assertEquals(55, entity.socStart)
        assertEquals(54, entity.socEnd)
    }

    @Test
    fun `rejects unsupported timestamps`() {
        assertEquals(null, normalizeVehicleTimestamp(0L))
        assertEquals(null, normalizeVehicleTimestamp(20_240_720_120_000L))
    }
}
