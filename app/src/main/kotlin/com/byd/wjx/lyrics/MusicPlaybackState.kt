package com.byd.wjx.lyrics

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

data class MusicPlaybackSnapshot(
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playing: Boolean = false,
    val packageName: String = "",
    val mediaUri: String? = null,
    val metadataLyrics: String? = null,
    val timedLyrics: List<LyricLine>? = null,
    val realtimeLyrics: List<String> = emptyList(),
    val anchorElapsedMs: Long = SystemClock.elapsedRealtime(),
    val playbackSpeed: Float = 1f,
    val source: MusicPlaybackSource = MusicPlaybackSource.Unknown,
) {
    val hasSong: Boolean
        get() = MusicIdentity.hasUsableTitle(title)

    fun songKey(): String {
        val identity = MusicIdentity.trackKey(
            title = title,
            artist = artist,
            durationMs = durationMs,
            mediaUri = mediaUri,
        )
        val lyricRevision = metadataLyrics
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.hashCode()
            ?: timedLyrics?.takeIf { it.isNotEmpty() }?.hashCode()
        return "$identity\u0000${lyricRevision?.toString().orEmpty()}"
    }

    fun isSameSongAs(other: MusicPlaybackSnapshot): Boolean =
        MusicIdentity.isSameTrack(
            previousTitle = other.title,
            previousArtist = other.artist,
            nextTitle = title,
            nextArtist = artist,
            previousDurationMs = other.durationMs,
            nextDurationMs = durationMs,
            previousMediaUri = other.mediaUri,
            nextMediaUri = mediaUri,
            previousPackageName = other.packageName,
            nextPackageName = packageName,
        )

    fun projectedPositionMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        if (!playing || anchorElapsedMs <= 0L) return positionMs.coerceAtLeast(0L)
        val delta = ((nowElapsedMs - anchorElapsedMs).coerceAtLeast(0L) * playbackSpeed).roundToLong()
        return (positionMs + delta).coerceAtLeast(0L)
    }

    companion object {
        val EMPTY = MusicPlaybackSnapshot()
    }
}

enum class MusicPlaybackSource {
    Unknown,
    AdbFallback,
    Notification,
    MediaController;

    val isPrimary: Boolean
        get() = this == Notification || this == MediaController
}

object MusicIdentity {
    private val ARTIST_SPLIT = Regex("[,，、/／&＆;；]|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+and\\s+", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")
    private val PLACEHOLDER_TITLES = setOf(
        "无歌曲",
        "无标题",
        "未知歌曲",
        "音乐 正在播放",
        "音乐正在播放",
        "“音乐”正在运行",
        "\"音乐\"正在运行",
        "unknown",
        "unknown title",
    )

    fun songKey(title: String, artist: String): String =
        listOf(normalizeTitle(title), splitArtists(artist).firstOrNull().orEmpty()).joinToString("\u0000")

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
        return normalized.isNotBlank() && normalized !in PLACEHOLDER_TITLES
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
        if (previousPackageName.isNotBlank() && nextPackageName.isNotBlank() && previousPackageName != nextPackageName) {
            return false
        }
        val previousUri = normalizeMediaUri(previousMediaUri)
        val nextUri = normalizeMediaUri(nextMediaUri)
        if (previousUri != null && nextUri != null && previousUri != nextUri) return false
        if (previousDurationMs > 0L && nextDurationMs > 0L && abs(previousDurationMs - nextDurationMs) > DURATION_IDENTITY_TOLERANCE_MS) {
            return false
        }
        return true
    }

    internal fun normalizedTitle(value: String): String = normalizeTitle(value)

    internal fun normalizedArtists(value: String): List<String> = splitArtists(value)

    private fun normalizeTitle(value: String): String =
        WHITESPACE.replace(value.trim(), " ").lowercase(Locale.ROOT)

