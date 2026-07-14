package com.byd.wjx.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicNotificationPlaybackTest {
    @Test
    fun sameSongWithoutControllerPosition_keepsProjectedProgress() {
        val previous = MusicPlaybackSnapshot(
            title = "旅行",
            artist = "歌手",
            positionMs = 1_000L,
            playing = true,
            packageName = "com.byd.mediacenter",
            anchorElapsedMs = 10_000L,
        )

        val resolved = resolveNotificationPlaybackAnchor(
            previous = previous,
            sameSong = true,
            statePositionMs = null,
            stateUpdatedAtMs = null,
            nowElapsedMs = 65_176L,
        )

        assertEquals(56_176L, resolved.positionMs)
        assertEquals(65_176L, resolved.anchorElapsedMs)
    }

    @Test
    fun sameSongWithControllerPosition_usesControllerAnchor() {
        val previous = MusicPlaybackSnapshot(
            title = "旅行",
            artist = "歌手",
            positionMs = 1_000L,
            playing = true,
            packageName = "com.byd.mediacenter",
            anchorElapsedMs = 10_000L,
        )

        val resolved = resolveNotificationPlaybackAnchor(
            previous = previous,
            sameSong = true,
            statePositionMs = 57_000L,
            stateUpdatedAtMs = 65_000L,
            nowElapsedMs = 65_176L,
        )

        assertEquals(57_000L, resolved.positionMs)
        assertEquals(65_000L, resolved.anchorElapsedMs)
    }

    @Test
    fun changedSong_doesNotReusePreviousPosition() {
        val previous = MusicPlaybackSnapshot(
            title = "旧歌",
            artist = "歌手",
            positionMs = 80_000L,
            playing = true,
            packageName = "com.byd.mediacenter",
            anchorElapsedMs = 10_000L,
        )

        val resolved = resolveNotificationPlaybackAnchor(
            previous = previous,
            sameSong = false,
            statePositionMs = 80_500L,
            stateUpdatedAtMs = 65_000L,
            nowElapsedMs = 65_176L,
        )

        assertEquals(0L, resolved.positionMs)
        assertEquals(65_176L, resolved.anchorElapsedMs)
    }
}
