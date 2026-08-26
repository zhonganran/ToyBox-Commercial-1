package com.byd.wjx.data.backup

import android.content.Context
import android.net.Uri
import com.byd.wjx.data.local.database.AppDatabase

/** Public backup facade; format, storage, validation, export, and restore have separate owners. */
class BackupManager(
    context: Context,
    appDatabase: AppDatabase,
    prefsFileNames: List<String>,
    deviceProtectedPrefsFileNames: Set<String> = emptySet(),
) {
    private val preferenceStore = BackupPreferenceStore(
        context = context,
        fileNames = prefsFileNames,
        deviceProtectedFileNames = deviceProtectedPrefsFileNames,
    )
    private val exporter = BackupExporter(
        context = context,
        appDatabase = appDatabase,
        preferenceStore = preferenceStore,
        backupFormatVersion = BACKUP_FORMAT_VERSION,
    )
    private val restorer = BackupRestorer(
        context = context,
        appDatabase = appDatabase,
        preferenceStore = preferenceStore,
        backupFormatVersion = BACKUP_FORMAT_VERSION,
    )

    fun export(destination: Uri) = exporter.export(destination)

    fun restore(uri: Uri) {
        restorer.restore(uri)
    }

    companion object {
        internal const val BACKUP_FORMAT_VERSION = 1

        fun serializePrefs(data: Map<String, Map<String, Any?>>): String =
            BackupPreferencesCodec.serialize(data)

        fun deserializePrefs(json: String): Map<String, Map<String, Any?>> =
            BackupPreferencesCodec.deserialize(json)

        fun isRestorable(backupSchemaVersion: Int, currentSchemaVersion: Int): Boolean =
            backupSchemaVersion == currentSchemaVersion
    }
}
