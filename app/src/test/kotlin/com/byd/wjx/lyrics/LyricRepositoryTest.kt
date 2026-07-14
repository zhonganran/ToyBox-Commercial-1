package com.byd.wjx.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricRepositoryTest {
    @Test
    fun parseLrcText_supportsMultipleTagsAndCentiseconds() {
        val lines = LyricRepository.parseLrcText(
            """
            [00:01.20][00:03.45]第一句
            [00:05.6]第二句
            [ar:歌手]
            """.trimIndent(),
        )

        assertEquals(3, lines.size)
        assertEquals(LyricLine(1_200L, "第一句"), lines[0])
        assertEquals(LyricLine(3_450L, "第一句"), lines[1])
        assertEquals(LyricLine(5_600L, "第二句"), lines[2])
    }

    @Test
    fun parseBydMetadataLyrics_treatsStandardLrcAsLrcNotJson() {
        val result = LyricRepository.parseBydMetadataLyrics(
            raw = """
                [00:01.00]第一句
                [00:02.50]第二句
            """.trimIndent(),
            key = "song",
        )

        requireNotNull(result)
        assertEquals(2, result.lines.size)
        assertEquals(LyricLine(1_000L, "第一句"), result.lines[0])
        assertEquals(LyricLine(2_500L, "第二句"), result.lines[1])
    }

    @Test
    fun onlineMatcher_prefersMatchingArtistInsteadOfFirstSameTitle() {
        val selected = OnlineLyricMatcher.selectBest(
            MusicPlaybackSnapshot(title = "晚风", artist = "歌手B", durationMs = 240_000L),
            listOf(
                OnlineSongCandidate("wrong", "晚风", listOf("歌手A"), 240_000L),
                OnlineSongCandidate("correct", "晚风", listOf("歌手B"), 241_000L),
            ),
        )

        assertEquals("correct", selected?.id)
    }

    @Test
    fun onlineMatcher_rejectsTitleOnlyAmbiguousSearch() {
        val selected = OnlineLyricMatcher.selectBest(
            MusicPlaybackSnapshot(title = "晚风"),
            listOf(OnlineSongCandidate("first", "晚风", listOf("歌手A"), 240_000L)),
        )

        assertNull(selected)
    }

    @Test
    fun onlineMatcher_usesDurationWhenArtistIsUnavailable() {
        val selected = OnlineLyricMatcher.selectBest(
            MusicPlaybackSnapshot(title = "晚风", durationMs = 240_000L),
            listOf(
                OnlineSongCandidate("wrong", "晚风", listOf("歌手A"), 180_000L),
                OnlineSongCandidate("correct", "晚风", listOf("歌手B"), 241_000L),
            ),
        )

        assertEquals("correct", selected?.id)
    }

    @Test
    fun onlineMatcher_rejectsUnverifiableCandidateWhenOnlyDurationCanDisambiguate() {
        val selected = OnlineLyricMatcher.selectBest(
            MusicPlaybackSnapshot(title = "晚风", durationMs = 240_000L),
            listOf(OnlineSongCandidate("unknown", "晚风", listOf("歌手A"), 0L)),
        )

        assertNull(selected)
    }

    @Test
    fun onlineMatcher_rejectsVersionSuffixWithoutDurationEvidence() {
        val selected = OnlineLyricMatcher.selectBest(
            MusicPlaybackSnapshot(title = "晚风", artist = "歌手A"),
            listOf(OnlineSongCandidate("live", "晚风 Live", listOf("歌手A"), 0L)),
        )

        assertNull(selected)
    }

    @Test
    fun onlineMatcher_acceptsVersionSuffixWhenDurationCloselyMatches() {
        val selected = OnlineLyricMatcher.selectBest(
            MusicPlaybackSnapshot(title = "晚风", artist = "歌手A", durationMs = 240_000L),
            listOf(OnlineSongCandidate("live", "晚风 Live", listOf("歌手A"), 243_000L)),
        )

        assertEquals("live", selected?.id)
    }
}
