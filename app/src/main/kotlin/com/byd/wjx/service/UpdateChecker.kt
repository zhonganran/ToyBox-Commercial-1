package com.byd.wjx.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.byd.wjx.BuildConfig
import com.byd.wjx.R
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    private val httpClient: OkHttpClient
) {
    private val apkVerifier = UpdateApkVerifier()
    companion object {
        private const val TAG = "UpdateChecker"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"
        private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes (protects only against repeated launches within one session)
        private const val SHA256_HEX_LENGTH = 64

        fun isAutoCheckEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_CHECK, false)

        fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        }
    }

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val forceUpdate: Boolean = false,
        val versionCode: Long = 0L,
        val sha256: String = "",
        val packageName: String = "",
        val certificateSha256: String = "",
        val apkSize: Long = 0L,
    )

    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        val now = System.currentTimeMillis()

        if (!forceCheck && now - lastCheck < CHECK_INTERVAL_MS) {
            WjxFileLogger.d(TAG, "check skipped interval force=$forceCheck")
            return@withContext null
        }

        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        WjxFileLogger.i(TAG, "check start force=$forceCheck local=${getAppVersion(context)}/${getAppVersionCode(context)} server=${BuildConfig.UPDATE_API_URL}")

        if (BuildConfig.UPDATE_API_URL.isBlank()) {
            throw IllegalStateException("更新源未配置")
        }
        return@withContext checkServerUpdate(context)
    }

    private fun checkServerUpdate(context: Context): UpdateInfo? {
        val apiUrl = BuildConfig.UPDATE_API_URL.trim()
        if (apiUrl.isEmpty()) return null
        WjxFileLogger.i(TAG, "server update request url=$apiUrl")

        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "SuperToyBox-UpdateCheck")
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("更新接口错误：${response.code}")
            }
            response.body?.string() ?: throw Exception("更新后台返回空响应")
        }

        val json = JSONObject(body)
        if (!json.optBoolean("enabled", true)) {
            WjxFileLogger.i(TAG, "server update disabled")
            return null
        }

        val remoteVersionCode = json.optInt("versionCode", 0).toLong()
        val localVersionCode = getAppVersionCode(context)
        val remoteVersion = json.optString("version", "").removePrefix("v")
        val currentVersion = getAppVersion(context)

        if (remoteVersion.isEmpty()) throw Exception("更新信息缺少版本号")
        val newerByCode = remoteVersionCode > 0 && localVersionCode > 0 && remoteVersionCode > localVersionCode
        val newerByName = isNewer(remoteVersion, currentVersion)
        if (!newerByCode && !newerByName) {
            WjxFileLogger.i(TAG, "server update none remote=$remoteVersion/$remoteVersionCode local=$currentVersion/$localVersionCode")
            return null
        }

        val apkUrl = json.optString("downloadUrl", "")
        if (apkUrl.isBlank()) throw Exception("版本 $remoteVersion 中没有安装包")
        val sha256 = normalizeSha256(json.optString("sha256", ""))
        val packageName = json.optString("packageName", "").trim()
        val certificateSha256 = normalizeSha256(json.optString("certificateSha256", ""))
        val apkSize = json.optLong("apkSize", 0L)
        if (remoteVersionCode <= 0L || sha256.length != SHA256_HEX_LENGTH ||
            certificateSha256.length != SHA256_HEX_LENGTH || apkSize <= 0L
        ) {
            throw Exception("版本 $remoteVersion 缺少安装包完整性信息")
        }
        if (packageName != context.packageName) {
            throw Exception("更新包名不匹配")
        }

        WjxFileLogger.i(TAG, "server update available remote=$remoteVersion/$remoteVersionCode force=${json.optBoolean("forceUpdate", false)}")
        return UpdateInfo(
            version = remoteVersion,
            downloadUrl = apkUrl,
            releaseNotes = json.optString("releaseNotes", ""),
            forceUpdate = json.optBoolean("forceUpdate", false),
            versionCode = remoteVersionCode,
            sha256 = sha256,
            packageName = packageName,
            certificateSha256 = certificateSha256,
            apkSize = apkSize,
        )
    }

    /**
     * Download APK and report progress via [onProgress] callback.
     * Calls [onProgress] with status strings like "下载中：45%".
     * When done, triggers the install dialog.
     */
    suspend fun downloadAndInstall(
        context: Context,
        update: UpdateInfo,
        onProgress: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        WjxFileLogger.i(TAG, "download start version=${update.version} url=${update.downloadUrl}")

        // Delete old file if exists
        val updateDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("更新目录不可用")
        if (!updateDirectory.isDirectory && !updateDirectory.mkdirs()) {
            throw IllegalStateException("无法创建更新目录")
        }
        val destFile = File(updateDirectory, "超级玩具箱-${update.version}.apk")
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("超级玩具箱 ${update.version}")
            .setDescription(context.getString(R.string.update_download_notification_description))
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        val downloadId = downloadManager.enqueue(request)
        onProgress(context.getString(R.string.update_downloading_start))

        // Poll download progress
        var finished = false
        while (!finished) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            // .use{} closes the cursor on every path — the old code only closed it inside the
            // moveToFirst() block, leaking one cursor per poll when the row was momentarily absent.
            val rowPresent = downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )
                when (status) {
                    DownloadManager.STATUS_RUNNING -> {
                        val total = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        val downloaded = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            onProgress(context.getString(R.string.update_downloading_progress, pct))
                        }
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        finished = true
                        onProgress(context.getString(R.string.update_downloading_done))
                        WjxFileLogger.i(TAG, "download success version=${update.version} file=${destFile.absolutePath}")
                    }
                    DownloadManager.STATUS_FAILED -> {
                        finished = true
                        val reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        )
                        WjxFileLogger.w(TAG, "download failed version=${update.version} reason=$reason")
                        destFile.delete()
                        throw Exception(context.getString(R.string.update_download_error, reason))
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        onProgress(context.getString(R.string.update_downloading_paused))
                    }
                }
                true
            } ?: false
            // Download row vanished (query null or cleared by the user): stop instead of spinning
            // forever every 500 ms with no row that could ever flip `finished`.
            if (!rowPresent) {
                WjxFileLogger.w(TAG, "download row missing version=${update.version} id=$downloadId")
                destFile.delete()
                throw Exception(context.getString(R.string.update_download_error, -1))
            }
            if (!finished) {
                kotlinx.coroutines.delay(500)
            }
        }

        try {
            apkVerifier.verify(context, destFile, update)
        } catch (error: Exception) {
            destFile.delete()
            WjxFileLogger.e(TAG, "download verification failed version=${update.version}", error)
            throw error
        }

        withContext(Dispatchers.Main) {
            installApk(context, destFile, update.version)
        }
    }

    private fun installApk(context: Context, file: File, version: String) {
        if (!file.exists()) {
            WjxFileLogger.w(TAG, "install skipped: apk missing version=$version path=${file.absolutePath}")
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        WjxFileLogger.i(TAG, "install intent start version=$version path=${file.absolutePath} size=${file.length()}")
        context.startActivity(intent)
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "getAppVersion failed: ${e.message}")
            "0.0.0"
        }
    }

    private fun getAppVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.longVersionCode
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "getAppVersionCode failed: ${e.message}")
            0L
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val l = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    private fun normalizeSha256(value: String): String = value
        .trim()
        .lowercase()
        .filter { it in '0'..'9' || it in 'a'..'f' }

}
