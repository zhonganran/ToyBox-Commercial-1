package com.byd.wjx.data.loop

import org.junit.Assert.assertEquals
import org.junit.Test

class CadenceConfigTest {
    @Test fun `default cadences match spec`() {
        val c = CadenceConfig.default()
        assertEquals(1_000L, c.intervalFor(LoopState.DRIVE))
        assertEquals(3_000L, c.intervalFor(LoopState.CHARGE))
        assertEquals(3_000L, c.intervalFor(LoopState.PARKED))
        assertEquals(30_000L, c.intervalFor(LoopState.IDLE))
        assertEquals(300L, c.actionMs)
    }

    @Test fun `max poll interval cap is 60s`() {
        assertEquals(60_000L, CadenceConfig.MAX_POLL_INTERVAL_MS)
    }
}
