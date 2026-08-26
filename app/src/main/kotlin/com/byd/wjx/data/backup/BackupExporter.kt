package com.byd.wjx.data.backup

import android.content.Context
import android.net.Uri
import com.byd.wjx.data.local.database.AppDatabase
import com.byd.wjx.log.WjxFileLogger
import java.io.File
import java.util.UUID

internal class BackupExporter(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val preferenceStore: BackupPreferenceStore,
    private val backupFormatVersion: Int,
) {
    fun export(destination: Uri) {
        val databaseSnapshot = createDatabaseSnapshot()
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
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IllegalStateException("无法打开所选备份文件")
            output.use {
                BackupArchiveWriter.write(
                    output = it,
                    databaseFile = databaseSnapshot,
                    preferencesJson = preferencesJson,
                    manifestJson = manifestJson,
                )
            }
        } finally {
            databaseSnapshot.delete()
        }
        WjxFileLogger.i(
            TAG,
            "backup exported uriScheme=${destination.scheme.orEmpty()} schema=${manifest.dbSchemaVersion}",
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
