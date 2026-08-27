package com.byd.wjx.ui.map

import com.byd.wjx.data.dashcam.DashcamVideoClip
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import kotlin.math.roundToInt

internal data class DashcamPlaybackTarget(
    val tripId: Long,
    val clipUri: String? = null,
    val positionMs: Long = 0L,
    val timestamp: Long? = null,
)

internal data class DashcamRouteVideoPoint(
    val routePoint: TripPointEntity,
    val target: DashcamPlaybackTarget,
)

internal object DashcamTripMatcher {
    fun match(
        trips: List<TripEntity>,
        clips: List<DashcamVideoClip>,
        now: Long = System.currentTimeMillis(),
    ): Map<Long, List<DashcamVideoClip>> {
        if (trips.isEmpty() || clips.isEmpty()) return emptyMap()

        val sortedClips = clips.sortedWith(compareBy(DashcamVideoClip::startTs, DashcamVideoClip::displayName))
        val maxDuration = sortedClips.maxOf { clip ->
            (clip.endTs - clip.startTs).coerceAtLeast(0L)
        }
        return buildMap {
            trips.forEach { trip ->
                val tripEnd = trip.endTs ?: now
                if (tripEnd <= trip.startTs) return@forEach
                val earliestPossibleStart = trip.startTs - maxDuration
                val fromIndex = sortedClips.firstIndexAtOrAfter(earliestPossibleStart)
                val toIndex = sortedClips.firstIndexAtOrAfter(tripEnd)
                val matches = buildList {
                    for (index in fromIndex until toIndex) {
                        val clip = sortedClips[index]
                        if (clip.overlaps(trip.startTs, tripEnd)) add(clip)
                    }
                }
                if (matches.isNotEmpty()) put(trip.id, matches)
            }
        }
    }

    private fun List<DashcamVideoClip>.firstIndexAtOrAfter(timestamp: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high).ushr(1)
            if (this[middle].startTs < timestamp) low = middle + 1 else high = middle
        }
        return low
    }
}

internal object DashcamRouteTimeline {
    fun targetAt(
        route: TripRoute,
        clips: List<DashcamVideoClip>,
        timestamp: Long,
    ): DashcamPlaybackTarget? {
        val clip = clips.firstOrNull { timestamp >= it.startTs && timestamp < it.endTs }
            ?: return null
        return targetForClip(route.trip.id, clip, timestamp)
    }

    fun markerPoints(
        route: TripRoute,
        clips: List<DashcamVideoClip>,
        maxMarkers: Int = DEFAULT_MAX_MARKERS,
    ): List<DashcamRouteVideoPoint> {
        if (route.points.isEmpty() || clips.isEmpty() || maxMarkers <= 0) return emptyList()
        val tripEnd = route.trip.endTs ?: route.points.last().timestamp
        val candidates = clips.asSequence()
            .sortedWith(compareBy(DashcamVideoClip::startTs, DashcamVideoClip::displayName))
            .mapNotNull { clip ->
                val timestamp = maxOf(route.trip.startTs, clip.startTs)
                if (timestamp > tripEnd || timestamp >= clip.endTs) return@mapNotNull null
                val routePoint = route.points.nearestByTimestamp(timestamp) ?: return@mapNotNull null
                DashcamRouteVideoPoint(
                    routePoint = routePoint,
                    target = targetForClip(route.trip.id, clip, timestamp),
                )
            }
            .distinctBy { point ->
                Triple(point.routePoint.timestamp, point.routePoint.lat, point.routePoint.lon)
            }
            .toList()
        return candidates.evenlySampled(maxMarkers)
    }

    private fun targetForClip(
        tripId: Long,
        clip: DashcamVideoClip,
        timestamp: Long,
    ): DashcamPlaybackTarget {
        val maxPosition = (clip.endTs - clip.startTs - 1L).coerceAtLeast(0L)
        return DashcamPlaybackTarget(
            tripId = tripId,
            clipUri = clip.uri,
            positionMs = (timestamp - clip.startTs).coerceIn(0L, maxPosition),
            timestamp = timestamp,
        )
    }

    private fun List<TripPointEntity>.nearestByTimestamp(timestamp: Long): TripPointEntity? {
        if (isEmpty()) return null
        val exactOrInsertion = binarySearchBy(timestamp) { point -> point.timestamp }
        if (exactOrInsertion >= 0) return this[exactOrInsertion]
        val insertion = -exactOrInsertion - 1
        val before = getOrNull(insertion - 1)
        val after = getOrNull(insertion)
        return when {
            before == null -> after
            after == null -> before
            timestamp - before.timestamp <= after.timestamp - timestamp -> before
            else -> after
        }
    }

    private fun <T> List<T>.evenlySampled(limit: Int): List<T> {
        if (size <= limit) return this
        if (limit == 1) return listOf(first())
        return List(limit) { sampleIndex ->
            val sourceIndex = (sampleIndex.toDouble() * lastIndex / (limit - 1)).roundToInt()
            this[sourceIndex]
        }.distinct()
    }

    private const val DEFAULT_MAX_MARKERS = 24
}
