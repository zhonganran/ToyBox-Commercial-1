package com.byd.wjx.lyrics

import com.byd.wjx.log.WjxFileLogger
import okhttp3.OkHttpClient
internal class OnlineLyricSource(httpClient: OkHttpClient) {
    private val lyricHttpClient = httpClient.forLyricLookup()
    private val qishui = QishuiLyricProvider(lyricHttpClient)
    private val netease = NeteaseLyricProvider(lyricHttpClient)
    private val kugou = KugouLyricProvider(lyricHttpClient)
    fun load(snapshot: MusicPlaybackSnapshot, key: String): OnlineLyricLookupResult {
        val isQishui = QishuiMusic.isPackage(snapshot.packageName)
        val attempts = buildList {
            if (isQishui) add("qishui" to { qishui.load(snapshot, key) })
            add("netease" to { netease.load(snapshot, key) })
            add("kugou" to { kugou.load(snapshot, key) })
            if (!isQishui) add("qishui fallback" to { qishui.search(snapshot, key) })
        }
        var firstServiceFailure: String? = null
        attempts.forEach { (source, loader) ->
            when (val result = attemptOnlineLyricProvider(loader)) {
                is OnlineProviderAttempt.Found -> return OnlineLyricLookupResult.Found(result.lyrics)
                OnlineProviderAttempt.NotFound -> Unit
                is OnlineProviderAttempt.NetworkUnavailable -> {
                    WjxFileLogger.w(TAG, "$source lyrics network unavailable: ${result.message}")
                    return OnlineLyricLookupResult.NetworkUnavailable(result.message)
                }
                is OnlineProviderAttempt.ServiceFailure -> {
                    if (firstServiceFailure == null) firstServiceFailure = result.message
                    WjxFileLogger.w(TAG, "$source lyrics service failed: ${result.message}")
                }
            }
        }
        return firstServiceFailure
            ?.let(OnlineLyricLookupResult::NetworkUnavailable)
            ?: OnlineLyricLookupResult.NotFound
    }
    private companion object {
        const val TAG = "OnlineLyricSource"
    }
}
