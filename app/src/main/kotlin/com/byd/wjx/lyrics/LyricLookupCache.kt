package com.byd.wjx.lyrics

internal class LyricLookupCache(
    private val currentTimeMillis: () -> Long,
) {
    private val found = linkedMapOf<String, LyricsResult>()
    private val notFoundUntil = linkedMapOf<String, Long>()

    @Synchronized
    fun found(key: String): LyricsResult? = found[key]

    @Synchronized
    fun isTemporarilyNotFound(key: String): Boolean {
        val expiresAt = notFoundUntil[key] ?: return false
        if (currentTimeMillis() < expiresAt) return true
        notFoundUntil.remove(key)
        return false
    }

    @Synchronized
    fun put(key: String, result: LyricsResult) {
        trimOldest(found, MAX_FOUND_CACHE_SIZE)
        found[key] = result
        notFoundUntil.remove(key)
    }

    @Synchronized
    fun rememberNotFound(key: String) {
        trimOldest(notFoundUntil, MAX_NOT_FOUND_CACHE_SIZE)
        notFoundUntil[key] = currentTimeMillis() + NOT_FOUND_CACHE_MS
    }

    private fun <T> trimOldest(values: LinkedHashMap<String, T>, limit: Int) {
        if (values.size >= limit) values.keys.firstOrNull()?.let(values::remove)
    }

    private companion object {
        const val MAX_FOUND_CACHE_SIZE = 24
        const val MAX_NOT_FOUND_CACHE_SIZE = 48
        const val NOT_FOUND_CACHE_MS = 5L * 60L * 1_000L
    }
}
