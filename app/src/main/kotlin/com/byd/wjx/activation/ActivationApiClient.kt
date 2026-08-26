package com.byd.wjx.activation

import android.content.Context
import com.byd.wjx.BuildConfig
import com.byd.wjx.log.WjxFileLogger
import java.time.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal data class ActivationPaymentCheckResult(
    val status: String,
    val paid: Boolean,
    val message: String,
    val response: JSONObject,
)

internal class ActivationApiClient(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val deviceTokenProvider: () -> String,
    private val deviceProofProvider: () -> JSONObject?,
    apiBaseUrl: String = BuildConfig.ACTIVATION_API_URL,
) {
    private val appContext = context.applicationContext
    private val apiBaseUrl = apiBaseUrl.trim().trimEnd('/')
    fun requestStatus(deviceCode: String): JSONObject =
        postJson("status", basePayload(deviceCode))

    fun requestActivation(
        deviceCode: String,
        activationCode: String,
    ): JSONObject = postJson(
        "activate",
        basePayload(deviceCode).apply {
            put("activationCode", activationCode)
        },
    )

    fun createPayment(
        deviceCode: String,
        paymentType: String,
    ): ActivationPaymentOrder {
        val response = postJson(
            "payment/create",
            basePayload(deviceCode).apply {
                put("paymentType", paymentType)
            },
        )
        val order = ActivationPaymentOrder(
            orderNo = response.optString("orderNo"),
            paymentType = response.optString("paymentType", paymentType),
            paymentName = response.optString(
                "paymentName",
                if (paymentType == "alipay") "支付宝" else "微信支付",
            ),
            payType = response.optString("payType"),
            checkoutMode = response.optString("checkoutMode"),
            payInfo = response.optString("payInfo"),
            payUrl = response.optString("payUrl", response.optString("payInfo")),
            amount = response.optString("amount", "15.90"),
            pollToken = response.optString("pollToken"),
            expiresAt = activationJsonMillis(response, "expiresAt"),
        )
        require(order.orderNo.isNotBlank() && order.pollToken.isNotBlank() && order.payUrl.isNotBlank()) {
            "支付服务器返回的订单信息不完整"
        }
        require(ActivationPaymentCheckoutPolicy.resolve(order) !is ActivationPaymentCheckoutTarget.Rejected) {
            "支付服务器返回了不安全的支付地址"
        }
        WjxFileLogger.i(TAG, "payment order created type=$paymentType payType=${order.payType}")
        return order
    }

    fun checkPayment(
        deviceCode: String,
        order: ActivationPaymentOrder,
    ): ActivationPaymentCheckResult {
        val response = postJson(
            "payment/status",
            basePayload(deviceCode).apply {
                put("orderNo", order.orderNo)
                put("pollToken", order.pollToken)
            },
        )
        val status = response.optString("status", "pending")
        val paid = response.optBoolean("paid", false)
        val message = response.optString(
            "message",
            when (status) {
                "paid" -> "支付成功，永久授权已自动激活"
                "expired" -> "支付二维码已过期，请重新创建订单"
                "failed" -> "支付订单失败，请重新尝试"
                else -> "等待扫码支付"
            },
        )
        WjxFileLogger.i(TAG, "payment status status=$status paid=$paid")
        return ActivationPaymentCheckResult(status, paid, message, response)
    }

    private fun basePayload(deviceCode: String): JSONObject = JSONObject().apply {
        put("deviceCode", ActivationDeviceIdentityProvider.normalizeDeviceCode(deviceCode))
        put("rawDeviceCode", deviceCode)
        put("appVersion", BuildConfig.VERSION_NAME)
        put("packageName", appContext.packageName)
        put("credentialVersion", DEVICE_CREDENTIAL_VERSION)
        deviceProofProvider()?.let { put("deviceProof", it) }
    }

    private fun postJson(path: String, payload: JSONObject): JSONObject {
        val requestBuilder = Request.Builder()
            .url("$apiBaseUrl/$path")
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
        deviceTokenProvider().takeIf(String::isNotBlank)?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }
        val request = requestBuilder.build()
        WjxFileLogger.i(
            TAG,
            "request path=$path url=$apiBaseUrl/$path deviceLength=${payload.optString("deviceCode").length}",
        )
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val message = json.optString("message", "HTTP ${response.code}")
                WjxFileLogger.w(TAG, "request failed path=$path code=${response.code} message=$message")
                throw IllegalStateException(message)
            }
            WjxFileLogger.i(
                TAG,
                "request success path=$path code=${response.code} activated=${json.optBoolean("activated", false)}",
            )
            return json
        }
    }

    companion object {
        private const val TAG = "ActivationApiClient"
        private const val DEVICE_CREDENTIAL_VERSION = 1
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun activationJsonMillis(json: JSONObject, key: String): Long {
    if (!json.has(key) || json.isNull(key)) return 0L
    val numeric = json.optLong(key, 0L)
    if (numeric > 0L) return numeric
    val text = json.optString(key, "")
    if (text.isBlank()) return 0L
    return runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
}
