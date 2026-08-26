package com.byd.wjx.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class LyricRepository(
    httpClient: OkHttpClient,
) {
    private val localSource = LocalLyricFileSource()
    private val onlineSource = OnlineLyricSource(httpClient)
    private val cache = linkedMapOf<String, LyricsResult>()

    suspend fun load(snapshot: MusicPlaybackSnapshot): LyricsResult = withContext(Dispatchers.IO) {
        val key = snapshot.songKey()
        cached(key)?.let { return@withContext it }
        val result = snapshot.timedLyrics
            ?.takeIf(List<LyricLine>::isNotEmpty)
            ?.let { LyricsResult(key, it) }
            ?: LyricParsers.parseBydMetadataLyrics(snapshot.metadataLyrics, key)
            ?: localSource.load(snapshot, key)
            ?: onlineSource.load(snapshot, key)
            ?: LyricsResult(key)
        if (result.lines.isNotEmpty()) putCache(key, result)
        result
    }

    private fun cached(key: String): LyricsResult? = synchronized(cache) { cache[key] }

    private fun putCache(key: String, result: LyricsResult) = synchronized(cache) {
        if (cache.size >= MAX_CACHE_SIZE) {
            cache.keys.firstOrNull()?.let(cache::remove)
        }
        cache[key] = result
    }

    companion object {
        private const val MAX_CACHE_SIZE = 24

        internal fun parseQishuiLyricResponse(raw: String, key: String): LyricsResult? =
            LyricParsers.parseQishuiLyricResponse(raw, key)

        internal fun parseQishuiLyricContent(raw: String): List<LyricLine> =
            LyricParsers.parseQishuiLyricContent(raw)

        internal fun parseQishuiSearchCandidates(raw: String): List<OnlineSongCandidate> =
            LyricParsers.parseQishuiSearchCandidates(raw)

        fun parseBydMetadataLyrics(raw: String?, key: String): LyricsResult? =
            LyricParsers.parseBydMetadataLyrics(raw, key)

        fun parseLrcText(raw: String): List<LyricLine> = LyricParsers.parseLrcText(raw)
    }
}
