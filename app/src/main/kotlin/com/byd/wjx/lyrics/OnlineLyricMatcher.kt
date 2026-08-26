package com.byd.wjx.lyrics

import kotlin.math.abs

internal object OnlineLyricMatcher {
    fun selectBest(
        snapshot: MusicPlaybackSnapshot,
        candidates: List<OnlineSongCandidate>,
    ): OnlineSongCandidate? {
        val queryArtists = MusicIdentity.normalizedArtists(snapshot.artist)
        if (queryArtists.isEmpty() && snapshot.durationMs <= 0L) return null
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
        return score.takeIf { it >= if (queryArtists.isNotEmpty()) 120 else 100 }
    }

    private fun comparableTitle(value: String): String =
        MusicIdentity.normalizedTitle(value).filter(Char::isLetterOrDigit)
}
