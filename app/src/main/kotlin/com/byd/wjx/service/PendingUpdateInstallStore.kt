package com.byd.wjx.service

import android.content.Context
import java.io.File

internal data class PendingUpdateInstall(
    val file: File,
    val update: UpdateChecker.UpdateInfo,
)

/** Persists a verified APK while Android sends the user through unknown-source settings. */
internal class PendingUpdateInstallStore(
    private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(file: File, update: UpdateChecker.UpdateInfo) {
        preferences.edit()
            .putString(KEY_FILE_PATH, file.absolutePath)
            .putString(KEY_VERSION, update.version)
            .putString(KEY_DOWNLOAD_URL, update.downloadUrl)
            .putString(KEY_RELEASE_NOTES, update.releaseNotes)
            .putBoolean(KEY_FORCE_UPDATE, update.forceUpdate)
            .putLong(KEY_VERSION_CODE, update.versionCode)
            .putString(KEY_SHA256, update.sha256)
            .putString(KEY_PACKAGE_NAME, update.packageName)
            .putString(KEY_CERTIFICATE_SHA256, update.certificateSha256)
            .putLong(KEY_APK_SIZE, update.apkSize)
            .apply()
    }

    fun load(): PendingUpdateInstall? {
        val filePath = preferences.getString(KEY_FILE_PATH, null)?.takeIf(String::isNotBlank)
            ?: return null
        val version = preferences.getString(KEY_VERSION, null)?.takeIf(String::isNotBlank)
            ?: return null
        return PendingUpdateInstall(
            file = File(filePath),
            update = UpdateChecker.UpdateInfo(
                version = version,
                downloadUrl = preferences.getString(KEY_DOWNLOAD_URL, "").orEmpty(),
                releaseNotes = preferences.getString(KEY_RELEASE_NOTES, "").orEmpty(),
                forceUpdate = preferences.getBoolean(KEY_FORCE_UPDATE, false),
                versionCode = preferences.getLong(KEY_VERSION_CODE, 0L),
                sha256 = preferences.getString(KEY_SHA256, "").orEmpty(),
                packageName = preferences.getString(KEY_PACKAGE_NAME, "").orEmpty(),
                certificateSha256 = preferences.getString(KEY_CERTIFICATE_SHA256, "").orEmpty(),
                apkSize = preferences.getLong(KEY_APK_SIZE, 0L),
            ),
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "pending_update_install"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_VERSION = "version"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_RELEASE_NOTES = "release_notes"
        const val KEY_FORCE_UPDATE = "force_update"
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_SHA256 = "sha256"
        const val KEY_PACKAGE_NAME = "package_name"
        const val KEY_CERTIFICATE_SHA256 = "certificate_sha256"
        const val KEY_APK_SIZE = "apk_size"
    }
}
