package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorDecisionTest {
    private fun input(
        turnSignal: Int? = 1,
        speedKmh: Float? = 40f,
        reverse: Boolean = false,
        thresholdKmh: Int = 20,
        ageMs: Long = 0L,
    ) = ElectronicMirrorInput(turnSignal, speedKmh, reverse, thresholdKmh, ageMs)

    @Test fun `left and right turn signals select their matching camera`() {
        assertEquals(ElectronicMirrorSide.LEFT, decideElectronicMirror(input(turnSignal = 2)).show)
        assertEquals(ElectronicMirrorSide.RIGHT, decideElectronicMirror(input(turnSignal = 4)).show)
    }

    @Test fun `reverse gear closes the camera pipeline`() {
        val decision = decideElectronicMirror(input(turnSignal = 2, reverse = true))
        assertEquals(ElectronicMirrorSide.NONE, decision.show)
        assertFalse(decision.cameraWarm)
    }

    @Test fun `hazard off unknown and stale telemetry never show a side`() {
        assertEquals(ElectronicMirrorSide.NONE, decideElectronicMirror(input(turnSignal = 1)).show)
        assertEquals(ElectronicMirrorSide.NONE, decideElectronicMirror(input(turnSignal = 6)).show)
        assertEquals(ElectronicMirrorSide.NONE, decideElectronicMirror(input(turnSignal = 9)).show)
        assertEquals(
            ElectronicMirrorSide.NONE,
            decideElectronicMirror(input(turnSignal = 2, ageMs = ELECTRONIC_MIRROR_WATCHDOG_MS + 1)).show,
        )
    }

    @Test fun `speed threshold controls showing while hysteresis keeps camera warm`() {
        assertEquals(ElectronicMirrorSide.NONE, decideElectronicMirror(input(turnSignal = 2, speedKmh = 19f)).show)
        assertEquals(ElectronicMirrorSide.LEFT, decideElectronicMirror(input(turnSignal = 2, speedKmh = 20f)).show)
        assertTrue(decideElectronicMirror(input(speedKmh = 15f)).cameraWarm)
        assertFalse(decideElectronicMirror(input(speedKmh = 14.9f)).cameraWarm)
    }

    @Test fun `arming requires feature speed and a non reverse gear`() {
        assertFalse(electronicMirrorArmed(false, gear = 4, speedKmh = 60, thresholdKmh = 20))
        assertTrue(electronicMirrorArmed(true, gear = 4, speedKmh = 15, thresholdKmh = 20))
        assertFalse(electronicMirrorArmed(true, gear = 2, speedKmh = 60, thresholdKmh = 20))
        assertFalse(electronicMirrorArmed(true, gear = 4, speedKmh = null, thresholdKmh = 20))
    }

    @Test fun `telemetry gate retains last valid sample and closes failed channel`() {
        val gate = ElectronicMirrorTelemetryGate().apply { reset(1_000L) }
        val valid = ElectronicMirrorSample(turnSignal = 2, speedKmh = 40f, gear = 4)
        val first = gate.onSample(valid, 1_100L)
        assertEquals(valid, first.lastValid)
        assertEquals(0L, first.ageMs)
        assertFalse(first.mustClose)

        val missingField = gate.onSample(
            ElectronicMirrorSample(turnSignal = 2, speedKmh = 40f, gear = null),
            1_500L,
        )
        assertEquals(valid, missingField.lastValid)
        assertEquals(400L, missingField.ageMs)
        assertFalse(missingField.mustClose)

        assertFalse(gate.onSample(null, 1_600L).mustClose)
        assertTrue(gate.onSample(null, 1_750L).mustClose)
    }

    @Test fun `incomplete samples are invalid`() {
        assertFalse(ElectronicMirrorSample(null, 1f, 4).isValid)
        assertFalse(ElectronicMirrorSample(2, null, 4).isValid)
        assertFalse(ElectronicMirrorSample(2, 1f, null).isValid)
        assertTrue(ElectronicMirrorSample(2, 1f, 4).isValid)
        assertNull(ElectronicMirrorTelemetryGate().apply { reset(0L) }.onSample(null, 1L).lastValid)
    }

    @Test fun `only non disruptive cluster overlay displays are selected`() {
        val overlayRank = electronicMirrorOverlayDisplayRank("叠加视图 #1")
        val fissionRank = electronicMirrorOverlayDisplayRank("fission_1")
        val projectionRank = electronicMirrorOverlayDisplayRank("XDJAScreenProjection_1")
        val secondaryProjectionRank = electronicMirrorOverlayDisplayRank("XDJAScreenProjection_0")

        assertTrue(overlayRank > fissionRank)
        assertTrue(fissionRank > projectionRank)
        assertTrue(projectionRank > secondaryProjectionRank)
        assertTrue(secondaryProjectionRank > 0)
        assertEquals(0, electronicMirrorOverlayDisplayRank("Built-in Screen"))
    }
}
