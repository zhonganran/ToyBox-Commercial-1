package com.byd.wjx.data.backup

data class BackupManifest(
    val backupFormatVersion: Int,
    val appVersionCode: Int,
    val dbSchemaVersion: Int,
    val createdAt: Long,
    val preferenceFiles: List<String>,
)

internal data class BackupArchivePayload(
    val preferencesJson: String,
    val manifestJson: String,
)
