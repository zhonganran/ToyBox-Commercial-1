package com.byd.wjx.camera.blindspot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindSpotDecisionTest {
    @Test
    fun `left and right blinkers select their own cameras inside speed range`() {
        assertEquals(setOf(BlindSpotSide.LEFT), decision(blink = 2).show)
        assertEquals(setOf(BlindSpotSide.RIGHT), decision(blink = 4).show)
    }

    @Test
    fun `hazard stale data and out of range speed never show a side`() {
        assertTrue(decision(blink = 6).show.isEmpty())
        assertTrue(decision(blink = 2, telemetryAgeMs = 751L).show.isEmpty())
        assertTrue(decision(blink = 2, speed = 9f).show.isEmpty())
        assertTrue(decision(blink = 2, speed = 121f).show.isEmpty())
    }

    @Test
    fun `reverse gear closes every side and prevents camera warming`() {
        val result = decision(blink = 2, reverse = true)

        assertTrue(result.show.isEmpty())
        assertFalse(result.cameraWarm)
    }

    @Test
    fun `automatic mode only arms and warms in drive gear`() {
        assertFalse(blindSpotArmed(true, 1, 0, 0, 120))
        assertFalse(blindSpotArmed(true, 3, 0, 0, 120))
        assertTrue(blindSpotArmed(true, BLIND_SPOT_GEAR_DRIVE, 0, 0, 120))
        assertFalse(decision(blink = 2, speed = 0f, forward = false, minSpeed = 0).cameraWarm)
        assertTrue(decision(blink = 2, speed = 0f, forward = true, minSpeed = 0).cameraWarm)
    }

    @Test
    fun `warm hysteresis covers both edges of configured speed range`() {
        assertTrue(decision(blink = 2, speed = 5f).cameraWarm)
        assertFalse(decision(blink = 2, speed = 4.9f).cameraWarm)
        assertTrue(decision(blink = 2, speed = 125f).cameraWarm)
        assertFalse(decision(blink = 2, speed = 125.1f).cameraWarm)
        assertFalse(decision(blink = 1, speed = 30f).cameraWarm)
    }

    @Test
    fun `arming respects enable reverse and both speed edges`() {
        assertTrue(blindSpotArmed(true, 4, 5, 10, 120))
        assertTrue(blindSpotArmed(true, 4, 125, 10, 120))
        assertFalse(blindSpotArmed(false, 4, 30, 10, 120))
        assertFalse(blindSpotArmed(true, BLIND_SPOT_GEAR_REVERSE, 30, 10, 120))
        assertFalse(blindSpotArmed(true, 4, 4, 10, 120))
        assertFalse(blindSpotArmed(true, 4, 126, 10, 120))
    }

    @Test
    fun `bsd only suppresses a valid inactive radar but not unavailable radar`() {
        assertTrue(
            decision(blink = 2, bsdOnly = true, leftBsdActive = false).show.isEmpty(),
        )
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            decision(blink = 2, bsdOnly = true, leftBsdActive = true).show,
        )
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            decision(blink = 2, bsdOnly = true, leftBsdActive = null).show,
        )
    }

    @Test
    fun `invalid bsd raw value is unavailable instead of inactive`() {
        assertFalse(checkNotNull(blindSpotBsdActive(0)))
        assertFalse(checkNotNull(blindSpotBsdActive(1)))
        assertTrue(checkNotNull(blindSpotBsdActive(2)))
        assertNull(blindSpotBsdActive(-1))
        assertNull(blindSpotBsdActive(3))
        assertNull(blindSpotBsdActive(null))
    }

    @Test
    fun `bsd only can suppress primary while sharp turn still adds opposite side`() {
        assertEquals(
            setOf(BlindSpotSide.RIGHT),
            decision(
                blink = 2,
                steering = 190f,
                bsdOnly = true,
                leftBsdActive = false,
                sharpTurn = true,
            ).show,
        )
    }

    @Test
    fun `sharp left turn adds opposite side while preserving primary side`() {
        assertEquals(
            setOf(BlindSpotSide.LEFT, BlindSpotSide.RIGHT),
            decision(blink = 2, steering = 190f, sharpTurn = true).show,
        )
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            decision(blink = 2, steering = 179f, sharpTurn = true).show,
        )
    }

    @Test
    fun `sharp right turn requires the matching negative steering direction`() {
        assertEquals(
            setOf(BlindSpotSide.RIGHT, BlindSpotSide.LEFT),
            decision(blink = 4, steering = -190f, sharpTurn = true).show,
        )
        assertEquals(
            setOf(BlindSpotSide.RIGHT),
            decision(blink = 4, steering = 190f, sharpTurn = true).show,
        )
    }

    @Test
    fun `telemetry gate tolerates one failed batch then closes on two`() {
        val gate = BlindSpotTelemetryGate()
        gate.reset(1_000L)
        gate.onSample(BlindSpotSample(1, 30f, 4), 1_100L)

        assertFalse(gate.onSample(null, 1_200L).mustClose)
        assertTrue(gate.onSample(null, 1_350L).mustClose)
    }

    @Test
    fun `missing steering signal does not invalidate basic turn telemetry`() {
        val sample = BlindSpotSample(2, 30f, 4, steeringAngleDeg = null)

        assertTrue(sample.isValid)
    }

    @Test
    fun `frame watchdog only diagnoses a shown established stream`() {
        assertTrue(blindSpotFrameStalled(true, true, 1_000L, 2_500L))
        assertFalse(blindSpotFrameStalled(false, true, 1_000L, 2_500L))
        assertFalse(blindSpotFrameStalled(true, false, 1_000L, 2_500L))
    }

    @Test
    fun `each shown side must deliver its own first frame`() {
        assertFalse(blindSpotFirstFrameTimedOut(false, false, 1_000L, 5_000L, 3_000L))
        assertFalse(blindSpotFirstFrameTimedOut(true, true, 1_000L, 5_000L, 3_000L))
        assertFalse(blindSpotFirstFrameTimedOut(true, false, 1_000L, 3_999L, 3_000L))
        assertTrue(blindSpotFirstFrameTimedOut(true, false, 1_000L, 4_000L, 3_000L))
    }

    private fun decision(
        blink: Int,
        speed: Float = 10f,
        steering: Float? = null,
        reverse: Boolean = false,
        forward: Boolean = true,
        minSpeed: Int = 10,
        telemetryAgeMs: Long = 0L,
        bsdOnly: Boolean = false,
        leftBsdActive: Boolean? = null,
        sharpTurn: Boolean = false,
    ) = decideBlindSpot(
        BlindSpotInput(
            blink = blink,
            speedKmh = speed,
            steeringAngleDeg = steering,
            gearIsReverse = reverse,
            gearIsForward = forward,
            minSpeedKmh = minSpeed,
            maxSpeedKmh = 120,
            telemetryAgeMs = telemetryAgeMs,
            bsdOnly = bsdOnly,
            leftBsdActive = leftBsdActive,
            sharpTurnEnabled = sharpTurn,
            sharpTurnAngleDeg = 180,
        ),
    )
}
