package com.byd.wjx.data.dashcam

import java.util.Calendar
import java.util.TimeZone

data class DashcamVideoClip(
    val uri: String,
    val displayName: String,
    val startTs: Long,
    val endTs: Long,
) {
    fun overlaps(startTs: Long, endTs: Long): Boolean =
        this.startTs < endTs && this.endTs > startTs
}

internal data class DashcamVideoFile(
    val uri: String,
    val displayName: String,
    val startTs: Long,
    val durationMs: Long? = null,
)

internal object DashcamVideoTimeline {
    private val timestampPattern = Regex(
        """(?<!\d)(20\d{2})[-_](0?[1-9]|1[0-2])[-_]([0-2]?\d|3[01])[-_]([01]?\d|2[0-3])[-_]([0-5]?\d)[-_]([0-5]?\d)(?!\d)""",
    )

    fun parseStartTimestamp(
        displayName: String,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        val match = timestampPattern.find(displayName) ?: return null
        val parts = match.groupValues.drop(1).mapNotNull(String::toIntOrNull)
        if (parts.size != 6) return null
        return runCatching {
            Calendar.getInstance(timeZone).apply {
                isLenient = false
                clear()
                set(parts[0], parts[1] - 1, parts[2], parts[3], parts[4], parts[5])
            }.timeInMillis
        }.getOrNull()
    }

    fun buildClips(files: List<DashcamVideoFile>): List<DashcamVideoClip> {
        val sorted = files.sortedWith(compareBy(DashcamVideoFile::startTs, DashcamVideoFile::displayName))
        return sorted.mapIndexed { index, file ->
            val nextStart = sorted.getOrNull(index + 1)?.startTs
            val inferredDuration = nextStart?.minus(file.startTs)
                ?.takeIf { it in MIN_INFERRED_DURATION_MS..MAX_INFERRED_DURATION_MS }
                ?: DEFAULT_CLIP_DURATION_MS
            val duration = file.durationMs
                ?.takeIf { it in MIN_MEDIA_DURATION_MS..MAX_MEDIA_DURATION_MS }
                ?: inferredDuration
            DashcamVideoClip(
                uri = file.uri,
                displayName = file.displayName,
                startTs = file.startTs,
                endTs = file.startTs + duration,
            )
        }
    }

    private const val DEFAULT_CLIP_DURATION_MS = 2L * 60L * 1_000L
    private const val MIN_MEDIA_DURATION_MS = 1_000L
    private const val MAX_MEDIA_DURATION_MS = 24L * 60L * 60L * 1_000L
    private const val MIN_INFERRED_DURATION_MS = 10_000L
    private const val MAX_INFERRED_DURATION_MS = 10L * 60L * 1_000L
}
