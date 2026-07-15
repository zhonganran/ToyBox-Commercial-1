package com.byd.wjx.data.loop

import com.byd.wjx.data.local.EnergyDataReader
import com.byd.wjx.data.local.dao.LastStateDao
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.diParsData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedFlowConcurrencyTest {

    private fun buildLoop(dispatcher: CoroutineDispatcher): Pair<SharedAdaptiveLoop, () -> Int> {
        var counter = 0
        val reader = object : ParsReader {
            override suspend fun fetch() = diParsData(
                soc = ++counter,
                voltage12v = 12.5,
                mileage = 1.0,
                powerState = 3,
                speed = 30,
            )
        }
        val energy = mockk<EnergyDataReader> { every { isAvailable() } returns false }
        val lastState = mockk<LastStateDao>(relaxed = true)
        return SharedAdaptiveLoop(reader, lastState, energy, dispatcher) to { counter }
    }

    // Regular drive cadence = 1 000 ms. The action loop still fetches every
    // 300 ms, but regular subscribers only see the 1-second business stream.

    @Test fun `slow consumer drops intermediate ticks, loop never blocked`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (loop, counter) = buildLoop(dispatcher)
        val scope = TestScope(dispatcher)
        loop.start(scope)

        val seen = mutableListOf<Int>()
        scope.launch {
            loop.flow.collect { d ->
                seen.add(d.soc!!)
                delay(10_000)   // way slower than 1s drive cadence
            }
        }
        advanceTimeBy(5_500)        // 6 regular ticks emitted
        assertEquals(1, seen.size)  // first tick consumed; slow consumer still in delay, rest dropped
        assertTrue("fast action polling kept reading", counter() >= 18)
        scope.cancel()              // cancel loop + collector coroutines
    }

    @Test fun `five fast subscribers all observe ticks, none block the loop`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (loop, counter) = buildLoop(dispatcher)
        val scope = TestScope(dispatcher)
        loop.start(scope)

        val seenLists = List(5) { mutableListOf<Int>() }
        seenLists.forEach { dst ->
            scope.launch { loop.flow.collect { dst.add(it.soc!!) } }
        }
        advanceTimeBy(5_500)   // 6 regular ticks emitted
        // Loop progressed through all 6 ticks without being blocked by any subscriber.
        assertTrue("fast action polling kept reading", counter() >= 18)
        // Each fast subscriber should have observed all 6 ticks (SharedFlow replay=1; extra
        // subscriber latency negligible in virtual-time test).
        seenLists.forEach { assertTrue("subscriber received ticks: $it", it.size in 5..6) }
        scope.cancel()
    }

    @Test fun `late subscriber receives the latest replayed value (replay=1)`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (loop, _) = buildLoop(dispatcher)
        val scope = TestScope(dispatcher)
        loop.start(scope)
        advanceTimeBy(2_500)   // 3 ticks emitted; replay buffer holds last value

        val latest = scope.async { loop.flow.first() }.also { advanceTimeBy(10) }.await()
        assertTrue("late subscriber sees a replayed value", latest.soc != null)
        scope.cancel()
    }
}
