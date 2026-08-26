package com.byd.wjx.data.local

import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.data.trips.TripSource

/** Imports and enriches trips from DiPlus when EnergyConsumption is unavailable. */
internal class DiPlusHistoryImporter(
    private val reader: DiPlusDbReader,
    private val tripRepository: TripRepository,
    private val tripDao: TripDao,
) {
    suspend fun import(): HistoryImportResult {
        val records = reader.readTripInfo()
        if (records.isEmpty()) return HistoryImportResult(trips = 0, details = "系统行程库暂无记录")

        var imported = 0
        var updated = 0
        var skipped = 0
        records.forEach { record ->
            val incoming = record.toTripEntity() ?: run {
                skipped++
                return@forEach
            }
            val existing = tripDao.getByStartTsRange(
                incoming.startTs - DEDUP_WINDOW_MS,
                incoming.startTs + DEDUP_WINDOW_MS,
            )
            when {
                existing == null -> {
                    tripRepository.insertTrip(incoming)
                    imported++
                }
                existing.source != TripSource.ENERGYDATA -> {
                    tripRepository.updateTrip(
                        existing.copy(
                            startTs = incoming.startTs,
                            endTs = incoming.endTs,
                            distanceKm = incoming.distanceKm,
                            kwhConsumed = incoming.kwhConsumed ?: existing.kwhConsumed,
                            kwhPer100km = incoming.kwhPer100km ?: existing.kwhPer100km,
                            socStart = incoming.socStart ?: existing.socStart,
                            socEnd = incoming.socEnd ?: existing.socEnd,
                            avgSpeedKmh = incoming.avgSpeedKmh ?: existing.avgSpeedKmh,
                            source = TripSource.DIPLUS,
                        ),
                    )
                    updated++
                }
                else -> skipped++
            }
        }
        return HistoryImportResult(
            trips = imported + updated,
            details = "+$imported 条系统行程，补全 $updated 条，跳过 $skipped 条重复或无效记录",
        )
    }

    private companion object {
        const val DEDUP_WINDOW_MS = 120_000L
    }
}
