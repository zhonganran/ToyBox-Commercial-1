package com.byd.wjx.data.backup

import android.content.Context
import com.byd.wjx.data.local.database.AppDatabase
import com.byd.wjx.log.WjxFileLogger
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal class BackupExporter(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val preferenceStore: BackupPreferenceStore,
    private val backupFormatVersion: Int,
) {
    fun export(destination: File) {
        val parent = destination.parentFile
            ?: throw IllegalStateException("备份文件没有有效的保存目录")
        check(parent.isDirectory || parent.mkdirs()) {
            "无法创建备份目录：${parent.absolutePath}"
        }
        val databaseSnapshot = createDatabaseSnapshot()
        val pendingFile = File(parent, ".${destination.name}.${UUID.randomUUID()}.tmp")
        val preferencesJson = BackupPreferencesCodec.serialize(preferenceStore.collectNonEmpty())
        val manifest = BackupManifest(
            backupFormatVersion = backupFormatVersion,
            appVersionCode = appVersionCode(),
            dbSchemaVersion = AppDatabase.SCHEMA_VERSION,
            createdAt = System.currentTimeMillis(),
            preferenceFiles = preferenceStore.fileNames.sorted(),
        )
        val manifestJson = BackupManifestCodec.serialize(manifest)
        try {
            FileOutputStream(pendingFile).buffered().use {
                BackupArchiveWriter.write(
                    output = it,
                    databaseFile = databaseSnapshot,
                    preferencesJson = preferencesJson,
                    manifestJson = manifestJson,
                )
            }
            check(pendingFile.isFile && pendingFile.length() > 0L) {
                "生成的备份文件为空"
            }
            check(pendingFile.renameTo(destination)) {
                "无法保存备份文件：${destination.absolutePath}"
            }
        } finally {
            databaseSnapshot.delete()
            pendingFile.delete()
        }
        WjxFileLogger.i(
            TAG,
            "backup exported path=${destination.absolutePath} schema=${manifest.dbSchemaVersion}",
        )
    }

    private fun createDatabaseSnapshot(): File {
        val snapshot = File(context.cacheDir, "backup-snapshot-${UUID.randomUUID()}.db")
        return try {
            val escapedPath = snapshot.absolutePath.replace("'", "''")
            appDatabase.openHelper.writableDatabase.execSQL("VACUUM INTO '$escapedPath'")
            require(snapshot.isFile && snapshot.canRead() && snapshot.length() > 0L) {
                "数据库一致快照为空"
            }
            snapshot
        } catch (error: Exception) {
            snapshot.delete()
            throw IllegalStateException(
                "数据库一致快照创建失败：${error.message ?: "未知错误"}",
                error,
            )
        }
    }

    private fun appVersionCode(): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    }.getOrDefault(0)

    private companion object {
        const val TAG = "BackupManager"
    }
}
