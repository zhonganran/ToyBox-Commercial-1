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
    fun `remote key steps play in order`() {
        val engine = MafuLinkageEngine()
        val rules = listOf(
            MafuLinkageRule(
                id = "reverse",
                triggerType = LedLinkageTriggerType.GEAR,
                conditionValue = "R",
                steps = listOf(
                    MafuLinkageStep(MafuRemoteKey.KEY_2, 1),
                    MafuLinkageStep(MafuRemoteKey.KEY_3, 2),
                ),
            )
        )

        assertEquals(
            MafuRemoteKey.KEY_2,
            engine.evaluate(rules, MODEL, LedVehicleSignals(gear = 2), 0L),
        )
        assertNull(engine.evaluate(rules, MODEL, LedVehicleSignals(gear = 2), 500L))
        assertEquals(
            MafuRemoteKey.KEY_3,
            engine.evaluate(rules, MODEL, LedVehicleSignals(gear = 2), 1_000L),
        )
        assertNull(engine.evaluate(rules, MODEL, LedVehicleSignals(gear = 2), 3_000L))
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
        steps = listOf(MafuLinkageStep(MafuRemoteKey.KEY_1, 1)),
    )

    private companion object {
        val MODEL = MafuRemoteModel.YS_20_KEY
    }
}
