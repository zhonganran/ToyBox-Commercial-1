package com.byd.wjx.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.byd.wjx.R
import com.byd.wjx.log.WjxFileLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Owns update download progress, integrity verification, and installer handoff. */
class UpdateDownloader @Inject constructor() {
    private val installer = UpdateInstaller()

    suspend fun downloadAndInstall(
        context: Context,
        update: UpdateChecker.UpdateInfo,
        onProgress: (String) -> Unit = {},
    ): UpdateChecker.InstallOutcome = withContext(Dispatchers.IO) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val destination = prepareDestination(context, update.version)
        WjxFileLogger.i(TAG, "download start version=${update.version}")

        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("超级玩具箱 ${update.version}")
            .setDescription(context.getString(R.string.update_download_notification_description))
            .setDestinationUri(Uri.fromFile(destination))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val downloadId = downloadManager.enqueue(request)
        onProgress(context.getString(R.string.update_downloading_start))

        awaitDownload(context, downloadManager, downloadId, update, destination, onProgress)
        verifyDownloadedApk(context, destination, update)
        installer.launchOrRequestPermission(context, destination, update, onProgress)
    }

    suspend fun continuePendingInstall(context: Context): Boolean =
        installer.continuePendingInstall(context)

    suspend fun resumePendingInstall(context: Context): Boolean =
        installer.resumePendingInstall(context)

    private fun prepareDestination(context: Context, version: String): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("更新目录不可用")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IllegalStateException("无法创建更新目录")
        }
        return File(directory, "超级玩具箱-$version.apk").also { file ->
            if (file.exists() && !file.delete()) {
                throw IllegalStateException("无法清理旧更新包")
            }
        }
    }

    private suspend fun awaitDownload(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        update: UpdateChecker.UpdateInfo,
        destination: File,
        onProgress: (String) -> Unit,
    ) {
        while (true) {
            val status = queryStatus(downloadManager, downloadId, context, onProgress)
                ?: run {
                    WjxFileLogger.w(TAG, "download row missing version=${update.version} id=$downloadId")
                    destination.delete()
                    throw Exception(context.getString(R.string.update_download_error, -1))
                }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    onProgress(context.getString(R.string.update_downloading_done))
                    WjxFileLogger.i(
                        TAG,
                        "download success version=${update.version} file=${destination.absolutePath}",
                    )
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = queryFailureReason(downloadManager, downloadId)
                    WjxFileLogger.w(TAG, "download failed version=${update.version} reason=$reason")
                    destination.delete()
                    throw Exception(context.getString(R.string.update_download_error, reason))
                }
                DownloadManager.STATUS_PAUSED ->
                    onProgress(context.getString(R.string.update_downloading_paused))
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun queryStatus(
        downloadManager: DownloadManager,
        downloadId: Long,
        context: Context,
        onProgress: (String) -> Unit,
    ): Int? = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        if (status == DownloadManager.STATUS_RUNNING) {
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            )
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            )
            if (total > 0L) {
                val percent = (downloaded * 100L / total).toInt()
                onProgress(context.getString(R.string.update_downloading_progress, percent))
            }
        }
        status
    }

    private fun queryFailureReason(downloadManager: DownloadManager, downloadId: Long): Int =
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use -1
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        } ?: -1

    private fun verifyDownloadedApk(
        context: Context,
        destination: File,
        update: UpdateChecker.UpdateInfo,
    ) {
        try {
            installer.verify(context, destination, update)
        } catch (error: Exception) {
            destination.delete()
            WjxFileLogger.e(TAG, "download verification failed version=${update.version}", error)
            throw error
        }
    }

    private companion object {
        const val TAG = "UpdateDownloader"
        const val POLL_INTERVAL_MS = 500L
    }
}
