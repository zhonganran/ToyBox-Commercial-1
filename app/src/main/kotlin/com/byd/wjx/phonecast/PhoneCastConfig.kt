package com.byd.wjx.phonecast

import java.net.URI

enum class PhoneCastScaleMode(
    val storedValue: String,
    val label: String,
) {
    FIT("fit", "完整"),
    CROP("crop", "铺满"),
    STRETCH("stretch", "拉伸");

    companion object {
        fun fromStored(value: String?): PhoneCastScaleMode =
            entries.firstOrNull { it.storedValue == value } ?: CROP
    }
}

object PhoneCastUrl {
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val candidate = if (SCHEME_PATTERN.containsMatchIn(trimmed)) trimmed else "rtsp://$trimmed"
        val uri = runCatching { URI(candidate).parseServerAuthority() }.getOrNull() ?: return null
        if (!uri.scheme.equals("rtsp", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.port !in -1..65535) return null
        if (uri.rawFragment != null) return null
        return buildString {
            append("rtsp://")
            append(uri.rawAuthority)
            append(uri.rawPath.orEmpty().ifEmpty { "/" })
            uri.rawQuery?.let { append('?').append(it) }
        }
    }

    fun endpointLabel(input: String): String {
        val uri = normalize(input)?.let { runCatching { URI(it) }.getOrNull() }
        val host = uri?.host ?: return "手机投屏"
        val port = uri.port
        return if (port >= 0) "$host:$port" else host
    }

    private val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
}
