package com.byd.wjx.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaSessionDumpParserTest {
    @Test
    fun parse_readsActiveSongFromDumpsysMediaSession() {
        val snapshot = MediaSessionDumpParser.parse(
            """
            Sessions Stack - have 1 sessions:
              SessionRecord{abc packageName=com.netease.cloudmusic}
                metadata: size=3, description=晚风, 歌手A, null
                duration=245000
                state=PlaybackState {state=3, position=12345, buffered position=0, speed=1.0, updated=456}
                android.media.metadata.MEDIA_URI=file:///sdcard/Music/song.mp3
            """.trimIndent(),
        )

        requireNotNull(snapshot)
        assertEquals("晚风", snapshot.title)
        assertEquals("歌手A", snapshot.artist)
        assertEquals(245_000L, snapshot.durationMs)
        assertEquals(12_345L, snapshot.positionMs)
        assertTrue(snapshot.playing)
        assertEquals("com.netease.cloudmusic", snapshot.packageName)
        assertEquals(MusicPlaybackSource.AdbFallback, snapshot.source)
    }

    @Test
    fun parse_prefersPlayingSupportedSessionOverStaleFirstSession() {
        val snapshot = MediaSessionDumpParser.parse(
            """
            Sessions Stack - have 2 sessions:
              SessionRecord{old packageName=com.qq.music}
                metadata: size=3, description=旧歌, 歌手A, null
                state=PlaybackState {state=2, position=8888, buffered position=0, speed=1.0, updated=111}
              SessionRecord{new packageName=com.byd.mediacenter}
                metadata: size=3, description=新歌, 歌手B, null
                android.media.metadata.DURATION=260000
                android.media.metadata.MEDIA_URI=file:///sdcard/Music/new.mp3
                state=PlaybackState {state=3, position=12000, buffered position=0, speed=1.0, updated=222}
            """.trimIndent(),
        )

        requireNotNull(snapshot)
        assertEquals("新歌", snapshot.title)
        assertEquals("歌手B", snapshot.artist)
        assertEquals("com.byd.mediacenter", snapshot.packageName)
        assertEquals(260_000L, snapshot.durationMs)
        assertTrue(snapshot.playing)
    }
}
