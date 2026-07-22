package com.byd.wjx.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun playingSong_rejectsNotificationLyricLineAsNewIdentity() {
        val previous = MusicPlaybackSnapshot(
            title = "Oh I Never Felt So Alive",
            artist = "Dabin",
            playing = true,
            packageName = "com.byd.mediacenter",
        )

        assertFalse(
            shouldAcceptNotificationIdentity(
                previous = previous,
                packageName = "com.byd.mediacenter",
                title = "Wide open eyes I'm seeing it all",
                artist = "Dabin",
            ),
        )
    }

    @Test
    fun stoppedSong_acceptsNotificationIdentityChange() {
        val previous = MusicPlaybackSnapshot(
            title = "旧歌",
            artist = "歌手",
            playing = false,
            packageName = "com.byd.mediacenter",
        )

        assertTrue(
            shouldAcceptNotificationIdentity(
                previous = previous,
                packageName = "com.byd.mediacenter",
                title = "新歌",
                artist = "歌手",
            ),
        )
    }

    @Test
    fun preferredSession_usesRecentThirdPartyPlaybackInsteadOfStaleMediaCenter() {
        val selected = selectPreferredMusicSnapshot(
            listOf(
                MusicPlaybackSnapshot(
                    title = "旧歌",
                    artist = "比亚迪媒体中心",
                    playing = true,
                    packageName = "com.byd.mediacenter",
                    anchorElapsedMs = 10_000L,
                ),
                MusicPlaybackSnapshot(
                    title = "新歌",
                    artist = "歌手A",
                    playing = true,
                    packageName = "com.kugou.android",
                    anchorElapsedMs = 65_000L,
                ),
            ),
        )

        assertEquals("com.kugou.android", selected?.packageName)
        assertEquals("新歌", selected?.title)
    }
}
