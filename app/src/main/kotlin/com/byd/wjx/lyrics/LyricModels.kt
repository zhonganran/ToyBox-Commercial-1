package com.byd.wjx.lyrics

data class LyricLine(val timeMs: Long, val text: String)

data class LyricsResult(
    val key: String = "",
    val lines: List<LyricLine> = emptyList(),
)

internal data class OnlineSongCandidate(
    val id: String,
    val title: String,
    val artists: List<String>,
    val durationMs: Long,
)

object LyricTextFilter {
    private val badParts = listOf("暂无歌词", "com.byd", "metadata", "MEDIA_", "lrc_line")
    private val dateLine = Regex("""\d{4}-\d{2}-\d{2}""")

    fun isUsable(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        return text.isNotBlank() &&
            !dateLine.containsMatchIn(text) &&
            badParts.none { text.contains(it, ignoreCase = true) }
    }
}
