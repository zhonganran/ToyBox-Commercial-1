package com.byd.wjx.lyrics

import android.os.SystemClock
import kotlin.math.roundToLong

data class MusicPlaybackSnapshot(
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playing: Boolean = false,
    val packageName: String = "",
    val mediaId: String? = null,
    val mediaUri: String? = null,
    val metadataLyrics: String? = null,
    val timedLyrics: List<LyricLine>? = null,
    val realtimeLyrics: List<String> = emptyList(),
    val anchorElapsedMs: Long = SystemClock.elapsedRealtime(),
    val sessionUpdatedElapsedMs: Long = anchorElapsedMs,
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
        val providerTrackId = if (QishuiMusic.isPackage(packageName)) {
            QishuiMusic.extractTrackId(mediaId, mediaUri).orEmpty()
        } else {
            ""
        }
        return "$identity\u0000${lyricRevision?.toString().orEmpty()}\u0000$providerTrackId"
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
