package com.byd.wjx.lyrics

import android.net.Uri
import android.util.Base64
import com.byd.wjx.log.WjxFileLogger
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

internal object OnlineLyricMatcher {
    fun selectBest(snapshot: MusicPlaybackSnapshot, candidates: List<OnlineSongCandidate>): OnlineSongCandidate? {
        val queryArtists = MusicIdentity.normalizedArtists(snapshot.artist)
        val hasDuration = snapshot.durationMs > 0L
        if (queryArtists.isEmpty() && !hasDuration) return null
        return candidates
            .mapNotNull { candidate -> score(snapshot, candidate)?.let { candidate to it } }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun score(snapshot: MusicPlaybackSnapshot, candidate: OnlineSongCandidate): Int? {
        val queryTitle = comparableTitle(snapshot.title)
        val candidateTitle = comparableTitle(candidate.title)
        if (queryTitle.isBlank() || candidateTitle.isBlank()) return null
        val exactTitle = queryTitle == candidateTitle
        val titleScore = when {
            exactTitle -> 100
            queryTitle.length >= 2 && candidateTitle.length >= 2 &&
                (queryTitle.contains(candidateTitle) || candidateTitle.contains(queryTitle)) -> 65
            else -> return null
        }

        val queryArtists = MusicIdentity.normalizedArtists(snapshot.artist)
        val candidateArtists = candidate.artists.flatMap(MusicIdentity::normalizedArtists)
        val artistMatched = queryArtists.isNotEmpty() && candidateArtists.any { candidateArtist ->
            queryArtists.any { queryArtist ->
                queryArtist == candidateArtist ||
                    (queryArtist.length >= 2 && candidateArtist.length >= 2 &&
                        (queryArtist.contains(candidateArtist) || candidateArtist.contains(queryArtist)))
            }
        }
        if (queryArtists.isNotEmpty() && !artistMatched) return null
        if (queryArtists.isEmpty() && snapshot.durationMs > 0L && candidate.durationMs <= 0L) return null

        val durationScore = when {
            snapshot.durationMs <= 0L || candidate.durationMs <= 0L -> 0
            abs(snapshot.durationMs - candidate.durationMs) <= 2_000L -> 30
            abs(snapshot.durationMs - candidate.durationMs) <= 8_000L -> 20
            abs(snapshot.durationMs - candidate.durationMs) <= 20_000L -> 5
            else -> return null
        }
        if (!exactTitle && durationScore < 20) return null
        val score = titleScore + (if (artistMatched) 60 else 0) + durationScore
        val minimum = if (queryArtists.isNotEmpty()) 120 else 100
        return score.takeIf { it >= minimum }
    }

    private fun comparableTitle(value: String): String =
        MusicIdentity.normalizedTitle(value).filter(Char::isLetterOrDigit)
}

object LyricTextFilter {
    private val BAD_PARTS = listOf("暂无歌词", "com.byd", "metadata", "MEDIA_", "lrc_line")
    private val DATE_LINE = Regex("""\d{4}-\d{2}-\d{2}""")

