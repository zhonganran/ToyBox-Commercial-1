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
        assertFalse(rearStreamArmed(true, BLIND_SPOT_GEAR_REVERSE))
    }

    @Test
    fun `rear stream is visible until a turn image takes priority`() {
        assertEquals(
            setOf(BlindSpotSide.REAR),
            blindSpotRequestedSides(emptySet(), rearStreamEnabled = true),
        )
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            blindSpotRequestedSides(setOf(BlindSpotSide.LEFT), rearStreamEnabled = true),
        )
        assertEquals(
            setOf(BlindSpotSide.RIGHT),
            blindSpotRequestedSides(setOf(BlindSpotSide.RIGHT), rearStreamEnabled = true),
        )
    }

    @Test
    fun `manual preview takes priority over turn and rear stream`() {
        assertEquals(
            setOf(BlindSpotSide.REAR),
            blindSpotRequestedSides(
                turnSides = setOf(BlindSpotSide.LEFT),
                rearStreamEnabled = true,
                manualSide = BlindSpotSide.REAR,
            ),
        )
    }

    @Test
    fun `turn signal takes priority over radar and radar takes priority over rear stream`() {
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            blindSpotRequestedSides(
                turnSides = setOf(BlindSpotSide.LEFT),
                radarSides = setOf(BlindSpotSide.RIGHT),
                rearStreamEnabled = true,
            ),
        )
        assertEquals(
            setOf(BlindSpotSide.LEFT, BlindSpotSide.RIGHT),
            blindSpotRequestedSides(
                turnSides = emptySet(),
                radarSides = setOf(BlindSpotSide.LEFT, BlindSpotSide.RIGHT),
                rearStreamEnabled = true,
            ),
        )
        assertEquals(
            BlindSpotTriggerSource.TURN,
            blindSpotRequest(
                turnSides = setOf(BlindSpotSide.LEFT),
                radarSides = setOf(BlindSpotSide.RIGHT),
                rearStreamEnabled = true,
            ).source,
        )
        assertEquals(
            BlindSpotTriggerSource.RADAR,
            blindSpotRequest(
                turnSides = emptySet(),
                radarSides = setOf(BlindSpotSide.RIGHT),
                rearStreamEnabled = true,
            ).source,
        )
    }

    @Test
    fun `radar auto popup keeps an active side visible and enforces per-side cooldown`() {
        val gate = BlindSpotAutoPopupGate()

        val firstRequest = gate.update(
            enabled = true,
            eligible = true,
            leftActive = true,
            rightActive = false,
            suppressed = false,
            cooldownMs = 5_000L,
            now = 1_000L,
        )
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            firstRequest,
        )
        gate.onDisplayed(firstRequest)
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            gate.update(true, true, true, false, false, 5_000L, 2_000L),
        )
        assertTrue(gate.update(true, true, false, false, false, 5_000L, 3_000L).isEmpty())
        assertTrue(gate.update(true, true, true, false, false, 5_000L, 7_999L).isEmpty())
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            gate.update(true, true, true, false, false, 5_000L, 8_000L),
        )
    }

    @Test
    fun `radar auto popup tracks both sides independently and rejects unavailable signals`() {
        val gate = BlindSpotAutoPopupGate()

        assertTrue(gate.update(true, true, null, null, false, 10_000L, 1_000L).isEmpty())
        val bothSides = gate.update(true, true, true, true, false, 10_000L, 2_000L)
        assertEquals(
            setOf(BlindSpotSide.LEFT, BlindSpotSide.RIGHT),
            bothSides,
        )
        gate.onDisplayed(bothSides)
        assertEquals(
            setOf(BlindSpotSide.RIGHT),
            gate.update(true, true, false, true, false, 10_000L, 3_000L),
        )
    }

    @Test
    fun `radar event hidden by a higher priority view does not consume cooldown`() {
        val gate = BlindSpotAutoPopupGate()

        assertTrue(gate.update(true, true, true, false, true, 10_000L, 1_000L).isEmpty())
        assertTrue(gate.update(true, true, false, false, true, 10_000L, 2_000L).isEmpty())
        assertEquals(0L, gate.cooldownRemainingMs(BlindSpotSide.LEFT, 2_000L))
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            gate.update(true, true, true, false, false, 10_000L, 3_000L),
        )
    }

    @Test
    fun `radar request that never reached the screen does not consume cooldown`() {
        val gate = BlindSpotAutoPopupGate()

        assertEquals(
            setOf(BlindSpotSide.LEFT),
            gate.update(true, true, true, false, false, 10_000L, 1_000L),
        )
        assertTrue(gate.update(true, true, false, false, false, 10_000L, 2_000L).isEmpty())
        assertEquals(0L, gate.cooldownRemainingMs(BlindSpotSide.LEFT, 2_000L))
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            gate.update(true, true, true, false, false, 10_000L, 3_000L),
        )
    }

    @Test
    fun `unavailable radar clears a displayed session without starting user cooldown`() {
        val gate = BlindSpotAutoPopupGate()
        val request = gate.update(true, true, true, false, false, 10_000L, 1_000L)
        gate.onDisplayed(request)

        assertTrue(gate.update(true, true, null, false, false, 10_000L, 2_000L).isEmpty())
        assertEquals(0L, gate.cooldownRemainingMs(BlindSpotSide.LEFT, 2_000L))
        assertEquals(
            setOf(BlindSpotSide.LEFT),
            gate.update(true, true, true, false, false, 10_000L, 3_000L),
        )
    }

    @Test
    fun `radar signal filter debounces edges and tolerates a short unavailable interval`() {
        val filter = BlindSpotBsdSignalFilter(
            activeSamplesRequired = 2,
            inactiveSamplesRequired = 2,
            unavailableGraceMs = 600L,
        )

        assertFalse(checkNotNull(filter.update(true, false, 0L).leftActive))
        assertTrue(checkNotNull(filter.update(true, false, 150L).leftActive))
        assertTrue(checkNotNull(filter.update(false, false, 300L).leftActive))
        assertTrue(checkNotNull(filter.update(null, false, 450L).leftActive))
        assertTrue(checkNotNull(filter.update(null, false, 1_049L).leftActive))
        assertNull(filter.update(null, false, 1_050L).leftActive)
    }

    @Test
    fun `unavailable samples do not erase valid inactive debounce evidence`() {
        val filter = BlindSpotBsdSignalFilter(
            activeSamplesRequired = 2,
            inactiveSamplesRequired = 2,
            unavailableGraceMs = 600L,
        )
        filter.update(true, false, 0L)
        assertTrue(checkNotNull(filter.update(true, false, 150L).leftActive))

        assertTrue(checkNotNull(filter.update(false, false, 300L).leftActive))
        assertTrue(checkNotNull(filter.update(null, false, 450L).leftActive))
        assertFalse(checkNotNull(filter.update(false, false, 500L).leftActive))
    }

    @Test
    fun `radar auto popup requires valid telemetry speed range and non reverse gear`() {
        assertTrue(blindSpotAutoPopupEligible(4, 30f, 10, 120, 0L))
        assertFalse(blindSpotAutoPopupEligible(BLIND_SPOT_GEAR_REVERSE, 30f, 10, 120, 0L))
        assertFalse(blindSpotAutoPopupEligible(null, 30f, 10, 120, 0L))
        assertFalse(blindSpotAutoPopupEligible(4, 9f, 10, 120, 0L))
        assertFalse(blindSpotAutoPopupEligible(4, 30f, 10, 120, BLIND_SPOT_WATCHDOG_MS + 1))
    }

    @Test
    fun `rear stream requires a known non reverse gear`() {
        assertTrue(rearStreamArmed(true, BLIND_SPOT_GEAR_DRIVE))
        assertTrue(rearStreamArmed(true, 1))
        assertFalse(rearStreamArmed(false, BLIND_SPOT_GEAR_DRIVE))
        assertFalse(rearStreamArmed(true, null))
        assertFalse(rearStreamArmed(true, BLIND_SPOT_GEAR_REVERSE))
    }

    @Test
    fun `manual calibration always works in park even when speed is unavailable`() {
        assertTrue(
            blindSpotManualPreviewAllowed(
                BlindSpotSide.REAR,
                BLIND_SPOT_GEAR_PARK,
                speedKmh = null,
            ),
        )
        assertTrue(
            blindSpotManualPreviewAllowed(
                BlindSpotSide.LEFT,
                BLIND_SPOT_GEAR_PARK,
                speedKmh = 20f,
            ),
        )
    }

    @Test
    fun `manual calibration in neutral still requires a stationary speed reading`() {
        assertTrue(
            blindSpotManualPreviewAllowed(
                BlindSpotSide.REAR,
                BLIND_SPOT_GEAR_NEUTRAL,
                speedKmh = 1f,
            ),
        )
        assertFalse(
            blindSpotManualPreviewAllowed(
                BlindSpotSide.REAR,
                BLIND_SPOT_GEAR_NEUTRAL,
                speedKmh = null,
            ),
        )
        assertFalse(
            blindSpotManualPreviewAllowed(
                BlindSpotSide.REAR,
                BLIND_SPOT_GEAR_NEUTRAL,
                speedKmh = 2f,
            ),
        )
        assertFalse(
            blindSpotManualPreviewAllowed(
                BlindSpotSide.REAR,
                BLIND_SPOT_GEAR_DRIVE,
                speedKmh = 0f,
            ),
        )
    }

    @Test
    fun `automatic mode works in every known non reverse gear`() {
        assertTrue(blindSpotArmed(true, BLIND_SPOT_GEAR_PARK, 0, 0, 120))
        assertTrue(blindSpotArmed(true, BLIND_SPOT_GEAR_NEUTRAL, 0, 0, 120))
        assertTrue(blindSpotArmed(true, BLIND_SPOT_GEAR_DRIVE, 0, 0, 120))
        assertFalse(blindSpotArmed(true, BLIND_SPOT_GEAR_REVERSE, 0, 0, 120))
        assertFalse(blindSpotArmed(true, null, 0, 0, 120))
        assertTrue(decision(blink = 2, speed = 0f, minSpeed = 0).cameraWarm)
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

        assertFalse(gate.onSample(null, 1_200L).turnMustClose)
        assertTrue(gate.onSample(null, 1_350L).turnMustClose)
    }

    @Test
    fun `gear only telemetry keeps rear stream valid while turn telemetry degrades`() {
        val gate = BlindSpotTelemetryGate()
        gate.reset(1_000L)

        val initial = gate.onSample(BlindSpotSample(2, 30f, BLIND_SPOT_GEAR_DRIVE), 1_100L)
        val degraded = gate.onSample(
            BlindSpotSample(null, null, BLIND_SPOT_GEAR_DRIVE),
            4_500L,
        )

        assertFalse(initial.turnMustClose)
        assertFalse(initial.rearMustClose)
        assertTrue(degraded.turnMustClose)
        assertFalse(degraded.rearMustClose)
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
