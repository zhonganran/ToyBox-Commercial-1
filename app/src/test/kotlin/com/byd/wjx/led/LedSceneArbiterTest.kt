package com.byd.wjx.led

import org.junit.Assert.assertEquals
import org.junit.Test

class LedSceneArbiterTest {
    @Test
    fun `turn signal is held through the vehicle blink gap then returns to default`() {
        val arbiter = LedSceneArbiter(turnHoldMs = 1_200L)

        assertEquals(LedScene.LEFT_TURN, arbiter.evaluate(LedVehicleSignals(turnSignalLeft = 1), 1_000L))
        assertEquals(LedScene.LEFT_TURN, arbiter.evaluate(LedVehicleSignals(turnSignalLeft = 0), 1_900L))
        assertEquals(LedScene.DEFAULT, arbiter.evaluate(LedVehicleSignals(turnSignalLeft = 0), 2_201L))
    }

    @Test
    fun `hazard overrides reverse and turn`() {
        val arbiter = LedSceneArbiter()
        val scene = arbiter.evaluate(
            LedVehicleSignals(turnSignalLeft = 1, hazardLights = 1, gear = 2),
            1_000L,
        )

        assertEquals(LedScene.HAZARD, scene)
    }

    @Test
    fun `reverse overrides ordinary turn`() {
        val arbiter = LedSceneArbiter()
        val scene = arbiter.evaluate(LedVehicleSignals(turnSignalRight = 1, gear = 2), 1_000L)

        assertEquals(LedScene.REVERSE_PARKING, scene)
    }

    @Test
    fun `new turn direction immediately replaces the old direction`() {
        val arbiter = LedSceneArbiter(turnHoldMs = 1_200L)
        arbiter.evaluate(LedVehicleSignals(turnSignalLeft = 1), 1_000L)

        assertEquals(
            LedScene.RIGHT_TURN,
            arbiter.evaluate(LedVehicleSignals(turnSignalRight = 1), 1_100L),
        )
    }

    @Test
    fun `both direction lamps are treated as hazard`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.HAZARD,
            arbiter.evaluate(LedVehicleSignals(turnSignalLeft = 1, turnSignalRight = 1), 1_000L),
        )
    }

    @Test
    fun `autopilot overrides keep distance`() {
        val arbiter = LedSceneArbiter()
        val scene = arbiter.evaluate(
            LedVehicleSignals(speed = 60, adasLccNcaStatus = 2, brakePedalDepth = 30),
            1_000L,
        )

        assertEquals(LedScene.AUTOPILOT, scene)
    }

    @Test
    fun `passive and fault TJA states do not trigger autopilot`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(adasTjaState = 1), 1_000L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(adasTjaState = 4), 1_100L),
        )
    }

    @Test
    fun `active TJA states trigger autopilot`() {
        listOf(2, 3, 5).forEachIndexed { index, state ->
            val arbiter = LedSceneArbiter()
            assertEquals(
                "TJA state $state",
                LedScene.AUTOPILOT,
                arbiter.evaluate(LedVehicleSignals(adasTjaState = state), 1_000L + index),
            )
        }
    }

    @Test
    fun `lane keep configuration alone does not trigger autopilot`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(laneKeepState = 3), 1_000L),
        )
    }

    @Test
    fun `keep distance requires moving vehicle and sufficient braking force`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(speed = 0, brakePedalDepth = 30), 1_000L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 11), 1_100L),
        )
        assertEquals(
            LedScene.KEEP_DISTANCE,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 12), 1_200L),
        )
    }

    @Test
    fun `keep distance uses hysteresis and a short release hold`() {
        val arbiter = LedSceneArbiter(keepDistanceReleaseHoldMs = 700L)

        assertEquals(
            LedScene.KEEP_DISTANCE,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 20), 1_000L),
        )
        assertEquals(
            LedScene.KEEP_DISTANCE,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 6), 1_100L),
        )
        assertEquals(
            LedScene.KEEP_DISTANCE,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 0), 1_200L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 0), 1_901L),
        )
    }

    @Test
    fun `park transition shows temporary stop then default`() {
        val arbiter = LedSceneArbiter(oneShotDurationMs = 4_000L)
        arbiter.evaluate(LedVehicleSignals(gear = 4, powerState = 2), 1_000L)

        assertEquals(
            LedScene.TEMPORARY_STOP,
            arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 2), 1_100L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 2), 5_101L),
        )
    }

    @Test
    fun `opening driver door in park shows exit scene`() {
        val arbiter = LedSceneArbiter(oneShotDurationMs = 4_000L)
        arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 1, driverDoor = 0), 1_000L)

        assertEquals(
            LedScene.EXIT_VEHICLE,
            arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 1, driverDoor = 1), 1_100L),
        )
    }

    @Test
    fun `cold start with vehicle already off does not fake exit scene`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 0, driverDoor = 1), 1_000L),
        )
    }
}
