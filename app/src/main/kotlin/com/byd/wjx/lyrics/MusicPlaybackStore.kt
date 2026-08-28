package com.byd.wjx.lyrics

import android.os.SystemClock
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Arbitrates primary media sessions and the lower-priority on-device ADB fallback. */
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
        if (snapshot.source == MusicPlaybackSource.AdbFallback && previous.hasSong && previous.playing) {
            val sameSongIgnoringPackage = MusicIdentity.isSameSong(
                previousTitle = previous.title,
                previousArtist = previous.artist,
                nextTitle = snapshot.title,
                nextArtist = snapshot.artist,
            )
            if (sameSongIgnoringPackage) {
                val previousPosition = previous.projectedPositionMs(nowElapsedMs)
                val fallbackPosition = snapshot.projectedPositionMs(nowElapsedMs)
                val driftMs = fallbackPosition - previousPosition
                if (previous.source.isPrimary && abs(driftMs) > FALLBACK_POSITION_DRIFT_LIMIT_MS) {
                    WjxFileLogger.d(
                        TAG,
                        "ADB position rejected drift=${driftMs}ms currentPkg=${previous.packageName} " +
                            "fallbackPkg=${snapshot.packageName} title=${snapshot.title.take(48)}",
                    )
                    return
                }
                val shouldLogMerge = previous.source != MusicPlaybackSource.AdbFallback
                _state.value = previous.withPlaybackPositionFrom(snapshot)
                if (shouldLogMerge) {
                    WjxFileLogger.d(
                        TAG,
                        "ADB position merged currentPkg=${previous.packageName} " +
                            "fallbackPkg=${snapshot.packageName} position=${snapshot.positionMs} " +
                            "title=${snapshot.title.take(48)}",
                    )
                }
                return
            }
            if (previous.packageName.isNotBlank() && snapshot.packageName != previous.packageName) {
                WjxFileLogger.d(
                    TAG,
                    "ADB snapshot rejected currentPkg=${previous.packageName} " +
                        "fallbackPkg=${snapshot.packageName} " +
                        "currentTitle=${previous.title.take(48)} " +
                        "fallbackTitle=${snapshot.title.take(48)}",
                )
                return
            }
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
        return last != Long.MIN_VALUE &&
            nowElapsedMs >= last &&
            nowElapsedMs - last <= PRIMARY_SOURCE_FRESH_MS
    }

    private fun MusicPlaybackSnapshot.mergeMissingFrom(
        previous: MusicPlaybackSnapshot,
    ): MusicPlaybackSnapshot = copy(
        artist = artist.ifBlank { previous.artist },
        durationMs = durationMs.takeIf { it > 0L } ?: previous.durationMs,
        packageName = packageName.ifBlank { previous.packageName },
        mediaId = mediaId ?: previous.mediaId,
        mediaUri = mediaUri ?: previous.mediaUri,
        sessionUpdatedElapsedMs = sessionUpdatedElapsedMs.takeIf { it > 0L }
            ?: previous.sessionUpdatedElapsedMs,
        metadataLyrics = metadataLyrics ?: previous.metadataLyrics,
        timedLyrics = timedLyrics?.takeIf { it.isNotEmpty() } ?: previous.timedLyrics,
        realtimeLyrics = realtimeLyrics.ifEmpty {
            if (playing && previous.playing) previous.realtimeLyrics else emptyList()
        },
    )

    private fun MusicPlaybackSnapshot.withPlaybackPositionFrom(
        fallback: MusicPlaybackSnapshot,
    ): MusicPlaybackSnapshot = copy(
        positionMs = fallback.positionMs,
        playing = fallback.playing,
        anchorElapsedMs = fallback.anchorElapsedMs,
        sessionUpdatedElapsedMs = fallback.sessionUpdatedElapsedMs,
        playbackSpeed = fallback.playbackSpeed,
    )

    private const val TAG = "MusicPlaybackStore"
    private const val PRIMARY_SOURCE_FRESH_MS = 5_000L
    private const val FALLBACK_POSITION_DRIFT_LIMIT_MS = 10_000L
}