    private fun splitArtists(value: String): List<String> =
        ARTIST_SPLIT.split(value)
            .map { normalizeTitle(it) }
            .filter { it.isNotBlank() }

    private fun normalizeMediaUri(value: String?): String? =
        value?.trim()?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)

    private const val DURATION_IDENTITY_TOLERANCE_MS = 3_000L
}

object MusicPlaybackStore {
    private val _state = MutableStateFlow(MusicPlaybackSnapshot.EMPTY)
    val state: StateFlow<MusicPlaybackSnapshot> = _state.asStateFlow()
    private var lastPrimaryUpdateElapsedMs = Long.MIN_VALUE

    @Synchronized
    fun publish(snapshot: MusicPlaybackSnapshot) {
        publishAt(snapshot, SystemClock.elapsedRealtime())
    }

    @Synchronized
    internal fun publishAt(snapshot: MusicPlaybackSnapshot, nowElapsedMs: Long) {
        val previous = _state.value
        if (snapshot.source.isPrimary && snapshot.hasSong) {
            lastPrimaryUpdateElapsedMs = nowElapsedMs
        }
        if (snapshot.source == MusicPlaybackSource.AdbFallback && hasFreshPrimaryLocked(nowElapsedMs)) {
            return
        }
        if (snapshot.hasSong) {
            _state.value = if (previous.hasSong && snapshot.isSameSongAs(previous)) {
                snapshot.mergeMissingFrom(previous)
            } else {
                snapshot
            }
        } else if (previous.hasSong) {
            _state.value = snapshot
            lastPrimaryUpdateElapsedMs = Long.MIN_VALUE
        } else if (snapshot.source != MusicPlaybackSource.AdbFallback) {
            lastPrimaryUpdateElapsedMs = Long.MIN_VALUE
        }
    }

    @Synchronized
    fun clearIfPackage(packageName: String) {
        if (_state.value.packageName == packageName) {
            _state.value = MusicPlaybackSnapshot.EMPTY
            lastPrimaryUpdateElapsedMs = Long.MIN_VALUE
        }
    }

    @Synchronized
    fun shouldPollFallback(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean =
        !hasFreshPrimaryLocked(nowElapsedMs)

    @Synchronized
    internal fun resetForTest() {
        _state.value = MusicPlaybackSnapshot.EMPTY
        lastPrimaryUpdateElapsedMs = Long.MIN_VALUE
    }

    private fun hasFreshPrimaryLocked(nowElapsedMs: Long): Boolean {
        val last = lastPrimaryUpdateElapsedMs
        return last != Long.MIN_VALUE && nowElapsedMs >= last && nowElapsedMs - last <= PRIMARY_SOURCE_FRESH_MS
    }

    private fun MusicPlaybackSnapshot.mergeMissingFrom(previous: MusicPlaybackSnapshot): MusicPlaybackSnapshot =
        copy(
            artist = artist.ifBlank { previous.artist },
            durationMs = durationMs.takeIf { it > 0L } ?: previous.durationMs,
            packageName = packageName.ifBlank { previous.packageName },
            mediaUri = mediaUri ?: previous.mediaUri,
            metadataLyrics = metadataLyrics ?: previous.metadataLyrics,
            timedLyrics = timedLyrics?.takeIf { it.isNotEmpty() } ?: previous.timedLyrics,
            realtimeLyrics = realtimeLyrics.ifEmpty {
                if (playing && previous.playing) previous.realtimeLyrics else emptyList()
            },
        )

    private const val PRIMARY_SOURCE_FRESH_MS = 5_000L
}

object SupportedMusicPackages {
    val names: Set<String> = setOf(
        "com.byd.mediacenter",
        "com.byd.synclink",
        "com.netease.cloudmusic",
        "com.kugou.android",
        "com.qq.music",
        "com.tencent.qqmusic",
        "cmccwm.mobilemusic",
        "cn.kuwo.player",
        "remix.music.player",
        "cmgyunting.vehicleplayer.cnr",
    )
}
