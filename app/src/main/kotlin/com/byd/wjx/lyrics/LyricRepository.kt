package com.byd.wjx.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class LyricRepository(
    httpClient: OkHttpClient,
    currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val localSource = LocalLyricFileSource()
    private val onlineSource = OnlineLyricSource(httpClient)
    private val cache = LyricLookupCache(currentTimeMillis)

    suspend fun load(snapshot: MusicPlaybackSnapshot): LyricsResult = loadOutcome(snapshot).lyrics

    internal suspend fun loadOutcome(snapshot: MusicPlaybackSnapshot): LyricLoadOutcome =
        withContext(Dispatchers.IO) {
            val key = snapshot.lyricKey()
            cache.found(key)?.let { return@withContext LyricLoadOutcome.Found(it) }
            if (cache.isTemporarilyNotFound(key)) {
                return@withContext LyricLoadOutcome.NotFound(LyricsResult(key))
            }
            val result = snapshot.timedLyrics
                ?.takeIf(List<LyricLine>::isNotEmpty)
                ?.let { LyricsResult(key, it) }
                ?: LyricParsers.parseBydMetadataLyrics(snapshot.metadataLyrics, key)
                ?: localSource.load(snapshot, key)
            if (result != null) {
                cache.put(key, result)
                return@withContext LyricLoadOutcome.Found(result)
            }
            when (val online = onlineSource.load(snapshot, key)) {
                is OnlineLyricLookupResult.Found -> {
                    cache.put(key, online.lyrics)
                    LyricLoadOutcome.Found(online.lyrics)
                }
                OnlineLyricLookupResult.NotFound -> {
                    cache.rememberNotFound(key)
                    LyricLoadOutcome.NotFound(LyricsResult(key))
                }
                is OnlineLyricLookupResult.NetworkUnavailable -> LyricLoadOutcome.NetworkUnavailable(
                    lyrics = LyricsResult(key),
                    message = online.message,
                )
            }
        }
}
