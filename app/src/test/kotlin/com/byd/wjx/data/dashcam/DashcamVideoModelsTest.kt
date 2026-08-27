package com.byd.wjx.data.dashcam

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashcamVideoModelsTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `filename timestamp is parsed from recorder naming scheme`() {
        val timestamp = DashcamVideoTimeline.parseStartTimestamp(
            "V5IN1H-2026-08-22-14-44-50.mp4",
            utc,
        )

        assertEquals(utcTimestamp(2026, 8, 22, 14, 44, 50), timestamp)
        assertNull(DashcamVideoTimeline.parseStartTimestamp("random-video.mp4", utc))
        assertNull(DashcamVideoTimeline.parseStartTimestamp("V5IN1H-2026-13-40-28-90-90.mp4", utc))
    }

    @Test
    fun `clip end follows next recorder timestamp and final clip uses default duration`() {
        val firstStart = utcTimestamp(2026, 8, 22, 14, 44, 50)
        val secondStart = utcTimestamp(2026, 8, 22, 14, 46, 47)
        val clips = DashcamVideoTimeline.buildClips(
            listOf(
                DashcamVideoFile("second", "second.mp4", secondStart),
                DashcamVideoFile("first", "first.mp4", firstStart),
            ),
        )

        assertEquals(listOf("first", "second"), clips.map(DashcamVideoClip::uri))
        assertEquals(secondStart, clips[0].endTs)
        assertEquals(secondStart + 120_000L, clips[1].endTs)
    }

    @Test
    fun `real media duration takes precedence over filename inference`() {
        val firstStart = utcTimestamp(2026, 8, 22, 14, 44, 50)
        val secondStart = firstStart + 120_000L
        val clips = DashcamVideoTimeline.buildClips(
            listOf(
                DashcamVideoFile("first", "first.mp4", firstStart, durationMs = 61_500L),
                DashcamVideoFile("second", "second.mp4", secondStart, durationMs = 181_000L),
            ),
        )

        assertEquals(firstStart + 61_500L, clips[0].endTs)
        assertEquals(secondStart + 181_000L, clips[1].endTs)
    }

    @Test
    fun `invalid media duration falls back to recorder timeline`() {
        val firstStart = utcTimestamp(2026, 8, 22, 14, 44, 50)
        val secondStart = firstStart + 90_000L
        val clips = DashcamVideoTimeline.buildClips(
            listOf(
                DashcamVideoFile("first", "first.mp4", firstStart, durationMs = 0L),
                DashcamVideoFile("second", "second.mp4", secondStart),
            ),
        )

        assertEquals(secondStart, clips[0].endTs)
    }

    @Test
    fun `clip overlap uses trip time boundaries`() {
        val clip = DashcamVideoClip("uri", "clip.mp4", 100_000L, 220_000L)

        assertTrue(clip.overlaps(150_000L, 300_000L))
        assertTrue(clip.overlaps(50_000L, 150_000L))
        assertFalse(clip.overlaps(220_000L, 300_000L))
        assertFalse(clip.overlaps(10_000L, 100_000L))
    }

    private fun utcTimestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long = Calendar.getInstance(utc).apply {
        clear()
        set(year, month - 1, day, hour, minute, second)
    }.timeInMillis
}
