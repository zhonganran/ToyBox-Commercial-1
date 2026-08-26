package com.byd.wjx.service

internal fun isValidReleaseVersion(value: String): Boolean =
    value.length in 3..64 && RELEASE_VERSION.matches(value)

internal fun normalizedSha256OrNull(value: String): String? =
    value.takeIf(SHA256::matches)?.lowercase()

private val RELEASE_VERSION = Regex(
    "[0-9]+(?:\\.[0-9]+){1,3}(?:[-+][0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?",
)

private val SHA256 = Regex("[0-9A-Fa-f]{64}")
