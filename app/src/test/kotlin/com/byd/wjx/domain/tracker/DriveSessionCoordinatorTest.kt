package com.byd.wjx.domain.tracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.data.repository.LastSessionRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.service.SessionPersistence
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DriveSessionCoordinatorTest {
    private lateinit var persistence: SessionPersistence

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        persistence = SessionPersistence(context).also { it.clear() }
    }

    @Test
    fun `session starts tracks deltas survives short idle and closes at threshold`() = runTest {
        val lastSession = LastSessionRepository()
        val tripRepository = mockk<TripRepository>()
        coEvery { tripRepository.getLastTripAvgConsumption() } returns 14.2
        val coordinator = DriveSessionCoordinator(
            persistence = persistence,
            lastSessionRepository = lastSession,
            tripRepository = tripRepository,
            applicationScope = backgroundScope,
        )
        coordinator.initialize(nowMs = 1_000L)
        runCurrent()

        val started = coordinator.onSample(
            nowMs = 2_000L,
            data = diParsData(
                soc = 80,
                powerState = 1,
                mileage = 100.0,
                totalElecConsumption = 1_000.0,
            ),
            trackerDriving = false,
        )
        assertEquals(DriveSessionTransition.STARTED, started.transition)
        assertEquals(2_000L, started.sessionStartedAt)
        assertEquals(0.0, started.tripDistanceKm)
        assertEquals(0.0, started.tripEnergyKwh)
        assertEquals(14.2, started.lastTripAverage)

        val moving = coordinator.onSample(
            nowMs = 4_000L,
            data = diParsData(
                soc = 78,
                powerState = 1,
                mileage = 101.25,
                totalElecConsumption = 1_000.35,
            ),
            trackerDriving = true,
        )
        assertEquals(1.25, moving.tripDistanceKm)
        assertEquals(0.35, moving.tripEnergyKwh!!, 0.0001)

        val grace = coordinator.onSample(
            nowMs = 13_999L,
            data = diParsData(soc = 77, powerState = 0, mileage = 101.25),
            trackerDriving = false,
        )
        assertEquals(DriveSessionTransition.NONE, grace.transition)
        assertEquals(2_000L, grace.sessionStartedAt)

        val ended = coordinator.onSample(
            nowMs = 14_000L,
            data = diParsData(soc = 77, powerState = 0, mileage = 101.25),
            trackerDriving = false,
        )
        assertEquals(DriveSessionTransition.ENDED, ended.transition)
        assertNull(ended.sessionStartedAt)
        assertNull(ended.tripDistanceKm)
        assertEquals(80, lastSession.snapshot()?.startSoc)
        assertEquals(77, lastSession.snapshot()?.endSoc)
    }

    @Test
    fun `fresh persisted session restores and stale session is discarded`() = runTest {
        val tripRepository = mockk<TripRepository>()
        coEvery { tripRepository.getLastTripAvgConsumption() } returns null
        val coordinator = DriveSessionCoordinator(
            persistence = persistence,
            lastSessionRepository = LastSessionRepository(),
            tripRepository = tripRepository,
            applicationScope = backgroundScope,
        )

        persistence.save(sessionStartedAt = 1_000L, lastActiveTs = 5_000L)
        val fresh = coordinator.initialize(nowMs = 14_999L)
        assertTrue(fresh.restored)
        assertEquals(1_000L, fresh.sessionStartedAt)

        persistence.save(sessionStartedAt = 1_000L, lastActiveTs = 5_000L)
        val stale = coordinator.initialize(nowMs = 15_000L)
        assertTrue(stale.discardedStale)
        assertNull(stale.sessionStartedAt)
        assertNull(persistence.load())
    }

    @Test
    fun `negative odometer and energy deltas remain unavailable`() = runTest {
        val tripRepository = mockk<TripRepository>()
        coEvery { tripRepository.getLastTripAvgConsumption() } returns 15.0
        val coordinator = DriveSessionCoordinator(
            persistence = persistence,
            lastSessionRepository = LastSessionRepository(),
            tripRepository = tripRepository,
            applicationScope = backgroundScope,
        )
        coordinator.initialize(nowMs = 1_000L)
        coordinator.onSample(
            nowMs = 2_000L,
            data = diParsData(
                powerState = 1,
                mileage = 200.0,
                totalElecConsumption = 2_000.0,
            ),
            trackerDriving = false,
        )

        val regressed = coordinator.onSample(
            nowMs = 3_000L,
            data = diParsData(
                powerState = 1,
                mileage = 199.5,
                totalElecConsumption = 1_999.8,
            ),
            trackerDriving = true,
        )
        assertNull(regressed.tripDistanceKm)
        assertNull(regressed.tripEnergyKwh)
    }
}
