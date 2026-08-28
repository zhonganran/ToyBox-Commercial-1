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
        assertFalse(basic.lyricKey() == rich.lyricKey())
    }

    @Test
    fun lyricKey_distinguishesDifferentTrackVersions() {
        val complete = MusicPlaybackSnapshot(
            title = "蔡健雅-被驯服的象",
            artist = "蔡健雅",
            durationMs = 208_000L,
            mediaUri = "content://player/current/123",
        )
        val incomplete = complete.copy(durationMs = 0L, mediaUri = null)
        val changedUri = complete.copy(mediaUri = "content://player/session/456")

        assertFalse(complete.lyricKey() == incomplete.lyricKey())
        assertFalse(complete.lyricKey() == changedUri.lyricKey())
        assertFalse(complete.songKey() == incomplete.songKey())
        assertFalse(complete.songKey() == changedUri.songKey())
    }

    @Test
    fun qishuiPackages_areSupportedAndTrackIdsAreExtracted() {
        assertTrue(SupportedMusicPackages.names.containsAll(QishuiMusic.packageNames))
        assertTrue(SupportedMusicPackages.isSupported("com.netease.cloudmusic.car.player"))
        assertTrue(SupportedMusicPackages.isSupported("com.kugou.android.auto"))
        assertTrue(SupportedMusicPackages.isSupported("com.tencent.qqmusiccar"))
        assertTrue(SupportedMusicPackages.isSupported("cn.kuwo.kwmusiccar"))
        assertFalse(SupportedMusicPackages.isSupported("com.example.video"))
        assertEquals("7123456789012345678", QishuiMusic.extractTrackId("https://www.qishui.com/track/7123456789012345678"))
        assertEquals("7234567890123456789", QishuiMusic.extractTrackId("luna://play?track_id=7234567890123456789"))
        assertEquals("7345678901234567890", QishuiMusic.extractTrackId("7345678901234567890"))
    }

    @Test
    fun qishuiSongKeyReloadsWhenMediaIdArrivesWithoutChangingOtherPlayers() {
        val qishuiWithoutId = MusicPlaybackSnapshot(
            title = "晚风",
            artist = "歌手A",
            packageName = "com.luna.music",
        )
        val qishuiWithId = qishuiWithoutId.copy(mediaId = "7123456789012345678")
        val neteaseWithoutId = qishuiWithoutId.copy(packageName = "com.netease.cloudmusic")
        val neteaseWithId = neteaseWithoutId.copy(mediaId = "7123456789012345678")

        assertFalse(qishuiWithoutId.lyricKey() == qishuiWithId.lyricKey())
        assertEquals(neteaseWithoutId.lyricKey(), neteaseWithId.lyricKey())
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
    fun sameTrackAndLyricKey_ignorePackageChangesForTheSameMediaIdentity() {
        val mediaCenter = MusicPlaybackSnapshot(
            title = "晚风",
            artist = "歌手A",
            durationMs = 240_000L,
            packageName = "com.byd.mediacenter",
        )
        val copiedSession = mediaCenter.copy(packageName = "com.android.server.telecom")

        assertTrue(copiedSession.isSameSongAs(mediaCenter))
        assertEquals(mediaCenter.lyricKey(), copiedSession.lyricKey())
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
        assertFalse(MusicPlaybackSnapshot(title = "null", artist = "null", playing = true).hasSong)
        assertFalse(MusicPlaybackSnapshot(title = "(null)", playing = true).hasSong)
        assertFalse(MusicPlaybackSnapshot(title = "MediaCenter", artist = "MediaCenter", playing = true).hasSong)
        assertFalse(MusicPlaybackSnapshot(title = "酷我音乐(remote)", artist = "好音质", playing = true).hasSong)
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
    fun musicPlaybackStore_preservesDurationWhenSameTrackMetadataTemporarilyDropsIt() {
        MusicPlaybackStore.publish(
            MusicPlaybackSnapshot(
                title = "蔡健雅-被驯服的象",
                artist = "蔡健雅",
                durationMs = 208_000L,
                mediaUri = "content://player/current/123",
                playing = true,
                packageName = "com.byd.mediacenter",
            ),
        )
        val lyricKey = MusicPlaybackStore.state.value.lyricKey()

        MusicPlaybackStore.publish(
            MusicPlaybackSnapshot(
                title = "蔡健雅-被驯服的象",
                artist = "蔡健雅",
                durationMs = 0L,
                mediaUri = null,
                positionMs = 10_000L,
                playing = true,
                packageName = "com.byd.mediacenter",
            ),
        )

        assertEquals(208_000L, MusicPlaybackStore.state.value.durationMs)
        assertEquals("content://player/current/123", MusicPlaybackStore.state.value.mediaUri)
        assertEquals(lyricKey, MusicPlaybackStore.state.value.lyricKey())
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
        assertEquals(MusicPlaybackSource.MediaController, MusicPlaybackStore.state.value.source)
    }

    @Test
    fun musicPlaybackStore_usesCrossPackageAdbSnapshotOnlyForSameSongPosition() {
        MusicPlaybackStore.resetForTest()
        val timed = listOf(LyricLine(1_000L, "第一句"))
        val qishui = MusicPlaybackSnapshot(
            title = "最浪漫的事",
            artist = "ALAN",
            durationMs = 238_000L,
            positionMs = 30_000L,
            playing = true,
            packageName = "com.luna.music",
            mediaId = "7632385172940703778",
            mediaUri = "luna://play?track_id=7632385172940703778",
            timedLyrics = timed,
            anchorElapsedMs = 1_000L,
            sessionUpdatedElapsedMs = 1_000L,
            source = MusicPlaybackSource.MediaController,
        )
        val originalSongKey = qishui.songKey()
        MusicPlaybackStore.publishAt(qishui, nowElapsedMs = 1_000L)

        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "最浪漫的事",
                artist = "ALAN",
                positionMs = 36_000L,
                playing = true,
                packageName = "com.byd.mediacenter",
                anchorElapsedMs = 7_000L,
                sessionUpdatedElapsedMs = 7_000L,
                source = MusicPlaybackSource.AdbFallback,
            ),
            nowElapsedMs = 7_000L,
        )

        val snapshot = MusicPlaybackStore.state.value
        assertEquals("com.luna.music", snapshot.packageName)
        assertEquals("7632385172940703778", snapshot.mediaId)
        assertEquals("luna://play?track_id=7632385172940703778", snapshot.mediaUri)
        assertEquals(238_000L, snapshot.durationMs)
        assertEquals(timed, snapshot.timedLyrics)
        assertEquals(originalSongKey, snapshot.songKey())
        assertEquals(36_000L, snapshot.positionMs)
        assertEquals(7_000L, snapshot.anchorElapsedMs)
        assertEquals(MusicPlaybackSource.MediaController, snapshot.source)
    }

    @Test
    fun musicPlaybackStore_rejectsCrossPackageAdbLyricLineAsTrackTitle() {
        MusicPlaybackStore.resetForTest()
        val qishui = MusicPlaybackSnapshot(
            title = "最浪漫的事",
            artist = "ALAN",
            durationMs = 238_000L,
            positionMs = 30_000L,
            playing = true,
            packageName = "com.luna.music",
            mediaId = "7632385172940703778",
            source = MusicPlaybackSource.MediaController,
        )
        MusicPlaybackStore.publishAt(qishui, nowElapsedMs = 1_000L)

        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "我能想到最浪漫的事",
                artist = "",
                positionMs = 36_000L,
                playing = true,
                packageName = "com.byd.mediacenter",
                source = MusicPlaybackSource.AdbFallback,
            ),
            nowElapsedMs = 7_000L,
        )

        assertEquals(qishui, MusicPlaybackStore.state.value)
    }

    @Test
    fun musicPlaybackStore_rejectsLargeAdbPositionJumpForProjectedPrimaryPlayback() {
        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "Nightwish-Bye Bye Beautiful",
                artist = "Nightwish",
                positionMs = 30_000L,
                playing = true,
                packageName = "com.byd.mediacenter",
                anchorElapsedMs = 10_000L,
                sessionUpdatedElapsedMs = 10_000L,
                source = MusicPlaybackSource.MediaController,
            ),
            nowElapsedMs = 10_000L,
        )

        MusicPlaybackStore.publishAt(
            MusicPlaybackSnapshot(
                title = "Nightwish-Bye Bye Beautiful",
                artist = "Nightwish",
                positionMs = 105_000L,
                playing = true,
                packageName = "com.byd.mediacenter",
                anchorElapsedMs = 20_000L,
                sessionUpdatedElapsedMs = 20_000L,
                source = MusicPlaybackSource.AdbFallback,
            ),
            nowElapsedMs = 20_000L,
        )

        val snapshot = MusicPlaybackStore.state.value
        assertEquals(30_000L, snapshot.positionMs)
        assertEquals(MusicPlaybackSource.MediaController, snapshot.source)
    }
}
