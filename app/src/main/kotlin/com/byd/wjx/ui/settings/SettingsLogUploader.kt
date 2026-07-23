package com.byd.wjx.ui.settings

import android.content.Context
import com.byd.wjx.BuildConfig
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.WjxDirectories
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
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

        val activation = activationManager.currentSnapshot().takeIf { it.deviceCode.isNotBlank() }
            ?: activationManager.refreshIfNeeded(forceAfterMs = 0L)
        val deviceCode = activation.deviceCode.trim()
        require(deviceCode.isNotBlank()) { "未读取到车架号，请先启动车辆并刷新授权状态" }

        WjxFileLogger.i(TAG, "diagnostic log upload requested version=${BuildConfig.VERSION_NAME}")
        WjxFileLogger.flush()
        val source = WjxDirectories.appLog
        require(source.isFile && source.length() > 0L) { "当前没有可上传的日志" }

        val snapshot = File.createTempFile("wjx-diagnostic-", ".log", appContext.cacheDir)
        try {
            copyLatestLogSnapshot(source, snapshot, WjxFileLogger.MAX_LOG_BYTES)
            require(snapshot.length() in 1..WjxFileLogger.MAX_LOG_BYTES) { "日志文件大小无效" }

            val text = "text/plain; charset=utf-8".toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("deviceCode", deviceCode)
                .addFormDataPart("appVersion", BuildConfig.VERSION_NAME)
                .addFormDataPart("packageName", BuildConfig.APPLICATION_ID)
                .addFormDataPart("log", "wjx.log", snapshot.asRequestBody(text))
                .build()
            val request = Request.Builder()
                .url(apiUrl)
                .post(body)
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

internal fun copyLatestLogSnapshot(
    source: File,
    target: File,
    maxBytes: Long,
) {
    require(source.isFile && source.length() > 0L) { "日志文件不存在或为空" }
    require(maxBytes > 0L) { "日志上传大小限制无效" }

    val sourceLength = source.length()
    val bytesToCopy = minOf(sourceLength, maxBytes)
    RandomAccessFile(source, "r").use { input ->
        input.seek(sourceLength - bytesToCopy)
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = bytesToCopy
            while (remaining > 0L) {
                val requested = minOf(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, requested)
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
    }
}
