package com.byd.wjx.led.ipixelfa

import com.byd.wjx.led.LedLinkageTriggerType
import com.byd.wjx.led.LedVehicleSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpixelFaLinkageEngineTest {
    @Test
    fun `numeric conditions use strict comparisons`() {
        assertTriggers(
            type = LedLinkageTriggerType.SPEED,
            condition = "gt_30",
            inactive = LedVehicleSignals(speed = 30),
            active = LedVehicleSignals(speed = 31),
        )
        assertTriggers(
            type = LedLinkageTriggerType.SPEED,
            condition = "lt_20",
            inactive = LedVehicleSignals(speed = 20),
            active = LedVehicleSignals(speed = 19),
        )
        assertTriggers(
            type = LedLinkageTriggerType.ACCELERATOR,
            condition = "gt_40",
            inactive = LedVehicleSignals(accelPedalDepth = 40),
            active = LedVehicleSignals(accelPedalDepth = 41),
        )
        assertTriggers(
            type = LedLinkageTriggerType.BRAKE,
            condition = "gt_10",
            inactive = LedVehicleSignals(brakePedalDepth = 10),
            active = LedVehicleSignals(brakePedalDepth = 11),
        )
    }

    @Test
    fun `gear conditions map the BYD gear enum`() {
        mapOf("P" to 1, "R" to 2, "N" to 3, "D" to 4, "S" to 5, "M" to 6)
            .forEach { (condition, gear) ->
                assertTriggers(
                    type = LedLinkageTriggerType.GEAR,
                    condition = condition,
                    inactive = LedVehicleSignals(gear = 0),
                    active = LedVehicleSignals(gear = gear),
                )
            }
    }

    @Test
    fun `door and light conditions use the decoded vehicle enums`() {
        val cases = listOf(
            Triple(LedLinkageTriggerType.DOOR, "front_left", LedVehicleSignals(doorFL = 1)),
            Triple(LedLinkageTriggerType.DOOR, "front_right", LedVehicleSignals(doorFR = 1)),
            Triple(LedLinkageTriggerType.DOOR, "rear_left", LedVehicleSignals(doorRL = 1)),
            Triple(LedLinkageTriggerType.DOOR, "rear_right", LedVehicleSignals(doorRR = 1)),
            Triple(LedLinkageTriggerType.DOOR, "trunk", LedVehicleSignals(trunk = 1)),
            Triple(LedLinkageTriggerType.LIGHT, "left_turn", LedVehicleSignals(turnSignalFlash = 2)),
            Triple(LedLinkageTriggerType.LIGHT, "right_turn", LedVehicleSignals(turnSignalFlash = 4)),
            Triple(LedLinkageTriggerType.LIGHT, "hazard", LedVehicleSignals(turnSignalFlash = 6)),
            Triple(LedLinkageTriggerType.LIGHT, "brake", LedVehicleSignals(lightBrake = 1)),
            Triple(LedLinkageTriggerType.LIGHT, "reverse", LedVehicleSignals(lightReversing = 1)),
        )

        cases.forEach { (type, condition, active) ->
            assertTriggers(type, condition, LedVehicleSignals(), active)
        }
    }

    @Test
    fun `a condition triggers only on its rising edge`() {
        val engine = IpixelFaLinkageEngine()
        val rules = listOf(rule("speed", LedLinkageTriggerType.SPEED, "gt_30"))

        assertNull(engine.evaluate(rules, LedVehicleSignals(speed = 20), 0L))
        assertEquals(IpixelFaPreset.TURN_LEFT, engine.evaluate(rules, LedVehicleSignals(speed = 31), 100L))
        assertNull(engine.evaluate(rules, LedVehicleSignals(speed = 40), 200L))
        assertNull(engine.evaluate(rules, LedVehicleSignals(speed = 20), 5_100L))
        assertEquals(IpixelFaPreset.TURN_LEFT, engine.evaluate(rules, LedVehicleSignals(speed = 31), 5_200L))
    }

    @Test
    fun `simultaneous matches choose the first saved rule`() {
        val engine = IpixelFaLinkageEngine()
        val rules = listOf(
            rule("hazard", LedLinkageTriggerType.LIGHT, "hazard", IpixelFaPreset.HAZARD_WARNING),
            rule("reverse", LedLinkageTriggerType.GEAR, "R", IpixelFaPreset.REVERSING),
        )

        assertEquals(
            IpixelFaPreset.HAZARD_WARNING,
            engine.evaluate(rules, LedVehicleSignals(turnSignalFlash = 6, gear = 2), 0L),
        )
    }

    @Test
    fun `scene steps play in order and other rules are ignored during playback`() {
        val engine = IpixelFaLinkageEngine()
        val rules = listOf(
            IpixelFaLinkageRule(
                id = "reverse",
                triggerType = LedLinkageTriggerType.GEAR,
                conditionValue = "R",
                steps = listOf(
                    IpixelFaLinkageStep(IpixelFaPreset.TURN_LEFT, 1),
                    IpixelFaLinkageStep(IpixelFaPreset.TURN_RIGHT, 2),
                ),
            ),
            rule("hazard", LedLinkageTriggerType.LIGHT, "hazard", IpixelFaPreset.HAZARD_WARNING),
        )

        val bothActive = LedVehicleSignals(gear = 2, turnSignalFlash = 6)
        assertEquals(IpixelFaPreset.TURN_LEFT, engine.evaluate(rules, bothActive, 0L))
        assertNull(engine.evaluate(rules, bothActive, 500L))
        assertEquals(IpixelFaPreset.TURN_RIGHT, engine.evaluate(rules, bothActive, 1_000L))
        assertNull(engine.evaluate(rules, bothActive, 3_000L))
        assertNull(engine.evaluate(rules, LedVehicleSignals(gear = 4, turnSignalFlash = 1), 3_100L))
        assertEquals(
            IpixelFaPreset.HAZARD_WARNING,
            engine.evaluate(rules, LedVehicleSignals(gear = 4, turnSignalFlash = 6), 3_200L),
        )
    }

    @Test
    fun `a newly active higher priority rule preempts current playback`() {
        val engine = IpixelFaLinkageEngine()
        val rules = listOf(
            rule("hazard", LedLinkageTriggerType.LIGHT, "hazard", IpixelFaPreset.HAZARD_WARNING),
            rule("reverse", LedLinkageTriggerType.GEAR, "R", IpixelFaPreset.REVERSING),
        )

        assertEquals(
            IpixelFaPreset.REVERSING,
            engine.evaluate(rules, LedVehicleSignals(gear = 2), 0L),
        )
        assertEquals(
            IpixelFaPreset.HAZARD_WARNING,
            engine.evaluate(rules, LedVehicleSignals(gear = 2, turnSignalFlash = 6), 500L),
        )
        assertNull(
            engine.evaluate(rules, LedVehicleSignals(gear = 2, turnSignalFlash = 6), 600L),
        )
    }

    @Test
    fun `sequence completion does not emit an implicit fallback scene`() {
        val engine = IpixelFaLinkageEngine()
        val rules = listOf(rule("left", LedLinkageTriggerType.LIGHT, "left_turn"))

        assertEquals(
            IpixelFaPreset.TURN_LEFT,
            engine.evaluate(rules, LedVehicleSignals(turnSignalFlash = 2), 0L),
        )
        assertNull(engine.evaluate(rules, LedVehicleSignals(turnSignalFlash = 1), 1_000L))
        assertNull(engine.evaluate(rules, LedVehicleSignals(turnSignalFlash = 1), 10_000L))
    }

    @Test
    fun `reset permits an active condition to trigger again`() {
        val engine = IpixelFaLinkageEngine()
        val rules = listOf(rule("left", LedLinkageTriggerType.LIGHT, "left_turn"))
        val active = LedVehicleSignals(turnSignalFlash = 2)

        assertEquals(IpixelFaPreset.TURN_LEFT, engine.evaluate(rules, active, 0L))
        engine.reset()
        assertEquals(IpixelFaPreset.TURN_LEFT, engine.evaluate(rules, active, 1L))
    }

    private fun assertTriggers(
        type: LedLinkageTriggerType,
        condition: String,
        inactive: LedVehicleSignals,
        active: LedVehicleSignals,
    ) {
        val engine = IpixelFaLinkageEngine()
        val rules = listOf(rule("rule", type, condition))
        assertNull(engine.evaluate(rules, inactive, 0L))
        assertEquals(IpixelFaPreset.TURN_LEFT, engine.evaluate(rules, active, 1L))
    }

    private fun rule(
        id: String,
        type: LedLinkageTriggerType,
        condition: String,
        preset: IpixelFaPreset = IpixelFaPreset.TURN_LEFT,
    ) = IpixelFaLinkageRule(
        id = id,
        triggerType = type,
        conditionValue = condition,
        steps = listOf(IpixelFaLinkageStep(preset, 1)),
    )
}
