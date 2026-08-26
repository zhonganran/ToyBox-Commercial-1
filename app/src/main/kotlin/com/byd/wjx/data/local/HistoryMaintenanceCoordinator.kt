package com.byd.wjx.data.local

import android.util.Log
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.flow.first

/** Owns post-import cost backfill and GPS-point reassignment. */
internal class HistoryMaintenanceCoordinator(
    private val tripRepository: TripRepository,
    private val tripDao: TripDao,
    private val tripPointDao: TripPointDao,
) {
    suspend fun calculateMissingCosts(tariff: Double, fuelPricePerLiter: Double) {
        try {
            var calculated = 0
            tripDao.getTripsWithoutCost().forEach { trip ->
                val electricCost = trip.cost ?: trip.kwhConsumed?.times(tariff)
                val fuelCost = trip.fuelCost ?: trip.fuelConsumedL?.times(fuelPricePerLiter)
                if (electricCost == trip.cost && fuelCost == trip.fuelCost) return@forEach
                tripRepository.updateTrip(trip.copy(cost = electricCost, fuelCost = fuelCost))
                calculated++
            }
            Log.d(TAG, "calculateMissingCosts: $calculated trips, tariff=$tariff")
            WjxFileLogger.i(TAG, "calculateMissingCosts: calculated=$calculated tariff=$tariff")
        } catch (error: Exception) {
            Log.e(TAG, "calculateMissingCosts failed", error)
            WjxFileLogger.e(TAG, "calculateMissingCosts failed: ${error.message}", error)
        }
    }

    suspend fun attachGpsPoints() {
        try {
            var attached = 0
            var tagged = 0
            val detached = tripPointDao.detachOrphanPoints()
            tripRepository.getAllTrips().first().forEach { trip ->
                val endTs = trip.endTs ?: return@forEach
                trip.bydId?.let { bydId ->
                    attached += tripPointDao.relinkByBydKey(trip.id, bydId, bydDate = null)
                    tagged += tripPointDao.tagTripPointsWithBydKey(
                        tripId = trip.id,
                        bydId = bydId,
                        bydDate = null,
                        from = (trip.startTs - GPS_TOLERANCE_MS).coerceAtLeast(0L),
                        to = endTs + GPS_TOLERANCE_MS,
                    )
                }
                attached += tripPointDao.attachToTripWithBydKey(
                    tripId = trip.id,
                    bydId = trip.bydId,
                    bydDate = null,
                    from = (trip.startTs - GPS_TOLERANCE_MS).coerceAtLeast(0L),
                    to = endTs + GPS_TOLERANCE_MS,
                )
            }
            Log.d(TAG, "attachGpsPoints: attached $attached, tagged $tagged, detached $detached")
            WjxFileLogger.i(TAG, "attachGpsPoints: attached=$attached tagged=$tagged detached=$detached")
        } catch (error: Exception) {
            Log.e(TAG, "attachGpsPoints failed", error)
            WjxFileLogger.e(TAG, "attachGpsPoints failed: ${error.message}", error)
        }
    }

    private companion object {
        const val TAG = "HistoryMaintenance"
        const val GPS_TOLERANCE_MS = 120_000L
    }
}
