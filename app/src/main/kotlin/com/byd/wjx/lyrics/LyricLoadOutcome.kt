package com.byd.wjx.lyrics

internal sealed interface LyricLoadOutcome {
    val lyrics: LyricsResult

    data class Found(override val lyrics: LyricsResult) : LyricLoadOutcome
    data class NotFound(override val lyrics: LyricsResult) : LyricLoadOutcome
    data class NetworkUnavailable(
        override val lyrics: LyricsResult,
        val message: String,
    ) : LyricLoadOutcome
}