    fun isUsable(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return false
        if (DATE_LINE.containsMatchIn(text)) return false
        return BAD_PARTS.none { text.contains(it, ignoreCase = true) }
    }
}

class LyricRepository(
    private val httpClient: OkHttpClient,
) {
    private val cache = linkedMapOf<String, LyricsResult>()

    suspend fun load(snapshot: MusicPlaybackSnapshot): LyricsResult = withContext(Dispatchers.IO) {
        val key = snapshot.songKey()
        cache[key]?.let { return@withContext it }
        val result = snapshot.timedLyrics?.takeIf { it.isNotEmpty() }?.let { LyricsResult(key, it) }
            ?: parseBydMetadataLyrics(snapshot.metadataLyrics, key)
            ?: loadLocalLyrics(snapshot, key)
            ?: loadOnlineLyrics(snapshot, key)
            ?: LyricsResult(key)
        putCache(key, result)
        result
    }

    private fun putCache(key: String, result: LyricsResult) {
        if (cache.size >= MAX_CACHE_SIZE) {
            val first = cache.keys.firstOrNull()
            if (first != null) cache.remove(first)
        }
        cache[key] = result
    }

    private fun loadLocalLyrics(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        val songFile = resolveMediaFile(snapshot.mediaUri) ?: return null
        val lyricFile = findSiblingLrc(songFile) ?: return null
        val bytes = runCatching { lyricFile.readBytes() }.getOrNull() ?: return null
        val utf8 = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        val raw = if (utf8?.contains("[") == true) {
            utf8
        } else {
            runCatching { bytes.toString(charset("GBK")) }.getOrNull()
        } ?: return null
        val lines = parseLrcText(raw)
        if (lines.isNotEmpty()) {
            WjxFileLogger.i(TAG, "local lyrics loaded ${lines.size} lines from ${lyricFile.absolutePath}")
        }
        return lines.takeIf { it.isNotEmpty() }?.let { LyricsResult(key, it) }
    }

    private suspend fun loadOnlineLyrics(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        val netease = runCatching { loadNeteaseLyrics(snapshot, key) }
            .onFailure { WjxFileLogger.w(TAG, "netease lyrics failed: ${it.message}") }
            .getOrNull()
        if (netease != null) return netease
        return runCatching { loadKugouLyrics(snapshot, key) }
            .onFailure { WjxFileLogger.w(TAG, "kugou lyrics failed: ${it.message}") }
            .getOrNull()
    }

    private fun loadNeteaseLyrics(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        val keyword = listOf(snapshot.title, snapshot.artist).filter { it.isNotBlank() }.joinToString(" ")
        if (keyword.isBlank()) return null
        val body = FormBody.Builder()
            .add("s", keyword)
            .add("type", "1")
            .add("limit", "5")
            .add("offset", "0")
            .build()
        val searchJson = httpClient.newCall(
            Request.Builder()
                .url("https://music.163.com/api/search/get/web")
                .header("User-Agent", USER_AGENT)
                .post(body)
                .build()
        ).execute().use { it.body?.string().orEmpty() }
        val songs = JSONObject(searchJson).optJSONObject("result")?.optJSONArray("songs") ?: return null
        val candidates = (0 until songs.length()).mapNotNull { index ->
            val song = songs.optJSONObject(index) ?: return@mapNotNull null
            val id = song.optLong("id").takeIf { it > 0L }?.toString() ?: return@mapNotNull null
            val artists = song.optJSONArray("artists") ?: song.optJSONArray("ar")
            OnlineSongCandidate(
                id = id,
                title = song.optString("name"),
                artists = artists?.let { array ->
                    (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
                }.orEmpty(),
                durationMs = song.optLong("duration").takeIf { it > 0L }
                    ?: song.optLong("dt").takeIf { it > 0L }
                    ?: 0L,
            )
        }
        val songId = OnlineLyricMatcher.selectBest(snapshot, candidates)?.id?.toLongOrNull()
            ?: return null
        val lyricJson = httpClient.newCall(
            Request.Builder()
                .url("https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1")
                .header("User-Agent", USER_AGENT)
                .build()
        ).execute().use { it.body?.string().orEmpty() }
        val lrc = JSONObject(lyricJson).optJSONObject("lrc")?.optString("lyric").orEmpty()
        val lines = parseLrcText(lrc)
        return lines.takeIf { it.isNotEmpty() }?.let { LyricsResult(key, it) }
    }

    private fun loadKugouLyrics(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        val keyword = listOf(snapshot.title, snapshot.artist).filter { it.isNotBlank() }.joinToString(" ")
        if (keyword.isBlank()) return null
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val searchJson = httpClient.newCall(
            Request.Builder()
                .url("https://mobileservice.kugou.com/api/v3/search/song?format=json&keyword=$encoded&page=1&pagesize=5")
                .header("User-Agent", USER_AGENT)
                .build()
        ).execute().use { it.body?.string().orEmpty() }
        val songs = JSONObject(searchJson).optJSONObject("data")?.optJSONArray("info") ?: return null
        val candidates = (0 until songs.length()).mapNotNull { index ->
            val song = songs.optJSONObject(index) ?: return@mapNotNull null
            val hash = song.optString("hash").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            OnlineSongCandidate(
                id = hash,
                title = song.optString("songname"),
                artists = listOfNotNull(song.optString("singername").takeIf(String::isNotBlank)),
                durationMs = song.optLong("duration").takeIf { it > 0L }?.times(1_000L) ?: 0L,
            )
        }
        val selected = OnlineLyricMatcher.selectBest(snapshot, candidates) ?: return null
        val hash = selected.id
        val durationSec = (snapshot.durationMs / 1000L).takeIf { it > 0L }
            ?: (selected.durationMs / 1_000L).takeIf { it > 0L }
            ?: return null
        val candidatesJson = httpClient.newCall(
            Request.Builder()
                .url("https://krcs.kugou.com/search?ver=1&man=yes&client=pc&keyword=$encoded&duration=$durationSec&hash=$hash")
                .header("User-Agent", USER_AGENT)
                .build()
        ).execute().use { it.body?.string().orEmpty() }
        val candidate = JSONObject(candidatesJson).optJSONArray("candidates")?.optJSONObject(0) ?: return null
        val id = candidate.optString("id").takeIf { it.isNotBlank() } ?: return null
        val accessKey = candidate.optString("accesskey").takeIf { it.isNotBlank() } ?: return null
        val downloadJson = httpClient.newCall(
            Request.Builder()
                .url("https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8")
                .header("User-Agent", USER_AGENT)
                .build()
        ).execute().use { it.body?.string().orEmpty() }
        val content = JSONObject(downloadJson).optString("content").takeIf { it.isNotBlank() } ?: return null
        val raw = String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        val lines = parseLrcText(raw)
        return lines.takeIf { it.isNotEmpty() }?.let { LyricsResult(key, it) }
    }

    private fun resolveMediaFile(mediaUri: String?): File? {
        val raw = mediaUri?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val path = when {
            raw.startsWith("file://", ignoreCase = true) -> runCatching { Uri.parse(raw).path }.getOrNull()
            raw.startsWith("/") -> raw
            else -> null
        } ?: return null
        val decoded = runCatching { URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
        return File(decoded).takeIf { it.isFile && it.canRead() }
    }

    private fun findSiblingLrc(songFile: File): File? {
        val parent = songFile.parentFile ?: return null
        val baseName = songFile.nameWithoutExtension
        val direct = File(parent, "$baseName.lrc")
        if (direct.isFile && direct.canRead()) return direct
        return parent.listFiles()?.firstOrNull {
            it.isFile &&
                it.canRead() &&
                it.name.equals("$baseName.lrc", ignoreCase = true)
        }
    }

    companion object {
        private const val TAG = "LyricRepository"
        private const val MAX_CACHE_SIZE = 24
        private const val USER_AGENT = "Mozilla/5.0 SuperToyBoxLyrics/1.0"
        private val TIME_TAG = Regex("""\[(\d+):(\d+)(?:\.(\d+))?]""")

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
            return lines.takeIf { it.isNotEmpty() }?.let { LyricsResult(key, it.sortedBy(LyricLine::timeMs)) }
        }

        fun parseLrcText(raw: String): List<LyricLine> {
            val out = mutableListOf<LyricLine>()
            raw.lineSequence().forEach { sourceLine ->
                val tags = TIME_TAG.findAll(sourceLine).toList()
                if (tags.isEmpty()) return@forEach
                val text = sourceLine.replace(TIME_TAG, "").trim()
                if (!LyricTextFilter.isUsable(text)) return@forEach
                tags.forEach { match ->
                    val minute = match.groupValues[1].toLongOrNull() ?: return@forEach
                    val second = match.groupValues[2].toLongOrNull() ?: return@forEach
                    val millisRaw = match.groupValues.getOrNull(3).orEmpty()
                    val millis = when (millisRaw.length) {
                        0 -> 0L
                        1 -> millisRaw.toLongOrNull()?.times(100L)
                        2 -> millisRaw.toLongOrNull()?.times(10L)
                        else -> millisRaw.take(3).toLongOrNull()
                    } ?: 0L
                    out += LyricLine(minute * 60_000L + second * 1_000L + millis, text)
                }
            }
            return out.distinctBy { it.timeMs to it.text.lowercase(Locale.ROOT) }.sortedBy { it.timeMs }
        }
    }
}
