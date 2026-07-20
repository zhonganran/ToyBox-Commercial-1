package com.byd.wjx.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BydTripRecord(
    val id: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val duration: Long,       // seconds
    val tripKm: Double,       // distance
    val electricityKwh: Double // BYD's own estimate
)

/**
 * Reads BYD trip history from the energydata directory.
 *
 * /storage/emulated/0/energydata is a DIRECTORY containing .db files.
 * On DiLink, the main file is typically EC_database.db.
 *
 * Strategy:
 * 1. Try listFiles() to find .db files
 * 2. If listFiles() returns null (permission issue on Android 11+),
 *    try known filenames directly (EC_database.db)
 * 3. Copy to app-local storage to avoid locking BYD's database
 * 4. Read EnergyConsumption table from the local copy
 */
@Singleton
open class EnergyDataReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var cachedAvailability: Boolean? = null
    @Volatile private var availabilityCheckedAtNanos = 0L

    companion object {
        private const val TAG = "EnergyDataReader"
        private const val ENERGY_DIR_PATH = "/storage/emulated/0/energydata"
        private const val LOCAL_DB_NAME = "vehicle_ec.db"
        private const val AVAILABILITY_CACHE_NANOS = 15_000_000_000L
        // Known BYD energy database filenames
        private val KNOWN_DB_NAMES = listOf(
            "EC_database.db",
            "energydata.db",
            "energy.db",
            "EnergyConsumption.db"
        )
    }

    /**
     * Run full diagnostics and return a human-readable report.
     * Shows directory access, DB files, table structure, row counts, sample data.
     */
    suspend fun diagnose(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("=== 比亚迪数据存储 ===")

        val energyDir = File(ENERGY_DIR_PATH)
        sb.appendLine("路径：$ENERGY_DIR_PATH")
        sb.appendLine("存在：${formatBool(energyDir.exists())}")
        sb.appendLine("是目录：${formatBool(energyDir.isDirectory)}")

        if (!energyDir.exists()) {
            sb.appendLine("错误：目录不存在")
            return@withContext sb.toString()
        }

        val allFiles = energyDir.listFiles()
        if (allFiles == null) {
            sb.appendLine("文件列表：无结果（可能无访问权限）")
        } else {
            sb.appendLine("文件列表：${allFiles.size} 个文件")
            allFiles.forEach { f ->
                sb.appendLine("  ${f.name}（${f.length()} 字节，${if (f.isFile) "文件" else "目录"}）")
            }
        }

        sb.appendLine("\n检查已知文件名：")
        for (name in KNOWN_DB_NAMES) {
            val f = File(energyDir, name)
            val status = when {
                !f.exists() -> "未找到"
                f.length() == 0L -> "空文件"
                else -> "${f.length()} 字节，可读取=${formatBool(f.canRead())}"
            }
            sb.appendLine("  $name: $status")
        }

        val sourceDb = findDbViaListFiles(energyDir) ?: findDbViaKnownNames(energyDir)
        if (sourceDb == null) {
            sb.appendLine("\n错误：未找到数据库")
            return@withContext sb.toString()
        }

        sb.appendLine("\n来源：${sourceDb.name}（${sourceDb.length()} 字节）")

        try {
            val localDb = copyToLocal(sourceDb)
            sb.appendLine("本地副本：${localDb.absolutePath}（${localDb.length()} 字节）")

            val db = SQLiteDatabase.openDatabase(
                localDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            db.use { database ->
                // Tables
                val tables = mutableListOf<String>()
                database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table'", null
                ).use { c -> while (c.moveToNext()) tables.add(c.getString(0)) }
                sb.appendLine("\n=== 数据库表 ===")
                sb.appendLine(tables.joinToString(", "))

                if (!tables.contains("EnergyConsumption")) {
                    sb.appendLine("错误：未找到 EnergyConsumption 表")
                    return@withContext sb.toString()
                }

                // Columns
                database.rawQuery("PRAGMA table_info(EnergyConsumption)", null).use { c ->
                    sb.appendLine("\n=== EnergyConsumption 字段 ===")
                    while (c.moveToNext()) {
                        sb.appendLine("  ${c.getString(1)} (${c.getString(2)})")
                    }
                }

                // Counts
                val totalCount = database.rawQuery(
                    "SELECT COUNT(*) FROM EnergyConsumption", null
                ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
                val activeCount = database.rawQuery(
                    "SELECT COUNT(*) FROM EnergyConsumption WHERE is_deleted = 0", null
                ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

                sb.appendLine("\n=== 记录 ===")
                sb.appendLine("总数：$totalCount")
                sb.appendLine("有效：$activeCount")
                sb.appendLine("已删除：${totalCount - activeCount}")

                sb.appendLine("\n=== 示例数据（前 5 条） ===")
                database.rawQuery(
                    """SELECT _id, start_timestamp, end_timestamp, duration, trip, electricity, is_deleted
                       FROM EnergyConsumption
                       ORDER BY start_timestamp DESC
                       LIMIT 5""", null
                ).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val startTs = c.getLong(1)
                        val endTs = c.getLong(2)
                        val dur = c.getLong(3)
                        val km = c.getDouble(4)
                        val kwh = c.getDouble(5)
                        val deleted = c.getInt(6)
                        // Format timestamp for readability
                        val startFmt = try {
                            java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.US)
                                .format(java.util.Date(startTs * 1000L))
                        } catch (_: Exception) { "?" }
                        sb.appendLine("#$id: $startFmt，${dur} 秒，%.1f 公里，%.2f 千瓦时，已删除=$deleted".format(km, kwh))
                    }
                }
            }
        } catch (e: Exception) {
            sb.appendLine("读取数据库错误：${e.message}")
            WjxFileLogger.w(TAG, "diagnose read database error: ${e.message}")
        }

        sb.toString()
    }

    private fun formatBool(value: Boolean): String = if (value) "是" else "否"

    /**
     * Read-only change detector. Does NOT mutate watermark — caller must invoke
     * [markSourceProcessed] only after a successful sync, so a failed import
     * does not silently skip the next event.
     */
    fun peekSourceChanged(context: Context): Boolean {
        val prefs = context.getSharedPreferences("energydata_sync", Context.MODE_PRIVATE)
        val energyDir = File(ENERGY_DIR_PATH)
        if (!energyDir.exists()) return false

        val sourceDb = findDbViaListFiles(energyDir) ?: findDbViaKnownNames(energyDir) ?: return false

        val storedModified = prefs.getLong("lastModified", 0L)
        val storedSize = prefs.getLong("fileSize", 0L)
        val storedPath = prefs.getString("filePath", "") ?: ""

        return sourceDb.lastModified() != storedModified ||
            sourceDb.length() != storedSize ||
            sourceDb.absolutePath != storedPath
    }

    /** Persist current source-file fingerprint. Call only after successful import. */
    fun markSourceProcessed(context: Context) {
        val energyDir = File(ENERGY_DIR_PATH)
        if (!energyDir.exists()) return
        val sourceDb = findDbViaListFiles(energyDir) ?: findDbViaKnownNames(energyDir) ?: return
        context.getSharedPreferences("energydata_sync", Context.MODE_PRIVATE).edit()
            .putLong("lastModified", sourceDb.lastModified())
            .putLong("fileSize", sourceDb.length())
            .putString("filePath", sourceDb.absolutePath)
            .apply()
    }

    suspend fun readTripsSince(sinceTimestampSec: Long): List<BydTripRecord> = withContext(Dispatchers.IO) {
        val energyDir = File(ENERGY_DIR_PATH)
        if (!energyDir.exists()) {
            Log.i(TAG, "readTripsSince($sinceTimestampSec): directory $ENERGY_DIR_PATH missing — empty")
            WjxFileLogger.i(TAG, "readTripsSince: directory missing path=$ENERGY_DIR_PATH")
            return@withContext emptyList()
        }

        val sourceDb = findDbViaListFiles(energyDir)
            ?: findDbViaKnownNames(energyDir)
            ?: run {
                Log.i(TAG, "readTripsSince: no .db files in $ENERGY_DIR_PATH (Song / non-Leopard?) — empty")
                WjxFileLogger.i(TAG, "readTripsSince: no db files path=$ENERGY_DIR_PATH")
                return@withContext emptyList()
            }
        Log.i(TAG, "readTripsSince($sinceTimestampSec): source=${sourceDb.name} size=${sourceDb.length()}B")
        WjxFileLogger.i(TAG, "readTripsSince since=$sinceTimestampSec source=${sourceDb.name} size=${sourceDb.length()}")

        val localDb = copyToLocal(sourceDb)
        val db = SQLiteDatabase.openDatabase(localDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        db.use { database ->
            val cursor = database.rawQuery(
                """SELECT _id, start_timestamp, end_timestamp, duration, trip, electricity
                   FROM EnergyConsumption
                   WHERE is_deleted = 0 AND start_timestamp > ?
                   ORDER BY start_timestamp""",
                arrayOf(sinceTimestampSec.toString())
            )
            val results = mutableListOf<BydTripRecord>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    results.add(BydTripRecord(
                        id = c.getLong(0),
                        startTimestamp = c.getLong(1),
                        endTimestamp = c.getLong(2),
                        duration = c.getLong(3),
                        tripKm = c.getDouble(4),
                        electricityKwh = c.getDouble(5)
                    ))
                }
            }
            val zeroKm = results.count { it.tripKm <= 0.0 }
            Log.i(TAG, "readTripsSince: ${results.size} rows (${zeroKm} zero-km / idle drain)")
            WjxFileLogger.i(TAG, "readTripsSince rows=${results.size} zeroKm=$zeroKm")
            results
        }
    }

    suspend fun readTrips(): List<BydTripRecord> = withContext(Dispatchers.IO) {
        val energyDir = File(ENERGY_DIR_PATH)
        if (!energyDir.exists()) {
            Log.w(TAG, "readTrips: directory $ENERGY_DIR_PATH missing — throwing")
            WjxFileLogger.w(TAG, "readTrips: directory missing path=$ENERGY_DIR_PATH")
            throw EnergyDataException("未找到 energydata 目录：$ENERGY_DIR_PATH")
        }

        val sourceDb = findDbViaListFiles(energyDir)
            ?: findDbViaKnownNames(energyDir)
            ?: run {
                Log.w(TAG, "readTrips: no .db files in $ENERGY_DIR_PATH — throwing")
                WjxFileLogger.w(TAG, "readTrips: no db files path=$ENERGY_DIR_PATH")
                throw EnergyDataException("未在 $ENERGY_DIR_PATH 中找到数据库文件")
            }
        Log.i(TAG, "readTrips: source=${sourceDb.name} size=${sourceDb.length()}B")
        WjxFileLogger.i(TAG, "readTrips source=${sourceDb.name} size=${sourceDb.length()}")

        val localDb = copyToLocal(sourceDb)
        val results = readTripsFromDb(localDb)
        val zeroKm = results.count { it.tripKm <= 0.0 }
        Log.i(TAG, "readTrips: ${results.size} rows (${zeroKm} zero-km / idle drain)")
        WjxFileLogger.i(TAG, "readTrips rows=${results.size} zeroKm=$zeroKm")
        results
    }

    /** Hook for tests to swap the dir. Default points at the real ENERGY_DIR_PATH. */
    internal open fun energyDataDir(): File = File(ENERGY_DIR_PATH)

    /**
     * Phase 1.5 mode switch: does the BYD energydata directory hold a readable trips DB?
     * Reuses existing find helpers — no new file probing logic.
     */
    fun isAvailable(): Boolean {
        val now = System.nanoTime()
        val cached = cachedAvailability
        if (cached != null && now - availabilityCheckedAtNanos < AVAILABILITY_CACHE_NANOS) {
            return cached
        }
        return synchronized(this) {
            val refreshedNow = System.nanoTime()
            val refreshedCached = cachedAvailability
            if (refreshedCached != null &&
                refreshedNow - availabilityCheckedAtNanos < AVAILABILITY_CACHE_NANOS
            ) {
                refreshedCached
            } else {
                probeAvailability().also {
                    cachedAvailability = it
                    availabilityCheckedAtNanos = refreshedNow
                }
            }
        }
    }

    private fun probeAvailability(): Boolean {
        val dir = energyDataDir()
        if (!dir.exists() || !dir.isDirectory) return false
        val db = findDbViaListFiles(dir) ?: findDbViaKnownNames(dir) ?: return false
        return db.canRead() && db.length() >= 16
    }

    private fun findDbViaListFiles(dir: File): File? {
        val allFiles = dir.listFiles() ?: return null
        return allFiles
            .filter { it.isFile && (it.name.endsWith(".db", true) || it.name.endsWith(".sqlite", true)) }
            .maxByOrNull { it.lastModified() }
    }

    private fun findDbViaKnownNames(dir: File): File? {
        return KNOWN_DB_NAMES
            .map { File(dir, it) }
            .firstOrNull { it.exists() && it.length() > 0 }
    }

    private fun copyToLocal(source: File): File {
        val extDir = context.getExternalFilesDir(null)
            ?: throw EnergyDataException("ExternalFilesDir 不可用")
        val localFile = File(extDir, LOCAL_DB_NAME)
        FileInputStream(source).use { input ->
            FileOutputStream(localFile).use { output ->
                input.copyTo(output)
            }
        }
        return localFile
    }

    private fun readTripsFromDb(dbFile: File): List<BydTripRecord> {
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        return db.use { database ->
            val cursor = database.rawQuery(
                """SELECT _id, start_timestamp, end_timestamp, duration, trip, electricity
                   FROM EnergyConsumption
                   WHERE is_deleted = 0
                   ORDER BY start_timestamp DESC""",
                null
            )
            val results = mutableListOf<BydTripRecord>()
            cursor.use { c ->
                val colId = c.getColumnIndexOrThrow("_id")
                val colStart = c.getColumnIndexOrThrow("start_timestamp")
                val colEnd = c.getColumnIndexOrThrow("end_timestamp")
                val colDuration = c.getColumnIndexOrThrow("duration")
                val colTrip = c.getColumnIndexOrThrow("trip")
                val colElectricity = c.getColumnIndexOrThrow("electricity")

                while (c.moveToNext()) {
                    results.add(
                        BydTripRecord(
                            id = c.getLong(colId),
                            startTimestamp = c.getLong(colStart),
                            endTimestamp = c.getLong(colEnd),
                            duration = c.getLong(colDuration),
                            tripKm = c.getDouble(colTrip),
                            electricityKwh = c.getDouble(colElectricity)
                        )
                    )
                }
            }
            results
        }
    }
}

/** Thrown when BYD energy data is unavailable or unreadable. */
class EnergyDataException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
