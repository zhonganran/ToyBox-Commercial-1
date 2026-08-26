package com.byd.wjx.data.local

import android.content.Context
import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coordinates read-only imports from BYD's energy-consumption database. */
@Singleton
open class EnergyDataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val databaseReader = EnergyDatabaseReader()
    private val availabilityCache = EnergyDataAvailabilityCache()

    suspend fun diagnose(): String = withContext(Dispatchers.IO) {
        EnergyDataDiagnostics(currentSource(), databaseReader).buildReport()
    }

    /**
     * Read-only change detector. The watermark advances only through [markSourceProcessed] after a
     * successful import, so a failed import remains eligible for retry.
     */
    fun peekSourceChanged(context: Context): Boolean = currentSource().peekSourceChanged(context)

    /** Persists the current source fingerprint after a successful import. */
    fun markSourceProcessed(context: Context) = currentSource().markSourceProcessed(context)

    suspend fun readTripsSince(sinceTimestampSec: Long): List<BydTripRecord> =
        withContext(Dispatchers.IO) {
            val source = currentSource()
            val directory = source.directory()
            if (!directory.exists()) {
                Log.i(TAG, "readTripsSince($sinceTimestampSec): directory ${directory.path} missing - empty")
                WjxFileLogger.i(TAG, "readTripsSince: directory missing path=${directory.path}")
                return@withContext emptyList()
            }

            val sourceDb = source.findDatabase(directory) ?: run {
                Log.i(TAG, "readTripsSince: no database files in ${directory.path} - empty")
                WjxFileLogger.i(TAG, "readTripsSince: no db files path=${directory.path}")
                return@withContext emptyList()
            }
            logSource("readTripsSince since=$sinceTimestampSec", sourceDb)

            val results = databaseReader.readTripsSince(source.copyToLocal(sourceDb), sinceTimestampSec)
            logResult("readTripsSince", results)
            results
        }

    suspend fun readTrips(): List<BydTripRecord> = withContext(Dispatchers.IO) {
        val source = currentSource()
        val directory = source.directory()
        if (!directory.exists()) {
            Log.w(TAG, "readTrips: directory ${directory.path} missing - throwing")
            WjxFileLogger.w(TAG, "readTrips: directory missing path=${directory.path}")
            throw EnergyDataException("未找到 energydata 目录：${directory.path}")
        }

        val sourceDb = source.findDatabase(directory) ?: run {
            Log.w(TAG, "readTrips: no database files in ${directory.path} - throwing")
            WjxFileLogger.w(TAG, "readTrips: no db files path=${directory.path}")
            throw EnergyDataException("未在 ${directory.path} 中找到数据库文件")
        }
        logSource("readTrips", sourceDb)

        val results = databaseReader.readTrips(source.copyToLocal(sourceDb))
        logResult("readTrips", results)
        results
    }

    /** Test hook; production uses BYD's shared energydata directory. */
    internal open fun energyDataDir(): File = File(EnergyDataSource.DEFAULT_DIRECTORY_PATH)

    fun isAvailable(): Boolean {
        val directory = energyDataDir()
        return availabilityCache.get(directory) { EnergyDataSource(context, directory).isAvailable() }
    }

    private fun currentSource(): EnergyDataSource = EnergyDataSource(context, energyDataDir())

    private fun logSource(operation: String, sourceDb: File) {
        Log.i(TAG, "$operation: source=${sourceDb.name} size=${sourceDb.length()}B")
        WjxFileLogger.i(TAG, "$operation source=${sourceDb.name} size=${sourceDb.length()}")
    }

    private fun logResult(operation: String, records: List<BydTripRecord>) {
        val zeroKm = records.count { it.tripKm <= 0.0 }
        Log.i(TAG, "$operation: ${records.size} rows ($zeroKm zero-km / idle drain)")
        WjxFileLogger.i(TAG, "$operation rows=${records.size} zeroKm=$zeroKm")
    }

    private companion object {
        const val TAG = "EnergyDataReader"
    }
}
