package com.byd.wjx.data.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestOperationGateTest {
    @Test fun `older completion cannot clear newer operation`() {
        val gate = LatestOperationGate()
        val first = gate.begin()
        val second = gate.begin()

        gate.finish(first)

        assertTrue(gate.pending)
        assertFalse(gate.isLatest(first))
        assertTrue(gate.isLatest(second))

        gate.finish(second)
        assertFalse(gate.pending)
    }

    @Test fun `cancel invalidates every outstanding token`() {
        val gate = LatestOperationGate()
        val token = gate.begin()

        gate.cancelAll()

        assertFalse(gate.pending)
        assertFalse(gate.isLatest(token))
        gate.finish(token)
        assertFalse(gate.pending)
    }
}
