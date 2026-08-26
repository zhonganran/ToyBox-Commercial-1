package com.byd.wjx.ui.settings

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun isAllowedServiceQrMetadataUrl(url: String, originUrl: String? = null): Boolean {
    val parsed = url.toHttpUrlOrNull() ?: return false
    if (!parsed.isHttps || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
    val origin = originUrl?.toHttpUrlOrNull() ?: return originUrl == null
    return parsed.host == origin.host && parsed.port == origin.port
}

internal fun isAllowedServiceQrImageUrl(imageUrl: String, metadataUrl: String): Boolean {
    val image = imageUrl.toHttpUrlOrNull() ?: return false
    val metadata = metadataUrl.toHttpUrlOrNull() ?: return false
    return image.isHttps &&
        image.username.isEmpty() &&
        image.password.isEmpty() &&
        image.host == metadata.host &&
        image.port == metadata.port
}

internal fun serviceQrImageCacheName(key: String, imageUrl: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$key\n$imageUrl".toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) } + ".img"
}

internal fun readLimitedBytes(input: InputStream, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_READ_BUFFER_BYTES))
    val buffer = ByteArray(DEFAULT_READ_BUFFER_BYTES)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "response exceeds $maxBytes bytes" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun requireValidServiceQrImage(bytes: ByteArray) {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth in 1..SERVICE_QR_IMAGE_MAX_DIMENSION) { "invalid image width" }
    require(options.outHeight in 1..SERVICE_QR_IMAGE_MAX_DIMENSION) { "invalid image height" }
    require(options.outWidth.toLong() * options.outHeight <= SERVICE_QR_IMAGE_MAX_PIXELS) {
        "image dimensions too large"
    }
}

internal const val SERVICE_QR_METADATA_MAX_BYTES = 256 * 1024
internal const val SERVICE_QR_IMAGE_MAX_BYTES = 2 * 1024 * 1024
internal const val SERVICE_QR_IMAGE_MAX_DIMENSION = 2_048
internal const val SERVICE_QR_IMAGE_MAX_PIXELS = 4_000_000L
private const val DEFAULT_READ_BUFFER_BYTES = 8 * 1024
