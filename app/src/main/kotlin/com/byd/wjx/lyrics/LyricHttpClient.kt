package com.byd.wjx.lyrics

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

internal fun OkHttpClient.forLyricLookup(): OkHttpClient = newBuilder()
    .connectTimeout(LYRIC_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(LYRIC_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(LYRIC_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .callTimeout(LYRIC_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .build()

private const val LYRIC_CONNECT_TIMEOUT_SECONDS = 5L
private const val LYRIC_READ_TIMEOUT_SECONDS = 8L
private const val LYRIC_CALL_TIMEOUT_SECONDS = 10L
