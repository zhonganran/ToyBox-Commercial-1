package com.byd.wjx.data.loop

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionPollingBudgetTest {
    @Test
    fun `small action snapshots keep the responsive base cadence`() {
        assertEquals(300L, ActionPollingBudget.effectiveIntervalMs(300L, 4))
        assertEquals(300L, ActionPollingBudget.effectiveIntervalMs(300L, 36))
    }

    @Test
    fun `large action snapshots are capped by field read budget`() {
        assertEquals(500L, ActionPollingBudget.effectiveIntervalMs(300L, 60))
        assertEquals(842L, ActionPollingBudget.effectiveIntervalMs(300L, 101))
    }

    @Test
    fun `slow fetches always leave autoservice an idle window`() {
        assertEquals(
            ActionPollingBudget.MIN_IDLE_AFTER_FETCH_MS,
            ActionPollingBudget.delayAfterFetchMs(
                baseIntervalMs = 300L,
                requestedFieldCount = 101,
                fetchElapsedMs = 900L,
            ),
        )
    }

    @Test
    fun `sleeping vehicle cadence remains the upper constraint`() {
        assertEquals(1_000L, ActionPollingBudget.effectiveIntervalMs(1_000L, 101))
    }
}
