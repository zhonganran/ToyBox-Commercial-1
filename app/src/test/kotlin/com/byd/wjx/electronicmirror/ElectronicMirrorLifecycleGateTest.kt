package com.byd.wjx.electronicmirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorLifecycleGateTest {
    @Test
    fun `advancing lifecycle invalidates delayed cleanup from previous service`() {
        val gate = ElectronicMirrorLifecycleGate()
        val oldService = gate.advance()
        val oldCleanup = gate.advance()
        val newService = gate.advance()

        assertFalse(gate.isCurrent(oldService))
        assertFalse(gate.isCurrent(oldCleanup))
        assertTrue(gate.isCurrent(newService))
    }
}
