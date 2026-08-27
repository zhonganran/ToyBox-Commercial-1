package com.byd.wjx.ui.map

import com.byd.wjx.data.dashcam.DashcamVideoClip
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashcamTripMatcherTest {
    @Test
    fun `unsorted clips are matched to each trip in timeline order`() {
        val clips = listOf(
            clip("late", 300L, 400L),
            clip("early", 100L, 200L),
            clip("middle", 210L, 290L),
        )

        val result = DashcamTripMatcher.match(
            trips = listOf(
                trip(1L, 150L, 250L),
                trip(2L, 350L, 450L),
            ),
            clips = clips,
        )

        assertEquals(listOf("early", "middle"), result.getValue(1L).map(DashcamVideoClip::uri))
        assertEquals(listOf("late"), result.getValue(2L).map(DashcamVideoClip::uri))
    }

    @Test
    fun `ongoing and invalid trips are handled without scanning unrelated clips`() {
        val clips = listOf(
            clip("old", 10L, 20L),
            clip("current", 500L, 700L),
        )

        val result = DashcamTripMatcher.match(
            trips = listOf(
                trip(1L, 550L, null),
                trip(2L, 900L, 800L),
            ),
            clips = clips,
            now = 650L,
        )

        assertEquals(listOf("current"), result.getValue(1L).map(DashcamVideoClip::uri))
        assertTrue(2L !in result)
    }

    @Test
    fun `route timestamps resolve to the covering clip and exact playback position`() {
        val route = route(
            trip = trip(7L, 1_000L, 8_000L),
            timestamps = listOf(1_000L, 3_000L, 5_000L, 7_000L),
        )
        val clips = listOf(
            clip("first", 0L, 4_000L),
            clip("second", 4_500L, 6_500L),
        )

        val target = DashcamRouteTimeline.targetAt(route, clips, 5_500L)

        assertEquals(7L, target?.tripId)
        assertEquals("second", target?.clipUri)
        assertEquals(1_000L, target?.positionMs)
        assertEquals(5_500L, target?.timestamp)
        assertNull(DashcamRouteTimeline.targetAt(route, clips, 4_200L))
    }

    @Test
    fun `video markers use nearest route points and respect marker limit`() {
        val route = route(
            trip = trip(9L, 1_000L, 8_000L),
            timestamps = listOf(1_000L, 3_000L, 5_000L, 7_000L),
        )
        val clips = listOf(
            clip("first", 0L, 4_000L),
            clip("second", 4_500L, 6_500L),
            clip("third", 6_700L, 7_900L),
        )

        val points = DashcamRouteTimeline.markerPoints(route, clips, maxMarkers = 2)

        assertEquals(2, points.size)
        assertEquals(1_000L, points.first().routePoint.timestamp)
        assertEquals("first", points.first().target.clipUri)
        assertEquals(7_000L, points.last().routePoint.timestamp)
        assertEquals("third", points.last().target.clipUri)
    }

    private fun trip(id: Long, startTs: Long, endTs: Long?): TripEntity = TripEntity(
        id = id,
        startTs = startTs,
        endTs = endTs,
    )

    private fun clip(uri: String, startTs: Long, endTs: Long): DashcamVideoClip = DashcamVideoClip(
        uri = uri,
        displayName = "$uri.mp4",
        startTs = startTs,
        endTs = endTs,
    )

    private fun route(trip: TripEntity, timestamps: List<Long>): TripRoute = TripRoute(
        trip = trip,
        points = timestamps.mapIndexed { index, timestamp ->
            TripPointEntity(
                id = index.toLong() + 1L,
                tripId = trip.id,
                timestamp = timestamp,
                lat = 22.5 + index * 0.001,
                lon = 114.0 + index * 0.001,
            )
        },
        kwhPer100km = null,
    )
}
