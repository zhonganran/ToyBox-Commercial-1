package com.byd.wjx.data.local

import android.util.Log
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.repository.LastSessionRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.log.WjxFileLogger

/** Imports EnergyConsumption rows while preserving source-id and time-window deduplication. */
internal class EnergyHistoryImporter(
    private val reader: EnergyDataReader,
    private val tripRepository: TripRepository,
    private val tripDao: TripDao,
    private val idleDrainDao: IdleDrainDao,
    private val settingsRepository: SettingsRepository,
    private val lastSessionRepository: LastSessionRepository,
) {
    suspend fun import(): HistoryImportResult {
        val lastImportTimestamp = settingsRepository.getLastEnergyImportTs()
        val records = reader.readTripsSince(lastImportTimestamp)
        val session = lastSessionRepository.snapshot()
        val fuelPrice = settingsRepository.getFuelPricePerLiter()
        if (records.isEmpty()) return HistoryImportResult(trips = 0, details = "没有新的记录")

        var tripsImported = 0
        var idleDrainsImported = 0
        var skippedDuplicates = 0
        var maxTimestamp = lastImportTimestamp

        for (record in records) {
            maxTimestamp = maxOf(maxTimestamp, record.startTimestamp)
            if (record.duration < MIN_TRIP_DURATION_SECONDS) continue
            if (tripDao.getByBydId(record.id) != null) {
                skippedDuplicates++
                continue
            }

            val startTsMs = record.startTimestamp * 1_000L
            val existing = tripDao.getByStartTsRange(
                startTsMs - DEDUP_WINDOW_MS,
                startTsMs + DEDUP_WINDOW_MS,
            )
            if (existing != null) {
                tripRepository.updateTrip(mergeEnergyRecord(existing, record, session, fuelPrice))
                skippedDuplicates++
                continue
            }

            val mapping = mapEnergyTrip(record, session, fuelPrice)
            mapping.idleDrain?.let { idleDrain ->
                idleDrainDao.insert(idleDrain)
                idleDrainsImported++
            }
            tripRepository.insertTrip(mapping.trip)
            tripsImported++
        }

        settingsRepository.setLastEnergyImportTs(maxTimestamp)
        Log.d(TAG, "Sync done: $tripsImported trips, $idleDrainsImported idle, $skippedDuplicates dups")
        WjxFileLogger.i(
            TAG,
            "Sync done: trips=$tripsImported idle=$idleDrainsImported " +
                "skippedDuplicate=$skippedDuplicates maxTs=$maxTimestamp",
        )
        return HistoryImportResult(
            trips = tripsImported,
            idleDrains = idleDrainsImported,
            details = "+$tripsImported 条行程，+$idleDrainsImported 条停车耗电，" +
                "跳过 $skippedDuplicates 条重复",
        )
    }

    private companion object {
        const val TAG = "EnergyHistoryImporter"
        const val MIN_TRIP_DURATION_SECONDS = 30L
        const val DEDUP_WINDOW_MS = 300_000L
    }
}
