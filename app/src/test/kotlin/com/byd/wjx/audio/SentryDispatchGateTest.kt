package com.byd.wjx.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryDispatchGateTest {
    @Test
    fun `gate is single flight and throttles repeated external broadcasts`() {
        var now = 20_000L
        val gate = SentryDispatchGate(clock = { now }, minimumIntervalMs = 10_000L)

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertFalse(gate.tryAcquire())

        now += 10_000L
        assertTrue(gate.tryAcquire())
    }
}
