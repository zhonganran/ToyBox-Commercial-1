package com.byd.wjx.ui.settings

import android.content.Context
import com.byd.wjx.BuildConfig
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.WjxDirectories
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

data class SettingsLogUploadResult(
    val id: Long,
    val message: String,
)

@Singleton
class SettingsLogUploader @Inject constructor(
    @ApplicationContext context: Context,
    private val activationManager: ActivationManager,
    private val okHttpClient: OkHttpClient,
) {
    private val appContext = context.applicationContext

    suspend fun upload(): SettingsLogUploadResult = withContext(Dispatchers.IO) {
        val apiUrl = BuildConfig.LOG_UPLOAD_API_URL.trim()
        require(apiUrl.isNotBlank()) { "日志上传地址未配置" }

        var activation = activationManager.currentSnapshot()
        if (activation.deviceCode.isBlank() || activationManager.currentDeviceToken().isBlank()) {
            activation = activationManager.refreshIfNeeded(forceAfterMs = 0L)
        }
        val deviceCode = activation.deviceCode.trim()
        require(deviceCode.isNotBlank()) { "未读取到车架号，请先启动车辆并刷新授权状态" }
        val deviceToken = activationManager.currentDeviceToken()
        require(deviceToken.isNotBlank()) { "设备凭证无效，请刷新授权或重新输入激活码" }

        WjxFileLogger.i(TAG, "diagnostic log upload requested version=${BuildConfig.VERSION_NAME}")
        WjxFileLogger.flush()
        val source = WjxDirectories.appLog
        require(source.isFile && source.length() > 0L) { "当前没有可上传的日志" }

        val snapshot = File.createTempFile("wjx-diagnostic-", ".log", appContext.cacheDir)
        try {
            copyRawLogSnapshot(source, snapshot, WjxFileLogger.MAX_LOG_BYTES)
            require(snapshot.length() in 1..WjxFileLogger.MAX_LOG_BYTES) { "日志文件大小无效" }

            val rawBytes = "application/octet-stream".toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("deviceCode", deviceCode)
                .addFormDataPart("appVersion", BuildConfig.VERSION_NAME)
                .addFormDataPart("packageName", BuildConfig.APPLICATION_ID)
                .addFormDataPart("log", "wjx.log", snapshot.asRequestBody(rawBytes))
                .build()
            val request = Request.Builder()
                .url(apiUrl)
                .post(body)
                .header("Authorization", "Bearer $deviceToken")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(responseText) }.getOrNull()
                val message = json?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "服务器返回 HTTP ${response.code}"
                if (!response.isSuccessful || json?.optBoolean("success", false) != true) {
                    error(message)
                }
                SettingsLogUploadResult(
                    id = json.optLong("logId", 0L),
                    message = message,
                )
            }
        } finally {
            snapshot.delete()
        }
    }

    private companion object {
        const val TAG = "SettingsLogUploader"
    }
}

internal fun copyRawLogSnapshot(
    source: File,
    target: File,
    maxBytes: Long,
) {
    require(source.isFile && source.length() > 0L) { "日志文件不存在或为空" }
    require(maxBytes > 0L) { "日志上传大小限制无效" }

    var copied = 0L
    try {
        source.inputStream().buffered().use { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    require(copied <= maxBytes) { "日志文件超过 ${maxBytes / 1024 / 1024} MB" }
                    output.write(buffer, 0, read)
                }
            }
        }
    } catch (error: Throwable) {
        target.delete()
        throw error
    }
    require(copied > 0L) { "日志文件不存在或为空" }
}
