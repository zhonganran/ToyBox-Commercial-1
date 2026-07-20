package com.byd.wjx.helper

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoserviceTransactionGateTest {
    @Test
    fun `concurrent callers enter autoservice transaction gate one at a time`() {
        val callers = 8
        val start = CountDownLatch(1)
        val done = CountDownLatch(callers)
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)

        repeat(callers) {
            Thread {
                start.await()
                withAutoserviceTransactionLock {
                    val current = active.incrementAndGet()
                    maxActive.accumulateAndGet(current, ::maxOf)
                    Thread.sleep(10L)
                    active.decrementAndGet()
                }
                done.countDown()
            }.start()
        }

        start.countDown()

        assertTrue(done.await(5L, TimeUnit.SECONDS))
        assertEquals(1, maxActive.get())
    }
}
