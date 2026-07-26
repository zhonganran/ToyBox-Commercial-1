package com.byd.wjx.vehicle

import com.byd.wjx.BuildConfig
import com.byd.wjx.activation.ActivationManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class MiniappBindingCode(
    val code: String,
    val expiresAt: Long,
)

@Singleton
class MiniappBindingCodeManager @Inject constructor(
    private val activationManager: ActivationManager,
    private val okHttpClient: OkHttpClient,
) {
    suspend fun generate(): MiniappBindingCode = withContext(Dispatchers.IO) {
        var activation = activationManager.currentSnapshot()
        var deviceToken = activationManager.currentDeviceToken()
        if (activation.deviceCode.isBlank() || deviceToken.isBlank()) {
            activation = activationManager.refreshIfNeeded(forceAfterMs = 0L)
            deviceToken = activationManager.currentDeviceToken()
        }
        val vin = activation.deviceCode.trim()
        require(vin.isNotBlank()) { "尚未读取到车架号，请先启动车辆并刷新授权状态" }
        require(deviceToken.isNotBlank()) { "设备凭证无效，请刷新授权或重新输入激活码" }

        val payload = JSONObject().put("deviceCode", vin)
        val request = Request.Builder()
            .url(BuildConfig.MINIAPP_BINDING_CODE_API_URL)
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $deviceToken")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || !json.optBoolean("success", false)) {
                error(json.optString("message", "服务器返回 HTTP ${response.code}"))
            }
            val code = json.optString("bindingCode").trim()
            require(code.length in 6..8) { "服务器未返回有效绑定码" }
            MiniappBindingCode(
                code = code,
                expiresAt = json.optLong("expiresAt", 0L),
            )
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
