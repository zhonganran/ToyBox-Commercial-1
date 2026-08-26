package com.byd.wjx.data.automation

import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class AutomationHttpActionHandler(
    private val httpClient: OkHttpClient,
) {
    suspend fun send(action: ActionDef): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
            ?: return DispatchResult(false, "未设置 HTTP 请求")
        val url = payload.optString("url").trim()
        if (url.isBlank()) return DispatchResult(false, "HTTP 地址为空")
        val method = payload.optString("method", "GET").uppercase()
        val bodyText = payload.optString("body", "")
        val contentType = payload.optString("contentType", "application/json")
        val builder = Request.Builder().url(url)
        payload.optJSONObject("headers")?.let { headers ->
            headers.keys().forEach { name -> builder.header(name, headers.optString(name)) }
        }
        val requestBody = bodyText.toRequestBody(contentType.toMediaTypeOrNull())
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> if (bodyText.isBlank()) builder.delete() else builder.delete(requestBody)
            "POST" -> builder.post(requestBody)
            "PUT" -> builder.put(requestBody)
            "PATCH" -> builder.patch(requestBody)
            else -> return DispatchResult(false, "不支持的 HTTP 方法：$method")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(builder.build()).execute().use { response ->
                    if (response.isSuccessful) DispatchResult(true)
                    else DispatchResult(false, "HTTP ${response.code}")
                }
            }.getOrElse { error ->
                WjxFileLogger.w(TAG, "http action failed: ${error.message}", error)
                DispatchResult(false, error.message ?: "HTTP 请求失败")
            }
        }
    }

    companion object {
        private const val TAG = "ActionDispatcher"
    }
}
