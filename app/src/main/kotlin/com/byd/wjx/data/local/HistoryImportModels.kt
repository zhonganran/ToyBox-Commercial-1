package com.byd.wjx.data.local

import com.byd.wjx.data.local.entity.IdleDrainEntity
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.repository.LastSessionRepository
import com.byd.wjx.data.trips.TripSource

data class HistoryImportResult(
    val trips: Int,
    val idleDrains: Int = 0,
    val error: String? = null,
    val details: String? = null,
) {
    val isError: Boolean get() = error != null
    val count: Int get() = trips + idleDrains
}

internal data class EnergyTripMapping(
    val trip: TripEntity,
    val idleDrain: IdleDrainEntity?,
)

internal fun isHistoryTripWithinSession(
    tripEndTsMs: Long,
    sessionStartTs: Long?,
    sessionEndTs: Long?,
    toleranceMs: Long = 30_000L,
): Boolean = sessionStartTs != null && sessionEndTs != null &&
    tripEndTsMs in sessionStartTs..(sessionEndTs + toleranceMs)

internal fun mapEnergyTrip(
    record: BydTripRecord,
    session: LastSessionRepository.Snapshot?,
    fuelPricePerLiter: Double,
): EnergyTripMapping {
    val startTsMs = record.startTimestamp * 1_000L
    val endTsMs = record.endTimestamp * 1_000L
    val withinSession = isHistoryTripWithinSession(endTsMs, session?.startTs, session?.endTs)
    val sessionFuel = session?.fuelConsumedL.takeIf { withinSession }
    val per100 = record.tripKm.takeIf { it > 0.0 }
        ?.let { distance -> record.electricityKwh / distance * 100.0 }
    val averageSpeed = if (record.duration > 0L && record.tripKm > 0.0) {
        record.tripKm / (record.duration / 3_600.0)
    } else {
        null
    }
    return EnergyTripMapping(
        trip = TripEntity(
            startTs = startTsMs,
            endTs = endTsMs,
            distanceKm = record.tripKm,
            kwhConsumed = record.electricityKwh,
            kwhPer100km = per100,
            fuelConsumedL = sessionFuel,
            fuelCost = sessionFuel?.times(fuelPricePerLiter),
            avgSpeedKmh = averageSpeed,
            socStart = session?.startSoc.takeIf { withinSession },
            socEnd = session?.endSoc.takeIf { withinSession },
            source = TripSource.ENERGYDATA,
            bydId = record.id,
        ),
        idleDrain = if (record.tripKm == 0.0) {
            IdleDrainEntity(startTs = startTsMs, endTs = endTsMs, kwhConsumed = record.electricityKwh)
        } else {
            null
        },
    )
}

internal fun mergeEnergyRecord(
    existing: TripEntity,
    record: BydTripRecord,
    session: LastSessionRepository.Snapshot?,
    fuelPricePerLiter: Double,
): TripEntity {
    val withinSession = isHistoryTripWithinSession(
        tripEndTsMs = record.endTimestamp * 1_000L,
        sessionStartTs = session?.startTs,
        sessionEndTs = session?.endTs,
    )
    val sessionFuel = session?.fuelConsumedL.takeIf { withinSession }
    val per100 = record.tripKm.takeIf { it > 0.0 }
        ?.let { distance -> record.electricityKwh / distance * 100.0 }
    return existing.copy(
        source = TripSource.ENERGYDATA,
        bydId = record.id,
        distanceKm = record.tripKm,
        kwhConsumed = record.electricityKwh,
        kwhPer100km = per100,
        fuelConsumedL = sessionFuel ?: existing.fuelConsumedL,
        fuelCost = sessionFuel?.times(fuelPricePerLiter) ?: existing.fuelCost,
    )
}
