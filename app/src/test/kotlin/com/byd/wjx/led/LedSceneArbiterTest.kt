package com.byd.wjx.led

import org.junit.Assert.assertEquals
import org.junit.Test

class LedSceneArbiterTest {
    @Test
    fun `stable left turn stays displayed until the switch enum turns off`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.LEFT_TURN,
            arbiter.evaluate(LedVehicleSignals(turnSignalFlash = 2), 1_000L),
        )
        assertEquals(
            LedScene.LEFT_TURN,
            arbiter.evaluate(LedVehicleSignals(turnSignalFlash = 2), 1_500L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(turnSignalFlash = 1), 1_600L),
        )
    }

    @Test
    fun `stable right turn stays displayed and restores immediately when cancelled`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.RIGHT_TURN,
            arbiter.evaluate(LedVehicleSignals(turnSignalFlash = 4), 1_000L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(turnSignalFlash = 1), 1_100L),
        )
    }

    @Test
    fun `hazard overrides reverse and turn`() {
        val arbiter = LedSceneArbiter()
        val scene = arbiter.evaluate(
            LedVehicleSignals(turnSignalFlash = 6, gear = 2),
            1_000L,
        )

        assertEquals(LedScene.HAZARD, scene)
    }

    @Test
    fun `reverse overrides ordinary turn`() {
        val arbiter = LedSceneArbiter()
        val scene = arbiter.evaluate(LedVehicleSignals(turnSignalFlash = 4, gear = 2), 1_000L)

        assertEquals(LedScene.REVERSE_PARKING, scene)
    }

    @Test
    fun `reverse scene follows reverse gear only`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.REVERSE_PARKING,
            arbiter.evaluate(LedVehicleSignals(gear = 2), 1_000L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(gear = 4), 1_100L),
        )
    }

    @Test
    fun `new turn direction immediately replaces the old direction`() {
        val arbiter = LedSceneArbiter()
        arbiter.evaluate(LedVehicleSignals(turnSignalFlash = 2), 1_000L)

        assertEquals(
            LedScene.RIGHT_TURN,
            arbiter.evaluate(LedVehicleSignals(turnSignalFlash = 4), 1_100L),
        )
    }

    @Test
    fun `autopilot overrides brake warning`() {
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
    fun `all active assisted driving families trigger autopilot`() {
        val activeSignals = listOf(
            "ACC mode" to LedVehicleSignals(adasAccMode = 3),
            "LCC NCA" to LedVehicleSignals(adasLccNcaStatus = 2),
            "TJA" to LedVehicleSignals(adasTjaState = 3),
            "lane keep" to LedVehicleSignals(laneKeepState = 2),
            "ACC cruise" to LedVehicleSignals(accCruiseState = 2),
            "MNOA" to LedVehicleSignals(adasMnoaStatus = 2),
        )

        activeSignals.forEachIndexed { index, (label, signals) ->
            assertEquals(
                label,
                LedScene.AUTOPILOT,
                LedSceneArbiter().evaluate(signals, 2_000L + index),
            )
        }
    }

    @Test
    fun `standby fault gray and takeover states do not trigger autopilot`() {
        val inactiveSignals = listOf(
            LedVehicleSignals(adasAccMode = 0),
            LedVehicleSignals(adasAccMode = 1),
            LedVehicleSignals(adasAccMode = 2),
            LedVehicleSignals(adasAccMode = 5),
            LedVehicleSignals(adasLccNcaStatus = 1),
            LedVehicleSignals(adasLccNcaStatus = 3),
            LedVehicleSignals(laneKeepState = 1),
            LedVehicleSignals(laneKeepState = 4),
            LedVehicleSignals(accCruiseState = 1),
            LedVehicleSignals(adasMnoaStatus = 1),
            LedVehicleSignals(adasMnoaStatus = 3),
            LedVehicleSignals(adasMnoaStatus = 4),
            LedVehicleSignals(adasMnoaStatus = 255),
            LedVehicleSignals(adasExecSpeed = 60),
        )

        inactiveSignals.forEachIndexed { index, signals ->
            assertEquals(
                LedScene.DEFAULT,
                LedSceneArbiter().evaluate(signals, 3_000L + index),
            )
        }
    }

    @Test
    fun `all vendor ACC working states trigger autopilot`() {
        listOf(3, 4, 6, 7).forEachIndexed { index, state ->
            assertEquals(
                "ACC state $state",
                LedScene.AUTOPILOT,
                LedSceneArbiter().evaluate(
                    LedVehicleSignals(adasAccMode = state),
                    3_500L + index,
                ),
            )
        }
    }

    @Test
    fun `automatic parking owns the reverse parking scene`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.REVERSE_PARKING,
            arbiter.evaluate(
                LedVehicleSignals(gear = 4, adasApaParkingStatus = 4),
                4_000L,
            ),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(
                LedVehicleSignals(gear = 4, adasApaParkingStatus = 5),
                4_100L,
            ),
        )
    }

    @Test
    fun `automatic parking does not enter the autopilot scene`() {
        assertEquals(
            LedScene.REVERSE_PARKING,
            LedSceneArbiter().evaluate(
                LedVehicleSignals(adasApaParkingStatus = 4),
                5_000L,
            ),
        )
    }

    @Test
    fun `brake warning requires moving vehicle and sufficient braking force`() {
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
            LedScene.BRAKE_WARNING,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 12), 1_200L),
        )
    }

    @Test
    fun `actual brake lamp drives warning and pedal remains the null fallback`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.BRAKE_WARNING,
            arbiter.evaluate(LedVehicleSignals(speed = 40, lightBrake = 1), 1_000L),
        )
        assertEquals(
            LedScene.BRAKE_WARNING,
            arbiter.evaluate(LedVehicleSignals(speed = 40, lightBrake = 0), 1_200L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(speed = 40, lightBrake = 0), 1_901L),
        )
    }

    @Test
    fun `brake warning uses hysteresis and a short release hold`() {
        val arbiter = LedSceneArbiter(brakeWarningReleaseHoldMs = 700L)

        assertEquals(
            LedScene.BRAKE_WARNING,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 20), 1_000L),
        )
        assertEquals(
            LedScene.BRAKE_WARNING,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 6), 1_100L),
        )
        assertEquals(
            LedScene.BRAKE_WARNING,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 0), 1_200L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(speed = 40, brakePedalDepth = 0), 1_901L),
        )
    }

    @Test
    fun `park transition holds temporary stop until leaving park`() {
        val arbiter = LedSceneArbiter()
        arbiter.evaluate(LedVehicleSignals(gear = 4, powerState = 3), 1_000L)

        assertEquals(
            LedScene.TEMPORARY_STOP,
            arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 3), 1_100L),
        )
        assertEquals(
            LedScene.TEMPORARY_STOP,
            arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 3), 10_000L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(gear = 4, powerState = 3), 10_100L),
        )
    }

    @Test
    fun `opening any door in park latches exit scene until leaving park`() {
        val openedDoors = listOf(
            LedVehicleSignals(gear = 1, powerState = 1, doorFL = 1, doorFR = 0, doorRL = 0, doorRR = 0),
            LedVehicleSignals(gear = 1, powerState = 1, doorFL = 0, doorFR = 1, doorRL = 0, doorRR = 0),
            LedVehicleSignals(gear = 1, powerState = 1, doorFL = 0, doorFR = 0, doorRL = 1, doorRR = 0),
            LedVehicleSignals(gear = 1, powerState = 1, doorFL = 0, doorFR = 0, doorRL = 0, doorRR = 1),
        )

        openedDoors.forEachIndexed { index, opened ->
            val arbiter = LedSceneArbiter()
            arbiter.evaluate(
                LedVehicleSignals(gear = 1, powerState = 1, doorFL = 0, doorFR = 0, doorRL = 0, doorRR = 0),
                1_000L,
            )
            assertEquals("door $index", LedScene.EXIT_VEHICLE, arbiter.evaluate(opened, 1_100L))
            assertEquals(
                "door $index remains latched after close",
                LedScene.EXIT_VEHICLE,
                arbiter.evaluate(
                    LedVehicleSignals(gear = 1, powerState = 1, doorFL = 0, doorFR = 0, doorRL = 0, doorRR = 0),
                    10_000L,
                ),
            )
            assertEquals(
                "door $index clears after leaving park",
                LedScene.DEFAULT,
                arbiter.evaluate(LedVehicleSignals(gear = 4, powerState = 1), 10_100L),
            )
        }
    }

    @Test
    fun `power down latches exit scene until the next power up`() {
        val arbiter = LedSceneArbiter()
        arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 3), 1_000L)

        assertEquals(
            LedScene.EXIT_VEHICLE,
            arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 0), 1_100L),
        )
        assertEquals(
            LedScene.EXIT_VEHICLE,
            arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 0), 20_000L),
        )
        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(LedVehicleSignals(gear = 1, powerState = 3), 20_100L),
        )
    }

    @Test
    fun `cold start with vehicle already off does not fake exit scene`() {
        val arbiter = LedSceneArbiter()

        assertEquals(
            LedScene.DEFAULT,
            arbiter.evaluate(
                LedVehicleSignals(gear = 1, powerState = 0, doorFL = 1, doorFR = 1, doorRL = 1, doorRR = 1),
                1_000L,
            ),
        )
    }
}
