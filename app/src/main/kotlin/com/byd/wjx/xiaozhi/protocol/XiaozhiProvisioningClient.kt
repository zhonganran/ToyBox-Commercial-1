package com.byd.wjx.xiaozhi.protocol

import android.content.Context
import android.os.Build
import com.byd.wjx.BuildConfig
import com.byd.wjx.xiaozhi.domain.XiaozhiConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class XiaozhiProvisioningResult(
    val websocketUrl: String?,
    val token: String?,
    val activationCode: String?,
    val activationMessage: String?,
)

@Singleton
class XiaozhiProvisioningClient @Inject constructor(
    @ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val appContext = context.applicationContext

    suspend fun fetch(config: XiaozhiConfig): Result<XiaozhiProvisioningResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(config.otaUrl)
                .header("Activation-Version", "1")
                .header("Device-Id", config.deviceId)
                .header("Client-Id", config.clientId)
                .header("User-Agent", "super-toy-box/${BuildConfig.VERSION_NAME}")
                .header("Accept-Language", "zh-CN")
                .post(systemInfo(config).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("官方配置请求失败（HTTP ${response.code}）")
                }
                val root = JSONObject(body)
                val websocket = root.optJSONObject("websocket")
                val activation = root.optJSONObject("activation")
                XiaozhiProvisioningResult(
                    websocketUrl = websocket?.optString("url")?.takeIf { it.isNotBlank() },
                    token = websocket?.optString("token")?.takeIf { it.isNotBlank() },
                    activationCode = activation?.opt("code")?.toString()?.takeIf { it.isNotBlank() },
                    activationMessage = activation?.optString("message")?.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    private fun systemInfo(config: XiaozhiConfig): JSONObject {
        val metrics = appContext.resources.displayMetrics
        return JSONObject()
            .put("version", 2)
            .put("language", "zh-CN")
            .put("flash_size", 0)
            .put("minimum_free_heap_size", "0")
            .put("mac_address", config.deviceId)
            .put("uuid", config.clientId)
            .put("chip_model_name", Build.HARDWARE.ifBlank { "android" })
            .put(
                "chip_info",
                JSONObject()
                    .put("model", 0)
                    .put("cores", Runtime.getRuntime().availableProcessors())
                    .put("revision", 0)
                    .put("features", 0),
            )
            .put(
                "application",
                JSONObject()
                    .put("name", "super-toy-box")
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("compile_time", "android")
                    .put("idf_version", "android-${Build.VERSION.SDK_INT}")
                    .put("elf_sha256", ""),
            )
            .put("partition_table", JSONArray())
            .put("ota", JSONObject().put("label", "android"))
            .put(
                "display",
                JSONObject()
                    .put("monochrome", false)
                    .put("width", metrics.widthPixels)
                    .put("height", metrics.heightPixels),
            )
            .put(
                "board",
                JSONObject()
                    .put("type", "android-dilink")
                    .put("name", "super-toy-box"),
            )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
