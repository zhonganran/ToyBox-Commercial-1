package com.byd.wjx.data.backup

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.byd.wjx.data.local.database.AppDatabase
import com.byd.wjx.log.WjxFileLogger
import java.io.File

internal class BackupDatabaseValidator(
    private val appDatabase: AppDatabase,
) {
    fun currentRoomIdentityHash(): String = appDatabase.query(
        "SELECT identity_hash FROM room_master_table WHERE id = 42",
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            throw IllegalStateException("当前数据库缺少 Room 结构标识")
        }
        cursor.getString(0)
    }

    fun validate(
        file: File,
        expectedSchemaVersion: Int,
        expectedRoomIdentityHash: String,
    ) {
        val database = openReadOnly(file)
        val validation = try {
            val schemaVersion = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else -1
            }
            val roomIdentityHash = database.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42",
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            val quickCheckOk = database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            Triple(schemaVersion, roomIdentityHash, quickCheckOk)
        } catch (error: SQLiteException) {
            file.delete()
            WjxFileLogger.e(TAG, "backup sqlite validation query failed: ${error.message}", error)
            throw IllegalStateException("备份中的数据库不是当前应用的有效数据库", error)
        } finally {
            database.close()
        }
        when {
            validation.first != expectedSchemaVersion -> {
                file.delete()
                throw IllegalStateException(
                    "备份数据库实际版本 ${validation.first} 与当前版本 $expectedSchemaVersion 不一致",
                )
            }
            validation.second != expectedRoomIdentityHash -> {
                file.delete()
                throw IllegalStateException("备份数据库结构与当前版本不一致")
            }
            !validation.third -> {
                file.delete()
                throw IllegalStateException("备份中的数据库文件未通过完整性检查")
            }
        }
    }

    private fun openReadOnly(file: File): SQLiteDatabase = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    } catch (error: SQLiteException) {
        file.delete()
        WjxFileLogger.e(TAG, "backup sqlite validation open failed: ${error.message}", error)
        throw IllegalStateException("备份中的数据库文件损坏，或不是 SQLite 数据库", error)
    }

    private companion object {
        const val TAG = "BackupManager"
    }
}
