package com.byd.wjx.ui.settings

import android.content.Context
import com.byd.wjx.BuildConfig
import com.byd.wjx.data.settings.ServiceQrCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Loads commercial service QR metadata while keeping network and cache details out of the UI. */
@Singleton
class ServiceQrRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
    private val cache: ServiceQrCache,
) {
    private val imageCacheDir = File(context.cacheDir, "service_qrcodes")

    fun cached(): Map<String, ServiceQrCode> = parseServiceQrCodes(cache.readJson().orEmpty())

    suspend fun refresh(): Map<String, ServiceQrCode> = withContext(Dispatchers.IO) {
        require(isAllowedServiceQrMetadataUrl(BuildConfig.SERVICE_QR_API_URL)) {
            "service QR metadata URL must use HTTPS"
        }
        val request = Request.Builder()
            .url(BuildConfig.SERVICE_QR_API_URL)
            .get()
            .build()
        val body = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            require(
                isAllowedServiceQrMetadataUrl(
                    response.request.url.toString(),
                    BuildConfig.SERVICE_QR_API_URL,
                ),
            ) { "service QR metadata redirect is not allowed" }
            val responseBody = response.body ?: error("empty body")
            responseBody.contentLength().takeIf { it >= 0L }?.let { length ->
                require(length <= SERVICE_QR_METADATA_MAX_BYTES) { "metadata response too large" }
            }
            readLimitedBytes(responseBody.byteStream(), SERVICE_QR_METADATA_MAX_BYTES)
                .toString(Charsets.UTF_8)
                .ifBlank { error("empty body") }
        }
        require(isValidServiceQrPayload(body)) { "invalid service QR payload" }
        parseServiceQrCodes(body).also { cache.writeJson(body) }
    }

    suspend fun cachedImage(key: String, imageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            imageCacheFile(key, imageUrl)
                .takeIf { it.isFile && it.length() in 1..SERVICE_QR_IMAGE_MAX_BYTES.toLong() }
                ?.inputStream()
                ?.use { input -> readLimitedBytes(input, SERVICE_QR_IMAGE_MAX_BYTES) }
        }.getOrNull()
    }

    suspend fun refreshImage(key: String, imageUrl: String): ByteArray = withContext(Dispatchers.IO) {
        require(isAllowedServiceQrImageUrl(imageUrl, BuildConfig.SERVICE_QR_API_URL)) {
            "service QR image URL is not allowed"
        }
        val request = Request.Builder().url(imageUrl).get().build()
        val bytes = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            require(isAllowedServiceQrImageUrl(response.request.url.toString(), BuildConfig.SERVICE_QR_API_URL)) {
                "service QR image redirect is not allowed"
            }
            val body = response.body ?: error("empty image body")
            body.contentType()?.let { require(it.type == "image") { "response is not an image" } }
            body.contentLength().takeIf { it >= 0L }?.let { length ->
                require(length <= SERVICE_QR_IMAGE_MAX_BYTES) { "image response too large" }
            }
            readLimitedBytes(body.byteStream(), SERVICE_QR_IMAGE_MAX_BYTES)
                .also { require(it.isNotEmpty()) { "empty image body" } }
        }
        requireValidServiceQrImage(bytes)
        writeImageCache(imageCacheFile(key, imageUrl), bytes)
        bytes
    }

    private fun imageCacheFile(key: String, imageUrl: String): File =
        File(imageCacheDir, serviceQrImageCacheName(key, imageUrl))

    private fun writeImageCache(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File.createTempFile(target.name, ".tmp", target.parentFile)
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }
}

internal fun parseServiceQrCodes(json: String): Map<String, ServiceQrCode> {
    if (json.isBlank()) return emptyMap()
    return runCatching {
        val items = JSONObject(json).optJSONArray("items") ?: return@runCatching emptyMap()
        buildMap {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val key = item.optString("key").trim()
                if (key.isBlank() || !item.optBoolean("enabled", true)) continue
                put(
                    key,
                    ServiceQrCode(
                        key = key,
                        title = item.optString("title").ifBlank { key },
                        subtitle = item.optString("subtitle"),
                        description = item.optString("description"),
                        imageUrl = item.optString("imageUrl"),
                        expiresAt = item.optString("expiresAt"),
                        updatedAt = item.optString("updatedAt"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())
}

internal fun isValidServiceQrPayload(json: String): Boolean = runCatching {
    JSONObject(json).getJSONArray("items")
    true
}.getOrDefault(false)
