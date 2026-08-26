package com.byd.wjx.service

import android.content.Context
import com.byd.wjx.BuildConfig
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.security.EndpointPrivacy
import com.byd.wjx.security.isSameDeploymentOrigin
import com.byd.wjx.security.isSecureDeploymentEndpoint
import com.byd.wjx.security.readBoundedUtf8Body
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    private val httpClient: OkHttpClient,
    private val downloader: UpdateDownloader = UpdateDownloader(),
) {
    enum class InstallOutcome {
        INSTALLER_STARTED,
        PERMISSION_REQUIRED,
    }

    companion object {
        private const val TAG = "UpdateChecker"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"
        private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes (protects only against repeated launches within one session)
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
        WjxFileLogger.i(
            TAG,
            "check start force=$forceCheck local=${getAppVersion(context)}/${getAppVersionCode(context)}",
        )

        if (BuildConfig.UPDATE_API_URL.isBlank()) {
            throw IllegalStateException("更新源未配置")
        }
        return@withContext checkServerUpdate(context)
    }

    private fun checkServerUpdate(context: Context): UpdateInfo? {
        val apiUrl = BuildConfig.UPDATE_API_URL.trim()
        if (apiUrl.isEmpty()) return null
        if (!isSecureDeploymentEndpoint(apiUrl)) {
            throw IllegalStateException("更新源配置不安全")
        }
        WjxFileLogger.i(TAG, "server update request")

        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "SuperToyBox-UpdateCheck")
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("更新接口错误：${response.code}")
            }
            if (!isSameDeploymentOrigin(response.request.url.toString(), apiUrl)) {
                throw Exception("更新源重定向不安全")
            }
            readBoundedUtf8Body(response.body)
        }

        val json = JSONObject(body)
        if (!json.optBoolean("enabled", true)) {
            WjxFileLogger.i(TAG, "server update disabled")
            return null
        }

        val remoteVersionCode = json.optLong("versionCode", 0L)
        val localVersionCode = getAppVersionCode(context)
        val remoteVersion = json.optString("version", "").removePrefix("v")
        val currentVersion = getAppVersion(context)

        if (!isValidReleaseVersion(remoteVersion)) throw Exception("更新版本号格式无效")
        if (remoteVersionCode <= 0L) throw Exception("更新版本码无效")
        if (remoteVersionCode <= localVersionCode) {
            WjxFileLogger.i(TAG, "server update none remote=$remoteVersion/$remoteVersionCode local=$currentVersion/$localVersionCode")
            return null
        }

        val apkUrl = json.optString("downloadUrl", "")
        if (apkUrl.isBlank()) throw Exception("版本 $remoteVersion 中没有安装包")
        if (!isSameDeploymentOrigin(apkUrl, apiUrl)) {
            throw Exception("安装包下载地址不安全")
        }
        val sha256 = normalizedSha256OrNull(json.optString("sha256", ""))
        val packageName = json.optString("packageName", "").trim()
        val certificateSha256 = normalizedSha256OrNull(json.optString("certificateSha256", ""))
        val apkSize = json.optLong("apkSize", 0L)
        if (sha256 == null || certificateSha256 == null || apkSize <= 0L) {
            throw Exception("版本 $remoteVersion 缺少安装包完整性信息")
        }
        if (packageName != context.packageName) {
            throw Exception("更新包名不匹配")
        }

        WjxFileLogger.i(TAG, "server update available remote=$remoteVersion/$remoteVersionCode force=${json.optBoolean("forceUpdate", false)}")
        return UpdateInfo(
            version = remoteVersion,
            downloadUrl = apkUrl,
            releaseNotes = EndpointPrivacy.redact(json.optString("releaseNotes", "")),
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
    ): InstallOutcome = downloader.downloadAndInstall(context, update, onProgress)

    /** Opens the pending permission page again, or installs immediately if permission is now ready. */
    suspend fun continuePendingInstall(context: Context): Boolean =
        downloader.continuePendingInstall(context)

    /** Continues a verified update after returning from Android's unknown-source settings. */
    suspend fun resumePendingInstall(context: Context): Boolean =
        downloader.resumePendingInstall(context)

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

}
