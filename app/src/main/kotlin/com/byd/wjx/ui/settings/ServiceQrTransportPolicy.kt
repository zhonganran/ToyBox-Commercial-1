package com.byd.wjx.ui.settings

import android.graphics.BitmapFactory
import com.byd.wjx.security.isSameDeploymentOrigin
import com.byd.wjx.security.isSecureDeploymentEndpoint
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

internal fun isAllowedServiceQrMetadataUrl(url: String, originUrl: String? = null): Boolean {
    return if (originUrl == null) {
        isSecureDeploymentEndpoint(url)
    } else {
        isSameDeploymentOrigin(url, originUrl)
    }
}

internal fun isAllowedServiceQrImageUrl(imageUrl: String, metadataUrl: String): Boolean {
    return isSameDeploymentOrigin(imageUrl, metadataUrl)
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
