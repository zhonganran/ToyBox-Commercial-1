package com.byd.wjx.lyrics

import java.io.IOException
import java.net.UnknownHostException

internal sealed interface OnlineLyricLookupResult {
    data class Found(val lyrics: LyricsResult) : OnlineLyricLookupResult
    data object NotFound : OnlineLyricLookupResult
    data class NetworkUnavailable(val message: String) : OnlineLyricLookupResult
}

internal sealed interface OnlineProviderAttempt {
    data class Found(val lyrics: LyricsResult) : OnlineProviderAttempt
    data object NotFound : OnlineProviderAttempt
    data class NetworkUnavailable(val message: String) : OnlineProviderAttempt
    data class ServiceFailure(val message: String) : OnlineProviderAttempt
}

internal inline fun attemptOnlineLyricProvider(
    block: () -> LyricsResult?,
): OnlineProviderAttempt = try {
    block()?.let(OnlineProviderAttempt::Found) ?: OnlineProviderAttempt.NotFound
} catch (error: IOException) {
    if (error.hasDnsCause()) {
        OnlineProviderAttempt.NetworkUnavailable("DNS 解析失败")
    } else {
        OnlineProviderAttempt.ServiceFailure("网络请求失败")
    }
} catch (_: Throwable) {
    OnlineProviderAttempt.ServiceFailure("歌词服务异常")
}

internal fun lyricFailureMessage(error: Throwable): String = when {
    error.hasDnsCause() -> "DNS 解析失败"
    error is IOException -> "网络请求失败"
    else -> "歌词服务异常"
}

private fun Throwable.hasDnsCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is UnknownHostException) return true
        val next = current.cause
        if (next === current) break
        current = next
    }
    return false
}
