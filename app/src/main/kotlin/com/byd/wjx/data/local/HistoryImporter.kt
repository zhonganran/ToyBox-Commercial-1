package com.byd.wjx.data.local

import android.content.Context
import android.util.Log
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.local.entity.IdleDrainEntity
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.repository.LastSessionRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val energyDataReader: EnergyDataReader,
    private val tripRepository: TripRepository,
    private val tripDao: TripDao,
    private val tripPointDao: TripPointDao,
    private val idleDrainDao: IdleDrainDao,
    private val settingsRepository: SettingsRepository,
    private val lastSessionRepository: LastSessionRepository,
) {
    companion object {
        private const val TAG = "HistoryImporter"
        private const val MIN_TRIP_DURATION_SEC = 30L
        private const val DEDUP_WINDOW_MS = 300_000L // ±5 min for time-based dedup
    }

    private val syncMutex = Mutex()

    data class ImportResult(
        val trips: Int,
        val idleDrains: Int = 0,
        val error: String? = null,
        val details: String? = null
    ) {
        val isError: Boolean get() = error != null
        val count: Int get() = trips + idleDrains
    }

    /**
     * Event-based sync: check if energydata changed, import new records.
     * Called on service start and app foreground.
     * Protected by mutex to prevent concurrent duplicate imports.
     */
    suspend fun syncFromEnergyData(): ImportResult {
        if (!syncMutex.tryLock()) {
            Log.d(TAG, "syncFromEnergyData: already running, skipping")
            WjxFileLogger.i(TAG, "syncFromEnergyData skipped: already running")
            return ImportResult(trips = 0, details = "同步已在进行中")
        }
        return try {
            if (!energyDataReader.peekSourceChanged(context)) {
                Log.d(TAG, "syncFromEnergyData: source unchanged, skipping")
                return ImportResult(trips = 0, details = "没有变化")
            }
            val result = doSync()
            // Mark only on success — a thrown EnergyDataException leaves the
            // watermark untouched so the next event retries this file.
            if (!result.isError) energyDataReader.markSourceProcessed(context)
            WjxFileLogger.i(TAG, "syncFromEnergyData result trips=${result.trips} idle=${result.idleDrains} error=${result.error.orEmpty()}")
            result
        } catch (e: EnergyDataException) {
            Log.w(TAG, "syncFromEnergyData failed: ${e.message}", e)
            WjxFileLogger.w(TAG, "syncFromEnergyData failed: ${e.message}", e)
            ImportResult(trips = 0, error = e.message)
        } catch (e: Exception) {
            Log.e(TAG, "syncFromEnergyData unexpected error", e)
            WjxFileLogger.e(TAG, "syncFromEnergyData unexpected error: ${e.message}", e)
            ImportResult(trips = 0, error = e.message ?: e.toString())
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun doSync(): ImportResult {
        val lastImportTs = settingsRepository.getLastEnergyImportTs()
        val bydRecords = energyDataReader.readTripsSince(lastImportTs)
        // Snapshot session SOC bookmarks once per sync run so all freshly-imported
        // trips in this batch share the same snapshot. Null when service is not running
        // (cold import after app update) — socStart/socEnd stay null in that case.
        val sessionSnap = lastSessionRepository.snapshot()

        if (bydRecords.isEmpty()) {
            return ImportResult(trips = 0, details = "没有新的记录")
        }

        var tripsImported = 0
        var idleDrainsImported = 0
        var skippedDuplicate = 0
        var maxTs = lastImportTs

        for (byd in bydRecords) {
            val startTsMs = byd.startTimestamp * 1000L
            val endTsMs = byd.endTimestamp * 1000L

            // Track max timestamp for next sync
            if (byd.startTimestamp > maxTs) maxTs = byd.startTimestamp

            // Skip very short sessions
            if (byd.duration < MIN_TRIP_DURATION_SEC) continue

            // Check duplicate by byd_id
            val existingById = tripDao.getByBydId(byd.id)
            if (existingById != null) {
                skippedDuplicate++
                continue
            }

            // Check duplicate by time overlap (catches v1.x live trips without byd_id)
            val existingByTime = tripDao.getByStartTsRange(
                startTsMs - DEDUP_WINDOW_MS,
                startTsMs + DEDUP_WINDOW_MS
            )
            if (existingByTime != null) {
                // Update existing trip with byd_id so future syncs skip it instantly
                val per100 = if (byd.tripKm > 0) byd.electricityKwh / byd.tripKm * 100.0 else null
                tripRepository.updateTrip(existingByTime.copy(
                    source = "energydata",
                    bydId = byd.id,
                    distanceKm = byd.tripKm,
                    kwhConsumed = byd.electricityKwh,
                    kwhPer100km = per100
                ))
                skippedDuplicate++
                continue
            }

            val isIdleDrain = byd.tripKm == 0.0

            if (isIdleDrain) {
                idleDrainDao.insert(
                    IdleDrainEntity(
                        startTs = startTsMs,
                        endTs = endTsMs,
                        kwhConsumed = byd.electricityKwh
                    )
                )
                idleDrainsImported++
            }

            // Insert all records (including zero-km) as trips for visibility
            val kwhPer100km = if (byd.tripKm > 0) {
                byd.electricityKwh / byd.tripKm * 100.0
            } else null

            // avgSpeedKmh: pure computation from energydata distance + duration.
            val avgSpeed = if (byd.duration > 0 && byd.tripKm > 0) {
                byd.tripKm / (byd.duration / 3600.0)
            } else null

            // SOC enrichment: attach socStart/socEnd when this trip's endTs falls
            // within the recorded session window [sessionStartTs .. sessionEndTs + 30s].
            // Containment on endTs (not intersection) avoids enriching trips that merely
            // overlap the session boundary but ended before the session started.
            // Tolerance: +30 sec on sessionEnd covers the post-idle-close window.
            // sessionSnap is Snapshot? — null when no session was recorded this process lifetime.
            val sessionStartTs = sessionSnap?.startTs
            val sessionEndTs = sessionSnap?.endTs
            val withinSession = sessionStartTs != null && sessionEndTs != null &&
                endTsMs in sessionStartTs..(sessionEndTs + 30_000L)
            val socStart = if (withinSession) sessionSnap?.startSoc else null
            val socEnd   = if (withinSession) sessionSnap?.endSoc   else null

            tripRepository.insertTrip(
                TripEntity(
                    startTs = startTsMs,
                    endTs = endTsMs,
                    distanceKm = byd.tripKm,
                    kwhConsumed = byd.electricityKwh,
                    kwhPer100km = kwhPer100km,
                    avgSpeedKmh = avgSpeed,
                    socStart = socStart,
                    socEnd = socEnd,
                    source = "energydata",
                    bydId = byd.id
                )
            )
            tripsImported++
        }

        settingsRepository.setLastEnergyImportTs(maxTs)

        Log.d(TAG, "Sync done: $tripsImported trips, $idleDrainsImported idle, $skippedDuplicate dups")
        WjxFileLogger.i(TAG, "Sync done: trips=$tripsImported idle=$idleDrainsImported skippedDuplicate=$skippedDuplicate maxTs=$maxTs")
        return ImportResult(
            trips = tripsImported,
            idleDrains = idleDrainsImported,
            details = "+$tripsImported 条行程，+$idleDrainsImported 条停车耗电，跳过 $skippedDuplicate 条重复"
        )
    }

    /**
     * Calculate cost for trips that have kWh but no cost yet.
     */
    suspend fun calculateMissingCosts(tariff: Double) {
        try {
            val trips = tripDao.getTripsWithoutCost()
            var calculated = 0
            for (trip in trips) {
                val kwh = trip.kwhConsumed ?: continue
                tripRepository.updateTrip(trip.copy(cost = kwh * tariff))
                calculated++
            }
            Log.d(TAG, "calculateMissingCosts: $calculated trips, tariff=$tariff")
            WjxFileLogger.i(TAG, "calculateMissingCosts: calculated=$calculated tariff=$tariff")
        } catch (e: Exception) {
            Log.e(TAG, "calculateMissingCosts failed", e)
            WjxFileLogger.e(TAG, "calculateMissingCosts failed: ${e.message}", e)
        }
    }

    /**
     * Attach unattached GPS points to trips by time window.
     */
    suspend fun attachGpsPoints() {
        try {
            val trips = tripRepository.getAllTrips().first()
            var attached = 0
            var tagged = 0
            val detached = tripPointDao.detachOrphanPoints()
            for (trip in trips) {
                val endTs = trip.endTs ?: continue
                val bydId = trip.bydId
                if (bydId != null) {
                    attached += tripPointDao.relinkByBydKey(
                        tripId = trip.id,
                        bydId = bydId,
                        bydDate = null
                    )
                    tagged += tripPointDao.tagTripPointsWithBydKey(
                        tripId = trip.id,
                        bydId = bydId,
                        bydDate = null,
                        from = (trip.startTs - 120_000L).coerceAtLeast(0L),
                        to = endTs + 120_000L
                    )
                }
                val count = tripPointDao.attachToTripWithBydKey(
                    tripId = trip.id,
                    bydId = bydId,
                    bydDate = null,
                    from = (trip.startTs - 120_000L).coerceAtLeast(0L),
                    to = endTs + 120_000L
                )
                if (count > 0) attached += count
            }
            Log.d(TAG, "attachGpsPoints: attached $attached points, tagged $tagged points, detached $detached orphan links")
            WjxFileLogger.i(TAG, "attachGpsPoints: attached=$attached tagged=$tagged detached=$detached")
        } catch (e: Exception) {
            Log.e(TAG, "attachGpsPoints failed", e)
            WjxFileLogger.e(TAG, "attachGpsPoints failed: ${e.message}", e)
        }
    }

    /**
     * Unified sync entry point — always uses energydata pipeline.
     */
    suspend fun runSync(): ImportResult {
        val r = syncFromEnergyData()
        calculateMissingCosts(settingsRepository.getTripCostTariff())
        attachGpsPoints()
        return r
    }

}
