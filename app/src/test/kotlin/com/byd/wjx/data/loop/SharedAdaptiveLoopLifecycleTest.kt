package com.byd.wjx.data.loop

import app.cash.turbine.test
import com.byd.wjx.data.local.EnergyDataReader
import com.byd.wjx.data.local.dao.LastStateDao
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.diParsData
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedAdaptiveLoopLifecycleTest {

    @Test fun `emits ticks from parsReader on the schedule`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reader = object : ParsReader {
            override suspend fun fetch() =
                // powerState=0 (OFF) -> IDLE cadence (30s)
                diParsData(soc = 80, mileage = 100.0, voltage12v = 12.5, powerState = 0)
        }
        val lastState = mockk<LastStateDao>(relaxed = true)
        val energy = mockk<EnergyDataReader> { every { isAvailable() } returns true }
        val loop = SharedAdaptiveLoop(reader, lastState, energy, dispatcher)

        val job = loop.start(TestScope(dispatcher))
        try {
            loop.flow.test {
                advanceTimeBy(1)             // first fetch runs immediately
                val t1 = awaitItem()
                assertEquals(80, t1.soc)
                advanceTimeBy(30_000)        // idle cadence
                val t2 = awaitItem()
                assertEquals(80, t2.soc)
            }
        } finally {
            job.cancel()
        }
    }

    @Test fun `action flow emits at 300ms while regular flow stays adaptive`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var n = 0
        val reader = object : ParsReader {
            override suspend fun fetch() = diParsData(
                soc = ++n,
                mileage = 100.0,
                voltage12v = 12.5,
                powerState = 2,
                speed = 20,
            )
        }
        val lastState = mockk<LastStateDao>(relaxed = true)
        val energy = mockk<EnergyDataReader> { every { isAvailable() } returns true }
        val loop = SharedAdaptiveLoop(reader, lastState, energy, dispatcher)

        val job = loop.start(TestScope(dispatcher))
        try {
            loop.actionFlow.test {
                advanceTimeBy(1)
                assertEquals(1, awaitItem().soc)
                advanceTimeBy(300)
                assertEquals(2, awaitItem().soc)
                advanceTimeBy(300)
                assertEquals(3, awaitItem().soc)
            }
        } finally {
            job.cancel()
        }
    }

    @Test fun `fetch time is included in action cadence instead of added after it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var reads = 0
        val reader = object : ParsReader {
            override suspend fun fetch(): com.byd.wjx.data.remote.DiParsData {
                delay(200)
                return diParsData(
                    soc = ++reads,
                    mileage = 100.0,
                    voltage12v = 12.5,
                    powerState = 2,
                    speed = 20,
                )
            }
        }
        val loop = SharedAdaptiveLoop(
            reader,
            mockk(relaxed = true),
            mockk<EnergyDataReader> { every { isAvailable() } returns true },
            dispatcher,
        )

        val job = loop.start(TestScope(dispatcher))
        try {
            loop.actionFlow.test {
                advanceTimeBy(200)
                assertEquals(1, awaitItem().soc)
                advanceTimeBy(300)
                assertEquals(2, awaitItem().soc)
            }
        } finally {
            job.cancel()
        }
    }

    @Test fun `action state transition forces full snapshot without waiting for idle cadence`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var fullReads = 0
        val reader = object : ParsReader {
            override suspend fun fetch() = diParsData(
                soc = 80,
                mileage = 100.0,
                voltage12v = 12.5,
                powerState = if (fullReads++ == 0) 0 else 2,
                speed = if (fullReads == 1) 0 else 20,
            )

            override suspend fun fetchAction() = diParsData(
                soc = 80,
                mileage = 100.0,
                voltage12v = 12.5,
                powerState = 2,
                speed = 20,
            )
        }
        val lastState = mockk<LastStateDao>(relaxed = true)
        val energy = mockk<EnergyDataReader> { every { isAvailable() } returns true }
        val loop = SharedAdaptiveLoop(reader, lastState, energy, dispatcher)

        val job = loop.start(TestScope(dispatcher))
        try {
            loop.flow.test {
                advanceTimeBy(1)
                assertEquals(0, awaitItem().powerState)

                // The 300 ms action sample sees DRIVE and forces a full read immediately.
                advanceTimeBy(300)
                assertEquals(2, awaitItem().powerState)
                assertEquals(2, fullReads)
            }
        } finally {
            job.cancel()
        }
    }

    @Test fun `start is idempotent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reader = object : ParsReader {
            override suspend fun fetch() = null
        }
        val lastState = mockk<LastStateDao>(relaxed = true)
        val energy = mockk<EnergyDataReader>(relaxed = true)
        val loop = SharedAdaptiveLoop(reader, lastState, energy, dispatcher)
        val scope = TestScope(dispatcher)
        val j1 = loop.start(scope)
        val j2 = loop.start(scope)
        assertEquals(j1, j2)
        j1.cancel()
    }

    @Test fun `stop clears connected state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reader = object : ParsReader {
            override suspend fun fetch() = diParsData(
                soc = 80,
                mileage = 100.0,
                voltage12v = 12.5,
                powerState = 2,
            )
        }
        val loop = SharedAdaptiveLoop(
            reader,
            mockk(relaxed = true),
            mockk<EnergyDataReader> { every { isAvailable() } returns true },
            dispatcher,
        )

        loop.start(TestScope(dispatcher))
        advanceTimeBy(1)
        assertTrue(loop.connected.value)

        loop.stop()
        runCurrent()
        assertFalse(loop.connected.value)
    }

    @Test fun `persistence failure does not stop live polling`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var reads = 0
        val reader = object : ParsReader {
            override suspend fun fetch() = diParsData(
                soc = ++reads,
                mileage = 100.0,
                voltage12v = 12.5,
                powerState = 2,
                speed = 20,
            )
        }
        val lastState = mockk<LastStateDao>(relaxed = true)
        coEvery {
            lastState.updateSnapshot(any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("disk temporarily unavailable")
        val energy = mockk<EnergyDataReader> { every { isAvailable() } returns true }
        val loop = SharedAdaptiveLoop(reader, lastState, energy, dispatcher)

        val job = loop.start(TestScope(dispatcher))
        try {
            loop.flow.test {
                advanceTimeBy(1)
                val firstSoc = awaitItem().soc
                assertEquals(1, firstSoc)
                advanceTimeBy(1_000)
                val nextSoc = awaitItem().soc
                assertTrue((nextSoc ?: 0) > (firstSoc ?: 0))
            }
        } finally {
            job.cancel()
        }
    }
}
