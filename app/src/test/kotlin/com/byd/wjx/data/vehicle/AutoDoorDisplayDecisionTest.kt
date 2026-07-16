package com.byd.wjx.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoDoorDisplayDecisionTest {

    @Test
    fun `only 100 percent is fully open`() {
        val almostOpen = resolveAutoDoorDisplayDecision(action = 1, percent = 99, previousMotion = null)
        val fullyOpen = resolveAutoDoorDisplayDecision(action = 1, percent = 100, previousMotion = null)

        assertEquals("半开 99%", almostOpen.statusText)
        assertEquals(1, almostOpen.nextCommand)
        assertEquals("已开", fullyOpen.statusText)
        assertEquals(2, fullyOpen.nextCommand)
        assertNull(fullyOpen.rememberedMotion)
    }

    @Test
    fun `zero through two percent is fully closed`() {
        listOf(0, 1, 2).forEach { percent ->
            val decision = resolveAutoDoorDisplayDecision(action = 1, percent = percent, previousMotion = null)
            assertEquals("已关", decision.statusText)
            assertEquals(1, decision.nextCommand)
            assertNull(decision.rememberedMotion)
        }

        assertEquals(
            "半开 3%",
            resolveAutoDoorDisplayDecision(action = 1, percent = 3, previousMotion = null).statusText,
        )
    }

    @Test
    fun `moving door pauses and remembers its direction`() {
        val opening = resolveAutoDoorDisplayDecision(action = 2, percent = 45, previousMotion = null)
        val closing = resolveAutoDoorDisplayDecision(action = 3, percent = 45, previousMotion = null)

        assertEquals(3, opening.nextCommand)
        assertEquals(AutoDoorMotionDirection.OPENING, opening.rememberedMotion)
        assertEquals(3, closing.nextCommand)
        assertEquals(AutoDoorMotionDirection.CLOSING, closing.rememberedMotion)
    }

    @Test
    fun `paused intermediate door reverses its previous direction`() {
        val pausedOpening = resolveAutoDoorDisplayDecision(
            action = 1,
            percent = 45,
            previousMotion = AutoDoorMotionDirection.OPENING,
        )
        val pausedClosing = resolveAutoDoorDisplayDecision(
            action = 1,
            percent = 45,
            previousMotion = AutoDoorMotionDirection.CLOSING,
        )

        assertEquals(2, pausedOpening.nextCommand)
        assertEquals(1, pausedClosing.nextCommand)
    }
}
