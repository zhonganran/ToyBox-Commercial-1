package com.byd.wjx.lyrics

import com.byd.wjx.log.WjxFileLogger
import okhttp3.OkHttpClient

internal class OnlineLyricSource(httpClient: OkHttpClient) {
    private val qishui = QishuiLyricProvider(httpClient)
    private val netease = NeteaseLyricProvider(httpClient)
    private val kugou = KugouLyricProvider(httpClient)

    fun load(snapshot: MusicPlaybackSnapshot, key: String): LyricsResult? {
        val isQishui = QishuiMusic.isPackage(snapshot.packageName)
        if (isQishui) attempt("qishui") { qishui.load(snapshot, key) }?.let { return it }
        attempt("netease") { netease.load(snapshot, key) }?.let { return it }
        attempt("kugou") { kugou.load(snapshot, key) }?.let { return it }
        if (!isQishui) {
            return attempt("qishui fallback") { qishui.search(snapshot, key) }
        }
        return null
    }

    private inline fun attempt(source: String, block: () -> LyricsResult?): LyricsResult? =
        runCatching(block)
            .onFailure { WjxFileLogger.w(TAG, "$source lyrics failed: ${it.message}") }
            .getOrNull()

    private companion object {
        const val TAG = "OnlineLyricSource"
    }
}
