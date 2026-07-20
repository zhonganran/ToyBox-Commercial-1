package com.byd.wjx.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.trips.TripSource
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

data class DiPlusTripRecord(
    val timeStart: Long,
    val timeEnd: Long,
    val mileageKm: Double,
    val travelTime: Double,
    val avgSpeedKmh: Double,
    val socStart: Double,
    val socEnd: Double,
    val electricityStartKwh: Double,
    val electricityEndKwh: Double,
    val odometerStartKm: Double,
    val odometerEndKm: Double,
)

@Singleton
open class DiPlusDbReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "DiPlusDbReader"
        private const val DB_PATH = "/storage/emulated/0/vandiplus/db/van_bm_db"
        private const val PREFS_NAME = "diplus_sync"
    }

    internal open fun databaseFile(): File = File(DB_PATH)

    fun isAvailable(): Boolean {
        val file = databaseFile()
        return file.exists() && file.isFile && file.canRead() && file.length() >= 16L
    }

    fun peekSourceChanged(): Boolean {
        val file = databaseFile()
        if (!isAvailable()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("lastModified", 0L) != file.lastModified() ||
            prefs.getLong("fileSize", 0L) != file.length() ||
            prefs.getString("filePath", "") != file.absolutePath
    }

    fun markSourceProcessed() {
        val file = databaseFile()
        if (!isAvailable()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong("lastModified", file.lastModified())
            .putLong("fileSize", file.length())
            .putString("filePath", file.absolutePath)
            .apply()
    }

    suspend fun readTripInfo(): List<DiPlusTripRecord> = withContext(Dispatchers.IO) {
        val file = databaseFile()
        if (!isAvailable()) {
            WjxFileLogger.i(TAG, "DiPlus DB unavailable path=${file.absolutePath}")
            return@withContext emptyList()
        }

        runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val hasTripInfo = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='TripInfo'",
                    null,
                ).use { it.moveToFirst() }
                if (!hasTripInfo) {
                    WjxFileLogger.w(TAG, "TripInfo table missing path=${file.absolutePath}")
                    return@use emptyList()
                }

                db.rawQuery(
                    """
                    SELECT time_start, time_end, mileage, travelTime, avgSpeed,
                           elecPer_start, elecPer_end, elecCon_start, elecCon_end,
                           mileage_start, mileage_end
                    FROM TripInfo
                    ORDER BY time_start
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                DiPlusTripRecord(
                                    timeStart = cursor.getLong(0),
                                    timeEnd = cursor.getLong(1),
                                    mileageKm = cursor.getDouble(2),
                                    travelTime = cursor.getDouble(3),
                                    avgSpeedKmh = cursor.getDouble(4),
                                    socStart = cursor.getDouble(5),
                                    socEnd = cursor.getDouble(6),
                                    electricityStartKwh = cursor.getDouble(7),
                                    electricityEndKwh = cursor.getDouble(8),
                                    odometerStartKm = cursor.getDouble(9) / 10.0,
                                    odometerEndKm = cursor.getDouble(10) / 10.0,
                                ),
                            )
                        }
                    }
                }
            }
        }.onSuccess { records ->
            Log.i(TAG, "readTripInfo: ${records.size} records from ${file.absolutePath}")
            WjxFileLogger.i(TAG, "readTripInfo rows=${records.size} size=${file.length()}")
        }.onFailure { error ->
            Log.e(TAG, "readTripInfo failed", error)
            WjxFileLogger.e(TAG, "readTripInfo failed: ${error.message}", error)
        }.getOrDefault(emptyList())
    }
}

internal fun DiPlusTripRecord.toTripEntity(): TripEntity? {
    val startTs = normalizeVehicleTimestamp(timeStart) ?: return null
    val rawEndTs = normalizeVehicleTimestamp(timeEnd)
    val durationMs = normalizeTravelDurationMs(travelTime)
    val endTs = rawEndTs?.takeIf { it > startTs }
        ?: durationMs?.let { startTs + it }
        ?: return null
    val odometerDistance = (odometerEndKm - odometerStartKm)
        .takeIf { it.isFinite() && it > 0.0 }
    val distance = mileageKm.takeIf { it.isFinite() && it > 0.0 } ?: odometerDistance
    if (distance == null || distance > 2_000.0) return null
    val kwh = (electricityEndKwh - electricityStartKwh)
        .takeIf { it.isFinite() && it > 0.0 && it < 500.0 }
    val per100 = kwh?.let { it / distance * 100.0 }
        ?.takeIf { it.isFinite() && it in 0.0..200.0 }

    return TripEntity(
        startTs = startTs,
        endTs = endTs,
        distanceKm = distance,
        kwhConsumed = kwh,
        kwhPer100km = per100,
        socStart = socStart.toSocPercent(),
        socEnd = socEnd.toSocPercent(),
        avgSpeedKmh = avgSpeedKmh.takeIf { it.isFinite() && it in 0.0..300.0 },
        source = TripSource.DIPLUS,
    )
}

internal fun normalizeVehicleTimestamp(raw: Long): Long? = when {
    raw <= 0L -> null
    raw < 10_000_000_000L -> raw * 1_000L
    raw < 10_000_000_000_000L -> raw
    else -> null
}

private fun normalizeTravelDurationMs(raw: Double): Long? = when {
    !raw.isFinite() || raw <= 0.0 -> null
    raw <= 172_800.0 -> (raw * 1_000.0).toLong()
    raw <= 172_800_000.0 -> raw.toLong()
    else -> null
}

private fun Double.toSocPercent(): Int? =
    takeIf { it.isFinite() && it in 0.0..100.0 }?.roundToInt()
