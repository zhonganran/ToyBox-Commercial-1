package com.byd.wjx.lyrics

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal object LyricParsers {
    private val timeTag = Regex("""\[(\d+):(\d+)(?:\.(\d+))?]""")
    private val qishuiTimeTag = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val qishuiInlineTag = Regex("""<[^>]*>""")

    fun parseQishuiLyricResponse(raw: String, key: String): LyricsResult? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val content = root.optJSONObject("data")
            ?.optJSONObject("lyric")
            ?.optString("content")
            .orEmpty()
            .ifBlank { root.optJSONObject("lyric")?.optString("content").orEmpty() }
            .ifBlank { root.optString("lrc_content") }
        val lines = parseQishuiLyricContent(content)
        return lines.takeIf(List<LyricLine>::isNotEmpty)?.let { LyricsResult(key, it) }
    }

    fun parseQishuiLyricContent(raw: String): List<LyricLine> {
        val lines = raw.lineSequence().mapNotNull { sourceLine ->
            val match = qishuiTimeTag.matchEntire(sourceLine.trim()) ?: return@mapNotNull null
            val timeMs = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val text = qishuiInlineTag.replace(match.groupValues[3], "").trim()
            if (LyricTextFilter.isUsable(text)) LyricLine(timeMs, text) else null
        }.distinctBy { it.timeMs to it.text.lowercase(Locale.ROOT) }
            .sortedBy(LyricLine::timeMs)
            .toList()
        return lines.ifEmpty { parseLrcText(qishuiInlineTag.replace(raw, "")) }
    }

    fun parseQishuiSearchCandidates(raw: String): List<OnlineSongCandidate> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val tracks = mutableListOf<JSONObject>()
        root.optJSONArray("result_groups")?.let { groups ->
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                if (group.optString("id") != "tracks") continue
                group.optJSONArray("data")?.let { data ->
                    for (itemIndex in 0 until data.length()) {
                        val item = data.optJSONObject(itemIndex) ?: continue
                        tracks += item.optJSONObject("entity")?.optJSONObject("track")
                            ?: item.optJSONObject("track")
                            ?: item
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            val fallback = root.optJSONObject("data")?.optJSONArray("tracks")
                ?: root.optJSONArray("tracks")
            if (fallback != null) {
                for (index in 0 until fallback.length()) {
                    fallback.optJSONObject(index)?.let(tracks::add)
                }
            }
        }
        return tracks.mapNotNull { track ->
            val id = track.optString("id").ifBlank { track.optString("track_id") }
                .takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = track.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val artists = track.optJSONArray("artists")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)
                }
            }.orEmpty()
            OnlineSongCandidate(
                id = id,
                title = title,
                artists = artists,
                durationMs = track.optLong("duration").coerceAtLeast(0L),
            )
        }.distinctBy(OnlineSongCandidate::id)
    }

    fun parseBydMetadataLyrics(raw: String?, key: String): LyricsResult? {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return null
        val lines = if (text.startsWith("[{")) {
            val jsonLines = runCatching {
                val array = JSONArray(text)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val time = item.optLong("time", -1L).takeIf { it >= 0L } ?: return@mapNotNull null
                    val line = item.optString("text").trim()
                    if (LyricTextFilter.isUsable(line)) LyricLine(time, line) else null
                }
            }.getOrNull().orEmpty()
            jsonLines.ifEmpty { parseLrcText(text) }
        } else {
            parseLrcText(text)
        }
        return lines.takeIf(List<LyricLine>::isNotEmpty)
            ?.let { LyricsResult(key, it.sortedBy(LyricLine::timeMs)) }
    }

    fun parseLrcText(raw: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { sourceLine ->
            val tags = timeTag.findAll(sourceLine).toList()
            if (tags.isEmpty()) return@forEach
            val text = sourceLine.replace(timeTag, "").trim()
            if (!LyricTextFilter.isUsable(text)) return@forEach
            tags.forEach { match ->
                val minute = match.groupValues[1].toLongOrNull() ?: return@forEach
                val second = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fraction = match.groupValues.getOrNull(3).orEmpty()
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLongOrNull()?.times(100L)
                    2 -> fraction.toLongOrNull()?.times(10L)
                    else -> fraction.take(3).toLongOrNull()
                } ?: 0L
                lines += LyricLine(minute * 60_000L + second * 1_000L + millis, text)
            }
        }
        return lines.distinctBy { it.timeMs to it.text.lowercase(Locale.ROOT) }
            .sortedBy(LyricLine::timeMs)
    }
}
