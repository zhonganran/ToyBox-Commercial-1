package com.byd.wjx.lyrics

import java.util.Locale
import kotlin.math.abs

/** Normalizes music metadata and decides whether two snapshots represent the same track. */
object MusicIdentity {
    private val artistSplit = Regex(
        "[,，、/／&＆;；]|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+and\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val whitespace = Regex("\\s+")
    private val placeholderTitles = setOf(
        "无歌曲",
        "无标题",
        "未知歌曲",
        "音乐 正在播放",
        "音乐正在播放",
        "“音乐”正在运行",
        "\"音乐\"正在运行",
        "null",
        "(null)",
        "<null>",
        "none",
        "unknown",
        "unknown title",
        "mediacenter",
        "酷我音乐",
        "酷我音乐(remote)",
    )

    fun songKey(title: String, artist: String): String =
        listOf(normalizeTitle(title), splitArtists(artist).firstOrNull().orEmpty())
            .joinToString("\u0000")

    fun trackKey(
        title: String,
        artist: String,
        durationMs: Long,
        mediaUri: String?,
    ): String {
        val durationKey = durationMs.takeIf { it > 0L }?.div(1_000L)?.toString().orEmpty()
        val uriKey = normalizeMediaUri(mediaUri)?.hashCode()?.toString().orEmpty()
        return listOf(songKey(title, artist), durationKey, uriKey).joinToString("\u0000")
    }

    fun hasUsableTitle(title: String): Boolean {
        val normalized = normalizeTitle(title)
        return normalized.isNotBlank() && normalized !in placeholderTitles
    }

    fun isSameSong(
        previousTitle: String,
        previousArtist: String,
        nextTitle: String,
        nextArtist: String,
    ): Boolean {
        val oldTitle = normalizeTitle(previousTitle)
        val newTitle = normalizeTitle(nextTitle)
        if (oldTitle.isBlank() || newTitle.isBlank() || oldTitle != newTitle) return false
        val oldArtists = splitArtists(previousArtist)
        val newArtists = splitArtists(nextArtist)
        if (oldArtists.isEmpty() || newArtists.isEmpty()) return true
        return oldArtists.any { it in newArtists }
    }

    fun isSameTrack(
        previousTitle: String,
        previousArtist: String,
        nextTitle: String,
        nextArtist: String,
        previousDurationMs: Long,
        nextDurationMs: Long,
        previousMediaUri: String?,
        nextMediaUri: String?,
        previousPackageName: String,
        nextPackageName: String,
    ): Boolean {
        if (!isSameSong(previousTitle, previousArtist, nextTitle, nextArtist)) return false
        val previousUri = normalizeMediaUri(previousMediaUri)
        val nextUri = normalizeMediaUri(nextMediaUri)
        if (previousUri != null && nextUri != null && previousUri != nextUri) return false
        if (
            previousDurationMs > 0L &&
            nextDurationMs > 0L &&
            abs(previousDurationMs - nextDurationMs) > DURATION_IDENTITY_TOLERANCE_MS
        ) {
            return false
        }
        return true
    }

    internal fun normalizedTitle(value: String): String = normalizeTitle(value)

    internal fun normalizedArtists(value: String): List<String> = splitArtists(value)

    private fun normalizeTitle(value: String): String =
        whitespace.replace(value.trim(), " ").lowercase(Locale.ROOT)

    private fun splitArtists(value: String): List<String> =
        artistSplit.split(value)
            .map { normalizeTitle(it) }
            .filter { it.isNotBlank() }

    private fun normalizeMediaUri(value: String?): String? =
        value?.trim()?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)

    private const val DURATION_IDENTITY_TOLERANCE_MS = 3_000L
}
