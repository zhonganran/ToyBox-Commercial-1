package com.byd.wjx.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.data.local.database.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manifest stored inside every backup zip.
 * Contains enough metadata to guard against restoring a newer-schema backup.
 */
data class BackupManifest(
    val appVersionCode: Int,
    val dbSchemaVersion: Int,
    val createdAt: Long,
)

/**
 * Handles full-app config backup (export to zip) and restore (import from zip).
 *
 * The zip contains three entries:
 *   - wjx.db         — Room database file (WAL folded in before copy)
 *   - prefs.json     — whitelisted SharedPreferences with explicit type tags
 *   - manifest.json  — version/schema metadata
 *
 * Inject via Hilt; pure serialization helpers live in the companion object so
 * unit tests can call them without Android.
 */
class BackupManager(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val prefsFileNames: List<String>,
    private val deviceProtectedPrefsFileNames: Set<String> = emptySet(),
) {

    init {
        require(deviceProtectedPrefsFileNames.all { it in prefsFileNames }) {
            "Device-protected preference files must also be included in prefsFileNames"
        }
    }

    companion object {

        private const val TAG = "BackupManager"

        private const val ENTRY_DB = "wjx.db"
        private const val DB_FILE_NAME = "wjx.db"
        private const val ENTRY_PREFS = "prefs.json"
        private const val ENTRY_MANIFEST = "manifest.json"

        // JSON type-tag constants
        private const val T_STRING = "String"
        private const val T_INT = "Int"
        private const val T_LONG = "Long"
        private const val T_FLOAT = "Float"
        private const val T_BOOLEAN = "Boolean"
        private const val T_STRING_SET = "StringSet"

        /**
         * Serialize a map of {fileName -> {key -> value}} into typed JSON.
         * Each value is wrapped as {"t": "<Type>", "v": <value>}.
         * Float values are stored as Double (Android org.json has no put(String, Float) overload).
         * Null values are skipped.
         */
        fun serializePrefs(data: Map<String, Map<String, Any?>>): String {
            val root = JSONObject()
            for ((fileName, entries) in data) {
                val fileObj = JSONObject()
                for ((key, rawValue) in entries) {
                    if (rawValue == null) continue
                    val entry = JSONObject()
                    when (rawValue) {
                        is String -> {
                            entry.put("t", T_STRING)
                            entry.put("v", rawValue)
                        }
                        is Int -> {
                            entry.put("t", T_INT)
                            entry.put("v", rawValue)
                        }
                        is Long -> {
                            entry.put("t", T_LONG)
                            entry.put("v", rawValue)
                        }
                        is Float -> {
                            // Cast to Double: Android org.json has no put(String, Float)
                            entry.put("t", T_FLOAT)
                            entry.put("v", rawValue.toDouble())
                        }
                        is Boolean -> {
                            entry.put("t", T_BOOLEAN)
                            entry.put("v", rawValue)
                        }
                        is Set<*> -> {
                            entry.put("t", T_STRING_SET)
                            val arr = JSONArray()
                            for (item in rawValue) {
                                arr.put(item?.toString() ?: "")
                            }
                            entry.put("v", arr)
                        }
                        else -> continue // unknown type, skip
                    }
                    fileObj.put(key, entry)
                }
                root.put(fileName, fileObj)
            }
            return root.toString()
        }

        /**
         * Deserialize typed JSON back to {fileName -> {key -> value}}.
         * Reconstructs exact Kotlin types: Int, Long, Float, Boolean, String, Set<String>.
         */
        fun deserializePrefs(json: String): Map<String, Map<String, Any?>> {
            val result = mutableMapOf<String, Map<String, Any?>>()
            val root = JSONObject(json)
            for (fileName in root.keys()) {
                val fileObj = root.getJSONObject(fileName)
                val entries = mutableMapOf<String, Any?>()
                for (key in fileObj.keys()) {
                    val entry = fileObj.getJSONObject(key)
                    val type = entry.getString("t")
                    val value: Any? = when (type) {
                        T_STRING -> entry.getString("v")
                        T_INT -> entry.getInt("v")
                        T_LONG -> entry.getLong("v")
                        T_FLOAT -> entry.getDouble("v").toFloat()
                        T_BOOLEAN -> entry.getBoolean("v")
                        T_STRING_SET -> {
                            val arr = entry.getJSONArray("v")
                            val set = mutableSetOf<String>()
                            for (i in 0 until arr.length()) {
                                set.add(arr.getString(i))
                            }
                            set as Set<String>
                        }
                        else -> null
                    }
                    if (value != null) entries[key] = value
                }
                result[fileName] = entries
            }
            return result
        }

        /**
         * Returns true when the backup belongs to this commercial product generation and Room
         * can migrate it forward. Pre-baseline backups and newer-schema backups are rejected.
         */
        fun isRestorable(
            backupSchemaVersion: Int,
            currentSchemaVersion: Int,
            baselineSchemaVersion: Int = AppDatabase.COMMERCIAL_BASELINE_SCHEMA_VERSION,
        ): Boolean = backupSchemaVersion in baselineSchemaVersion..currentSchemaVersion
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    /**
     * Export the full app state to a zip file in Downloads.
     * Returns the created File.
     *
     * Steps:
     *   1. Fold WAL into the main DB file (PRAGMA wal_checkpoint(TRUNCATE)).
     *   2. Read the DB bytes.
     *   3. Collect whitelisted SharedPreferences.
     *   4. Build manifest JSON.
     *   5. Write zip to Downloads.
     */
    fun export(): File {
        // 1. Fold WAL so the DB file is self-contained
        try {
            val cursor = appDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)", null)
            cursor.close()
        } catch (_: Exception) {
            // Non-fatal: proceed anyway; worst case WAL entries are missing from the snapshot
        }

        // 2. Read DB bytes
        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        val dbBytes = dbFile.readBytes()

        // 3. Collect SharedPreferences (only files with at least one entry)
        val prefsData = mutableMapOf<String, Map<String, Any?>>()
        for (name in prefsFileNames) {
            val all = preferencesContext(name).getSharedPreferences(name, Context.MODE_PRIVATE).all
            if (all.isNotEmpty()) {
                prefsData[name] = all
            }
        }
        val prefsJson = serializePrefs(prefsData)

        // 4. Build manifest
        val versionCode = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: Exception) { 0 }
        val manifest = BackupManifest(
            appVersionCode = versionCode,
            dbSchemaVersion = AppDatabase.SCHEMA_VERSION,
            createdAt = System.currentTimeMillis(),
        )
        val manifestJson = JSONObject().apply {
            put("appVersionCode", manifest.appVersionCode)
            put("dbSchemaVersion", manifest.dbSchemaVersion)
            put("createdAt", manifest.createdAt)
        }.toString()

        // 5. Write zip to Downloads
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val zipFile = File(downloadsDir, "wjx_backup_$timestamp.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_DB))
            zip.write(dbBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_PREFS))
            zip.write(prefsJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        WjxFileLogger.i(TAG, "backup exported file=${zipFile.absolutePath} size=${zipFile.length()} schema=${manifest.dbSchemaVersion}")
        return zipFile
    }

    // -------------------------------------------------------------------------
    // Restore
    // -------------------------------------------------------------------------

    /**
     * Restore the full app state from a previously exported backup zip.
     *
     * Order is deliberate: everything is validated BEFORE the destructive replace
     * so a corrupt or incompatible zip never touches the live database.
     *
     * After this function returns the caller MUST restart the process so that
     * Room opens the replaced DB file fresh.
     */
    fun restore(uri: Uri) {
        // 1. Read the zip into memory (extract all three required entries)
        var dbBytes: ByteArray? = null
        var prefsJson: String? = null
        var manifestJson: String? = null

        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开备份文件")

        inputStream.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        ENTRY_DB -> dbBytes = zip.readBytes()
                        ENTRY_PREFS -> prefsJson = zip.readBytes().toString(Charsets.UTF_8)
                        ENTRY_MANIFEST -> manifestJson = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        // 2. Validate completeness
        if (dbBytes == null || prefsJson == null || manifestJson == null) {
            WjxFileLogger.w(TAG, "restore rejected: missing required entries uri=$uri")
            throw IllegalStateException(
                "备份文件损坏或不完整。" +
                "缺少必要内容：$ENTRY_DB, $ENTRY_PREFS, $ENTRY_MANIFEST"
            )
        }

        // ---------------------------------------------------------------------
        // PRE-VALIDATION — everything that can fail MUST be checked here, before
        // a single destructive operation runs. A backup with a valid manifest but
        // a corrupt DB or malformed prefs.json must be rejected while the live DB
        // is still intact, never half-applied.
        // ---------------------------------------------------------------------

        // 2a. Manifest schema compatibility
        val manifestObj = JSONObject(manifestJson!!)
        val backupSchema = manifestObj.getInt("dbSchemaVersion")
        if (!isRestorable(backupSchema, AppDatabase.SCHEMA_VERSION)) {
            WjxFileLogger.w(TAG, "restore rejected: backupSchema=$backupSchema current=${AppDatabase.SCHEMA_VERSION}")
            val message = if (backupSchema < AppDatabase.COMMERCIAL_BASELINE_SCHEMA_VERSION) {
                "该备份来自旧版软件（数据库版本 $backupSchema），" +
                    "当前全新商业版不支持导入。"
            } else {
                "该备份来自更新版本的应用 " +
                    "（备份数据库版本 $backupSchema，当前 ${AppDatabase.SCHEMA_VERSION}）。" +
                    "请先升级应用后再恢复。"
            }
            throw IllegalStateException(message)
        }

        // 2b. Deserialize prefs now — malformed JSON throws here, before any destructive step.
        val prefsMap = deserializePrefs(prefsJson!!)

        // 2c. Write the DB bytes to a temp file and verify it is a real, intact SQLite
        //     database. openDatabase rejects a non-SQLite file; quick_check catches
        //     structural corruption. Bad file -> throw, temp deleted, live DB untouched.
        val targetDbFile = context.getDatabasePath(DB_FILE_NAME)
        val dbDir = targetDbFile.parentFile
        dbDir?.mkdirs()
        val tmpDbFile = File(dbDir, "$DB_FILE_NAME.restore.tmp")
        tmpDbFile.delete()
        tmpDbFile.writeBytes(dbBytes!!)
        validateSqliteFile(tmpDbFile)

        // ---------------------------------------------------------------------
        // DESTRUCTIVE PART — only reached once the backup is fully validated.
        // ---------------------------------------------------------------------

        // 3. Close the database so we can safely replace the file.
        appDatabase.close()

        // 4. Swap the validated temp file in via an atomic rename. The live wjx.db
        //    is never absent: it is replaced atomically. If rename fails the original is
        //    still in place and NOTHING has been mutated yet, so we abort (throw) rather
        //    than risk destroying it with a non-atomic delete+copy. This also closes the
        //    race where the foreground TrackingService could re-open Room mid-restore and
        //    find a missing file.
        //
        //    A successful rename is the POINT OF NO RETURN: from here the app state is
        //    already changed, so no later step may throw past the caller's restart. File
        //    deletes below do not throw, and the prefs loop is best-effort (see below).
        if (!tmpDbFile.renameTo(targetDbFile)) {
            tmpDbFile.delete()
            WjxFileLogger.e(TAG, "restore failed: cannot replace database")
            throw IllegalStateException("恢复时无法替换数据库文件")
        }
        // Drop stale WAL/SHM left from the old DB AFTER the swap. The restored file is
        // self-contained (WAL was folded in at export time).
        File(dbDir, "$DB_FILE_NAME-wal").delete()
        File(dbDir, "$DB_FILE_NAME-shm").delete()

        // 5. Restore SharedPreferences.
        // FULL REPLACE: clear EVERY whitelisted file first, even files absent from the
        // backup (they were empty at export time). Iterating only over prefsMap would let
        // stale prefs on the current device survive a "full replace".
        for (fileName in prefsFileNames) {
            val editor = preferencesContext(fileName)
                .getSharedPreferences(fileName, Context.MODE_PRIVATE)
                .edit()
            editor.clear()
            val entries = prefsMap[fileName]
            if (entries != null) {
                for ((key, value) in entries) {
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                    }
                }
            }
            if (!editor.commit()) {
                // Past the point of no return: the DB is already swapped. A failed prefs
                // commit (rare, disk-level error) must NOT throw here — that would skip the
                // caller's restart and freeze the app half-restored. Log and continue so
                // the remaining files still apply and the process still restarts.
                Log.w(TAG, "Failed to commit prefs file during restore: $fileName")
                WjxFileLogger.w(TAG, "Failed to commit prefs file during restore: $fileName")
            }
        }
        WjxFileLogger.i(TAG, "backup restored uri=$uri schema=$backupSchema prefsFiles=${prefsFileNames.size}")
        // Caller is responsible for restarting the process after this returns.
    }

    private fun preferencesContext(fileName: String): Context {
        if (fileName !in deviceProtectedPrefsFileNames || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return context
        }
        return context.createDeviceProtectedStorageContext()
    }

    /**
     * Verify [file] is a readable, structurally intact SQLite database.
     * Throws IllegalStateException (and deletes the temp file) if it is not a SQLite file
     * or fails quick_check. Opened read-only so a backup from an older (but compatible)
     * schema is not migrated here.
     */
    private fun validateSqliteFile(file: File) {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            file.delete()
            WjxFileLogger.e(TAG, "backup sqlite validation open failed: ${e.message}", e)
            throw IllegalStateException("备份中的数据库文件损坏，或不是 SQLite 数据库", e)
        }
        val ok = try {
            db.rawQuery("PRAGMA quick_check", null).use { c ->
                c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
            }
        } catch (e: SQLiteException) {
            false
        } finally {
            db.close()
        }
        if (!ok) {
            file.delete()
            WjxFileLogger.e(TAG, "backup sqlite quick_check failed")
            throw IllegalStateException("备份中的数据库文件未通过完整性检查")
        }
    }
}
