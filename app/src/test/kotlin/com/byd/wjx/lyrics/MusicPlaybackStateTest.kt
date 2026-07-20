package com.byd.wjx.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class MusicPlaybackStateTest {
    @After
    fun tearDown() {
        MusicPlaybackStore.resetForTest()
    }

    @Test
    fun musicIdentity_matchesSameTitleWithSharedArtist() {
        assertTrue(
            MusicIdentity.isSameSong(
                previousTitle = " 晚风 ",
                previousArtist = "歌手A feat. 歌手B",
                nextTitle = "晚风",
                nextArtist = "歌手B",
            ),
        )
    }

    @Test
    fun musicIdentity_rejectsDifferentTitle() {
        assertFalse(
            MusicIdentity.isSameSong(
                previousTitle = "晚风",
                previousArtist = "歌手A",
                nextTitle = "晴天",
                nextArtist = "歌手A",
            ),
        )
    }

    @Test
    fun songKey_usesNormalizedPrimaryArtist() {
        assertEquals("晚风\u0000歌手a", MusicIdentity.songKey(" 晚风 ", "歌手A / 歌手B"))
    }

    @Test
    fun trackKey_changesWhenPlayerAddsReliableIdentity() {
        val basic = MusicPlaybackSnapshot(title = "晚风")
        val rich = MusicPlaybackSnapshot(
            title = "晚风",
            artist = "歌手A",
            durationMs = 240_000L,
            mediaUri = "file:///music/song.mp3",
        )

        assertFalse(basic.songKey() == rich.songKey())
    }

    @Test
    fun sameTrack_rejectsSameTitleWhenMediaUriChanges() {
        val previous = MusicPlaybackSnapshot(
            title = "同名歌曲",
            artist = "歌手A",
            mediaUri = "file:///music/old.mp3",
        )
        val next = previous.copy(mediaUri = "file:///music/new.mp3")

        assertFalse(next.isSameSongAs(previous))
    }

    @Test
    fun trackKey_changesWhenEmbeddedLyricsAreCorrected() {
        val stale = MusicPlaybackSnapshot(
            title = "晚风",
            artist = "歌手A",
            metadataLyrics = "[00:01.00]旧歌词",
        )
        val corrected = stale.copy(metadataLyrics = "[00:01.00]正确歌词")

        assertFalse(stale.songKey() == corrected.songKey())
    }

    @Test
    fun hasSong_ignoresPlayerPlaceholderTitle() {
        assertFalse(MusicPlaybackSnapshot(title = "无歌曲", artist = "无艺术家", playing = true).hasSong)
        assertFalse(MusicPlaybackSnapshot(title = "Unknown Title", playing = true).hasSong)
        assertFalse(MusicPlaybackSnapshot(title = "音乐 正在播放", playing = true).hasSong)
        assertFalse(MusicPlaybackSnapshot(title = "“音乐”正在运行", playing = true).hasSong)
    }

    @Test
    fun musicPlaybackStore_preservesArtistWhenSameSongFallbackIsMissingArtist() {
        MusicPlaybackStore.publish(MusicPlaybackSnapshot.EMPTY)
        MusicPlaybackStore.publish(
            MusicPlaybackSnapshot(
                title = "Kiss By The Pool",
                artist = "7YE",
                playing = true,
                packageName = "com.byd.mediacenter",
            ),
        )

        MusicPlaybackStore.publish(
            MusicPlaybackSnapshot(
                title = "kiss by the pool",
                artist = "",
                positionMs = 2_000L,
                playing = true,
                packageName = "com.byd.mediacenter",
            ),
        )

        val snapshot = MusicPlaybackStore.state.value
        assertEquals("7YE", snapshot.artist)
        assertEquals(2_000L, snapshot.positionMs)
        MusicPlaybackStore.publish(MusicPlaybackSnapshot.EMPTY)
    }

    @Test
    fun musicPlaybackStore_preservesRichLyricsWhenFallbackSnapshotLacksThem() {
        MusicPlaybackStore.publish(MusicPlaybackSnapshot.EMPTY)
        val timed = listOf(LyricLine(1_000L, "第一句"))
        MusicPlaybackStore.publish(
            MusicPlaybackSnapshot(
                title = "晚风",
                artist = "歌手A",
                durationMs = 240_000L,
                positionMs = 100L,
                playing = true,
                packageName = "com.byd.mediacenter",
                mediaUri = "file:///sdcard/Music/a.mp3",
                metadataLyrics = "[{\"time\":1000,\"text\":\"第一句\"}]",
                timedLyrics = timed,
                realtimeLyrics = listOf("上一句", "当前句", "下一句"),
            ),
        )

        MusicPlaybackStore.publish(
            MusicPlaybackSnapshot(
                title = "晚风",
                artist = "歌手A",
                positionMs = 2_000L,
                playing = true,
            ),
        )

        val snapshot = MusicPlaybackStore.state.value
        assertEquals("com.byd.mediacenter", snapshot.packageName)
        assertEquals("file:///sdcard/Music/a.mp3", snapshot.mediaUri)
        assertEquals(timed, snapshot.timedLyrics)
        assertEquals(listOf("上一句", "当前句", "下一句"), snapshot.realtimeLyrics)
        assertEquals(2_000L, snapshot.positionMs)
        MusicPlaybackStore.publish(MusicPlaybackSnapshot.EMPTY)
    }

    @Test
    fun musicPlaybackStore_rejectsAdbPositionResetWhilePrimaryIsFresh() {
        MusicPlaybackStore.resetForTest()
        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "晚风",
                artist = "歌手A",
                positionMs = 84_000L,
                playing = true,
                source = MusicPlaybackSource.MediaController,
            ),
            nowElapsedMs = 1_000L,
        )

        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "晚风",
                artist = "歌手A",
                positionMs = 0L,
                playing = true,
                source = MusicPlaybackSource.AdbFallback,
            ),
            nowElapsedMs = 3_000L,
        )

        assertEquals(84_000L, MusicPlaybackStore.state.value.positionMs)
        assertEquals(MusicPlaybackSource.MediaController, MusicPlaybackStore.state.value.source)
    }

    @Test
    fun musicPlaybackStore_rejectsDifferentAdbSongWhilePrimaryIsFresh() {
        MusicPlaybackStore.resetForTest()
        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "当前歌曲",
                playing = true,
                source = MusicPlaybackSource.Notification,
            ),
            nowElapsedMs = 10_000L,
        )

        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "过期歌曲",
                playing = false,
                source = MusicPlaybackSource.AdbFallback,
            ),
            nowElapsedMs = 12_000L,
        )

        assertEquals("当前歌曲", MusicPlaybackStore.state.value.title)
    }

    @Test
    fun musicPlaybackStore_allowsAdbFallbackAfterPrimaryExpires() {
        MusicPlaybackStore.resetForTest()
        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "当前歌曲",
                positionMs = 50_000L,
                playing = true,
                source = MusicPlaybackSource.MediaController,
            ),
            nowElapsedMs = 20_000L,
        )

        assertFalse(MusicPlaybackStore.shouldPollFallback(nowElapsedMs = 24_999L))
        assertTrue(MusicPlaybackStore.shouldPollFallback(nowElapsedMs = 25_001L))

        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "当前歌曲",
                positionMs = 56_000L,
                playing = true,
                source = MusicPlaybackSource.AdbFallback,
            ),
            nowElapsedMs = 25_001L,
        )

        assertEquals(56_000L, MusicPlaybackStore.state.value.positionMs)
        assertEquals(MusicPlaybackSource.AdbFallback, MusicPlaybackStore.state.value.source)
    }
}
