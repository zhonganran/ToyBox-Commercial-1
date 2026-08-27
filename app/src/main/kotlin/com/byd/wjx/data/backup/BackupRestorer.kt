package com.byd.wjx.data.backup

import android.content.Context
import android.net.Uri
import com.byd.wjx.data.local.database.AppDatabase
import com.byd.wjx.log.WjxFileLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal class BackupRestorer(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val preferenceStore: BackupPreferenceStore,
    private val backupFormatVersion: Int,
) {
    private val databaseValidator = BackupDatabaseValidator(appDatabase)

    fun restore(uri: Uri) {
        val targetDatabase = context.getDatabasePath(BackupArchiveContract.DATABASE_FILE_NAME)
        val databaseDirectory = targetDatabase.parentFile
        databaseDirectory?.mkdirs()
        val temporaryDatabase = File(
            databaseDirectory,
            "${BackupArchiveContract.DATABASE_FILE_NAME}.restore.tmp",
        )
        var rollbackDatabase: File? = null
        try {
            val payload = context.contentResolver.openInputStream(uri)?.use { input ->
                BackupArchiveReader.extract(input, temporaryDatabase)
            } ?: throw IllegalStateException("无法打开备份文件")

            val manifest = BackupManifestCodec.deserialize(payload.manifestJson)
            BackupManifestCodec.requireCurrent(
                manifest = manifest,
                currentFormatVersion = backupFormatVersion,
                currentSchemaVersion = AppDatabase.SCHEMA_VERSION,
                currentPreferenceFiles = preferenceStore.fileNames,
            )
            val preferences = preferenceStore.restorableSubset(
                preferences = BackupPreferencesCodec.deserialize(payload.preferencesJson),
                declaredFiles = manifest.preferenceFiles,
            )
            databaseValidator.validate(
                file = temporaryDatabase,
                expectedSchemaVersion = AppDatabase.SCHEMA_VERSION,
                expectedRoomIdentityHash = databaseValidator.currentRoomIdentityHash(),
            )

            val rollbackPreferences = preferenceStore.collectNonEmpty()
            rollbackDatabase = createDatabaseSnapshot()
            appDatabase.close()
            BackupRestoreTransaction.execute(
                applyDatabase = {
                    replaceDatabase(temporaryDatabase, targetDatabase)
                    deleteDatabaseSidecars(databaseDirectory)
                },
                applyPreferences = { preferenceStore.replaceAll(preferences) },
                rollbackDatabase = {
                    copyDatabase(rollbackDatabase, targetDatabase)
                    deleteDatabaseSidecars(databaseDirectory)
                },
                rollbackPreferences = { preferenceStore.replaceAll(rollbackPreferences) },
            )
            WjxFileLogger.i(
                TAG,
                "backup restored uri=$uri schema=${manifest.dbSchemaVersion} " +
                    "prefsFiles=${preferenceStore.fileNames.size}",
            )
        } finally {
            temporaryDatabase.delete()
            rollbackDatabase?.delete()
        }
    }

    private fun createDatabaseSnapshot(): File {
        val snapshot = File(context.cacheDir, "backup-rollback-${UUID.randomUUID()}.db")
        return try {
            val escapedPath = snapshot.absolutePath.replace("'", "''")
            appDatabase.openHelper.writableDatabase.execSQL("VACUUM INTO '$escapedPath'")
            require(snapshot.isFile && snapshot.canRead() && snapshot.length() > 0L) {
                "恢复前数据库快照为空"
            }
            snapshot
        } catch (error: Exception) {
            snapshot.delete()
            throw IllegalStateException("无法创建恢复前安全快照", error)
        }
    }

    private fun replaceDatabase(temporaryDatabase: File, targetDatabase: File) {
        try {
            Files.move(
                temporaryDatabase.toPath(),
                targetDatabase.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (atomicError: Exception) {
            try {
                Files.move(
                    temporaryDatabase.toPath(),
                    targetDatabase.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (replaceError: Exception) {
                replaceError.addSuppressed(atomicError)
                WjxFileLogger.e(TAG, "restore failed: cannot replace database", replaceError)
                throw IllegalStateException("恢复时无法替换数据库文件", replaceError)
            }
        }
    }

    private fun copyDatabase(sourceDatabase: File?, targetDatabase: File) {
        val source = requireNotNull(sourceDatabase) { "恢复前数据库快照不存在" }
        Files.copy(
            source.toPath(),
            targetDatabase.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun deleteDatabaseSidecars(databaseDirectory: File?) {
        File(databaseDirectory, "${BackupArchiveContract.DATABASE_FILE_NAME}-wal").delete()
        File(databaseDirectory, "${BackupArchiveContract.DATABASE_FILE_NAME}-shm").delete()
    }

    private companion object {
        const val TAG = "BackupManager"
    }
}
