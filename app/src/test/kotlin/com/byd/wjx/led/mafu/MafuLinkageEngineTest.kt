package com.byd.wjx.led.mafu

import com.byd.wjx.led.LedLinkageTriggerType
import com.byd.wjx.led.LedVehicleSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MafuLinkageEngineTest {
    @Test
    fun `condition triggers only on its rising edge`() {
        val engine = MafuLinkageEngine()
        val rules = listOf(rule("speed", "gt_30"))

        assertNull(engine.evaluate(rules, MODEL, LedVehicleSignals(speed = 30), 0L))
        assertEquals(
            MafuRemoteKey.KEY_1,
            engine.evaluate(rules, MODEL, LedVehicleSignals(speed = 31), 1L),
        )
        assertNull(engine.evaluate(rules, MODEL, LedVehicleSignals(speed = 40), 500L))
        assertNull(engine.evaluate(rules, MODEL, LedVehicleSignals(speed = 20), 1_500L))
        assertEquals(
            MafuRemoteKey.KEY_1,
            engine.evaluate(rules, MODEL, LedVehicleSignals(speed = 31), 1_600L),
        )
    }

    @Test
    fun `remote key remains selected while its condition remains true`() {
        val engine = MafuLinkageEngine()
        val rules = listOf(
            MafuLinkageRule(
                id = "reverse",
                triggerType = LedLinkageTriggerType.GEAR,
                conditionValue = "R",
                steps = listOf(
                    MafuLinkageStep(MafuRemoteKey.KEY_2),
                    MafuLinkageStep(MafuRemoteKey.KEY_3),
                ),
            )
        )

        assertEquals(MafuRemoteKey.KEY_2, engine.evaluate(rules, MODEL, LedVehicleSignals(gear = 2), 0L))
        assertNull(engine.evaluate(rules, MODEL, LedVehicleSignals(gear = 2), 500L))
        assertNull(engine.evaluate(rules, MODEL, LedVehicleSignals(gear = 2), 10_000L))
    }

    @Test
    fun `released condition requests the normal program list exactly once`() {
        val engine = MafuLinkageEngine()
        val rules = listOf(rule("speed", "gt_30"))

        assertEquals(
            MafuRemoteKey.KEY_1,
            engine.evaluate(rules, MODEL, LedVehicleSignals(speed = 31), 0L),
        )
        assertNull(engine.evaluate(rules, MODEL, LedVehicleSignals(speed = 20), 1L))
        assertEquals(true, engine.consumeProgramListResume())
        assertEquals(false, engine.consumeProgramListResume())
    }

    @Test
    fun `unsupported keys normalize to the first key of the selected model`() {
        val rule = MafuLinkageRule(
            id = "rule",
            triggerType = LedLinkageTriggerType.LIGHT,
            conditionValue = "hazard",
            steps = listOf(MafuLinkageStep(MafuRemoteKey.KEY_20)),
        )

        assertEquals(
            MafuRemoteKey.KEY_1,
            rule.normalized(MafuRemoteModel.YS_9_KEY).steps.single().remoteKey,
        )
    }

    private fun rule(id: String, condition: String) = MafuLinkageRule(
        id = id,
        triggerType = LedLinkageTriggerType.SPEED,
        conditionValue = condition,
        steps = listOf(MafuLinkageStep(MafuRemoteKey.KEY_1)),
    )

    private companion object {
        val MODEL = MafuRemoteModel.YS_20_KEY
    }
}
