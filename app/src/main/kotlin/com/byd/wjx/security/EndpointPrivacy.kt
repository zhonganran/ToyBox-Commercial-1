package com.byd.wjx.security

import com.byd.wjx.BuildConfig
import java.net.URI

/** Removes deployment endpoints from user-visible errors and diagnostic output. */
object EndpointPrivacy {
    const val HIDDEN_ENDPOINT = "[服务器地址已隐藏]"

    private val protectedDomainPatterns: List<Regex> by lazy {
        listOf(
            BuildConfig.UPDATE_API_URL,
            BuildConfig.ACTIVATION_API_URL,
            BuildConfig.SERVICE_QR_API_URL,
            BuildConfig.LOG_UPLOAD_API_URL,
        ).mapNotNull(::hostFromUrl)
            .mapNotNull(::registrableRoot)
            .distinct()
            .map { root ->
                Regex(
                    "(?i)(?<![a-z0-9.-])(?:[a-z0-9-]+\\.)*${Regex.escape(root)}" +
                        "(?![a-z0-9.-])",
                )
            }
    }

    fun redact(value: String?): String {
        var result = value.orEmpty()
        if (result.isBlank()) return result
        result = URL_REGEX.replace(result, HIDDEN_ENDPOINT)
        result = BRACKETED_IPV6_REGEX.replace(result, HIDDEN_ENDPOINT)
        result = UNBRACKETED_IPV6_REGEX.replace(result, HIDDEN_ENDPOINT)
        result = IPV4_REGEX.replace(result, HIDDEN_ENDPOINT)
        protectedDomainPatterns.forEach { pattern ->
            result = pattern.replace(result, HIDDEN_ENDPOINT)
        }
        return result
    }

    fun userMessage(error: Throwable, fallback: String): String {
        val redacted = redact(error.message).trim()
        return redacted.takeUnless { it.isBlank() || it == HIDDEN_ENDPOINT } ?: fallback
    }

    private fun hostFromUrl(value: String): String? = runCatching {
        URI(value.trim()).host?.trim('.')?.lowercase()
    }.getOrNull()?.takeIf(String::isNotBlank)

    private fun registrableRoot(host: String): String? {
        if (IPV4_REGEX.matches(host)) return null
        val labels = host.split('.').filter(String::isNotBlank)
        if (labels.size < 2) return null
        val secondLevelSuffix = labels.last().length == 2 &&
            labels[labels.lastIndex - 1] in COMMON_SECOND_LEVEL_SUFFIXES
        val count = if (secondLevelSuffix && labels.size >= 3) 3 else 2
        return labels.takeLast(count).joinToString(".")
    }

    private val URL_REGEX = Regex("(?i)\\b(?:https?|wss?)://[^\\s<>\"']+")
    private val BRACKETED_IPV6_REGEX = Regex("(?i)\\[[0-9a-f:]+](?::\\d{1,5})?")
    private val UNBRACKETED_IPV6_REGEX = Regex(
        "(?i)(?<![0-9a-f:])(?=(?:[0-9a-f]*:){3})[0-9a-f:]+(?:%[0-9a-z._-]+)?" +
            "(?![0-9a-f:])",
    )
    private val IPV4_REGEX = Regex(
        "(?<![\\d.])(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)" +
            "(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}" +
            "(?::\\d{1,5})?(?![\\d.])",
    )
    private val COMMON_SECOND_LEVEL_SUFFIXES = setOf("ac", "co", "com", "edu", "gov", "net", "org")
}
