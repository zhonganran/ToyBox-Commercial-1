package com.byd.wjx.lyrics

import android.util.Base64
import com.byd.wjx.log.WjxFileLogger
import java.net.URLEncoder
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal class QishuiLyricProvider(
    private val httpClient: OkHttpClient,
) {
    fun load(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        QishuiMusic.extractTrackId(snapshot.mediaId, snapshot.mediaUri)?.let { trackId ->
            loadById(trackId, key)?.let { return it }
        }
        return search(snapshot, key)
    }

    fun search(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        val keyword = snapshot.searchKeyword() ?: return null
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val searchJson = httpClient.newCall(
            request(
                "$API_BASE/search/track?q=$encoded&cursor=0&search_method=input" +
                    "&aid=$AID&device_platform=web&channel=pc_web",
            ),
        ).execute().use { it.body?.string().orEmpty() }
        val selected = OnlineLyricMatcher.selectBest(
            snapshot,
            LyricParsers.parseQishuiSearchCandidates(searchJson),
        ) ?: return null
        return loadById(selected.id, key)
    }

    private fun loadById(trackId: String, key: String): LyricsResult? {
        val responseJson = httpClient.newCall(
            request(
                "$API_BASE/track_v2?track_id=$trackId&media_type=track" +
                    "&aid=$AID&device_platform=web&channel=pc_web",
            ),
        ).execute().use { it.body?.string().orEmpty() }
        return LyricParsers.parseQishuiLyricResponse(responseJson, key)?.also {
            WjxFileLogger.i(TAG, "loaded ${it.lines.size} lines trackId=$trackId")
        }
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json,text/plain,*/*")
        .header("Referer", "https://www.qishui.com/")
        .build()

    private companion object {
        const val TAG = "QishuiLyricProvider"
        const val API_BASE = "https://api.qishui.com/luna/pc"
        const val AID = "386088"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

internal class NeteaseLyricProvider(
    private val httpClient: OkHttpClient,
) {
    fun load(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        val keyword = snapshot.searchKeyword() ?: return null
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
                .build(),
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
                    (0 until array.length()).mapNotNull {
                        array.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank)
                    }
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
                .build(),
        ).execute().use { it.body?.string().orEmpty() }
        val lrc = JSONObject(lyricJson).optJSONObject("lrc")?.optString("lyric").orEmpty()
        val lines = LyricParsers.parseLrcText(lrc)
        return lines.takeIf(List<LyricLine>::isNotEmpty)?.let { LyricsResult(key, it) }
    }
}

internal class KugouLyricProvider(
    private val httpClient: OkHttpClient,
) {
    fun load(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        val keyword = snapshot.searchKeyword() ?: return null
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val searchJson = httpClient.newCall(
            Request.Builder()
                .url("https://mobileservice.kugou.com/api/v3/search/song?format=json&keyword=$encoded&page=1&pagesize=5")
                .header("User-Agent", USER_AGENT)
                .build(),
        ).execute().use { it.body?.string().orEmpty() }
        val songs = JSONObject(searchJson).optJSONObject("data")?.optJSONArray("info") ?: return null
        val candidates = (0 until songs.length()).mapNotNull { index ->
            val song = songs.optJSONObject(index) ?: return@mapNotNull null
            val hash = song.optString("hash").takeIf(String::isNotBlank) ?: return@mapNotNull null
            OnlineSongCandidate(
                id = hash,
                title = song.optString("songname"),
                artists = listOfNotNull(song.optString("singername").takeIf(String::isNotBlank)),
                durationMs = song.optLong("duration").takeIf { it > 0L }?.times(1_000L) ?: 0L,
            )
        }
        val selected = OnlineLyricMatcher.selectBest(snapshot, candidates) ?: return null
        val durationSec = (snapshot.durationMs / 1_000L).takeIf { it > 0L }
            ?: (selected.durationMs / 1_000L).takeIf { it > 0L }
            ?: return null
        val candidatesJson = httpClient.newCall(
            Request.Builder()
                .url(
                    "https://krcs.kugou.com/search?ver=1&man=yes&client=pc" +
                        "&keyword=$encoded&duration=$durationSec&hash=${selected.id}",
                )
                .header("User-Agent", USER_AGENT)
                .build(),
        ).execute().use { it.body?.string().orEmpty() }
        val candidate = JSONObject(candidatesJson).optJSONArray("candidates")?.optJSONObject(0)
            ?: return null
        val id = candidate.optString("id").takeIf(String::isNotBlank) ?: return null
        val accessKey = candidate.optString("accesskey").takeIf(String::isNotBlank) ?: return null
        val downloadJson = httpClient.newCall(
            Request.Builder()
                .url("https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8")
                .header("User-Agent", USER_AGENT)
                .build(),
        ).execute().use { it.body?.string().orEmpty() }
        val content = JSONObject(downloadJson).optString("content").takeIf(String::isNotBlank)
            ?: return null
        val raw = String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        val lines = LyricParsers.parseLrcText(raw)
        return lines.takeIf(List<LyricLine>::isNotEmpty)?.let { LyricsResult(key, it) }
    }
}

private const val USER_AGENT = "Mozilla/5.0 SuperToyBoxLyrics/1.0"

private fun MusicPlaybackSnapshot.searchKeyword(): String? =
    listOf(title, artist).filter(String::isNotBlank).joinToString(" ").takeIf(String::isNotBlank)
