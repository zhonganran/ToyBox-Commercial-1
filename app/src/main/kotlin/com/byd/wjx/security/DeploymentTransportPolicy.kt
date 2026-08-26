package com.byd.wjx.security

import java.io.ByteArrayOutputStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody

internal const val DEPLOYMENT_API_MAX_RESPONSE_BYTES = 256 * 1024

internal fun isSecureDeploymentEndpoint(url: String): Boolean =
    secureHttpUrl(url) != null

internal fun isSameDeploymentOrigin(candidateUrl: String, configuredUrl: String): Boolean {
    val candidate = secureHttpUrl(candidateUrl) ?: return false
    val configured = secureHttpUrl(configuredUrl) ?: return false
    return candidate.host == configured.host && candidate.port == configured.port
}

internal fun readBoundedUtf8Body(
    body: ResponseBody?,
    maxBytes: Int = DEPLOYMENT_API_MAX_RESPONSE_BYTES,
): String {
    val responseBody = body ?: throw IllegalStateException("服务器返回空响应")
    require(maxBytes > 0) { "响应大小限制无效" }
    responseBody.contentLength().takeIf { it >= 0L }?.let { length ->
        if (length > maxBytes) throw IllegalStateException("服务器响应过大")
    }
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_BYTES))
    val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
    var total = 0
    responseBody.byteStream().use { input ->
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw IllegalStateException("服务器响应过大")
            output.write(buffer, 0, count)
        }
    }
    return output.toString(Charsets.UTF_8.name())
}

private fun secureHttpUrl(url: String): HttpUrl? = url.trim().toHttpUrlOrNull()
    ?.takeIf { parsed ->
        parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty()
    }

private const val DEFAULT_BUFFER_BYTES = 8 * 1024
