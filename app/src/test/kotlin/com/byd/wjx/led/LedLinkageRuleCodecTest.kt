package com.byd.wjx.led

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LedLinkageRuleCodecTest {
    @Test
    fun `rules round trip with order enabled state and scene steps intact`() {
        val expected = listOf(
            LedLinkageRule(
                id = "first",
                enabled = false,
                triggerType = LedLinkageTriggerType.SPEED,
                conditionValue = "gt_60",
                steps = listOf(
                    LedLinkageStep(LedScene.AUTOPILOT, 3),
                    LedLinkageStep(LedScene.DEFAULT, 7),
                ),
            ),
            LedLinkageRule(
                id = "second",
                triggerType = LedLinkageTriggerType.DOOR,
                conditionValue = "trunk",
                steps = listOf(LedLinkageStep(LedScene.EXIT_VEHICLE, 5)),
            ),
        )

        assertEquals(expected, LedLinkageRuleCodec.decode(LedLinkageRuleCodec.encode(expected)))
    }

    @Test
    fun `empty list remains empty and does not restore defaults`() {
        assertEquals(emptyList<LedLinkageRule>(), LedLinkageRuleCodec.decode("[]"))
    }

    @Test
    fun `invalid JSON falls back through a null decode result`() {
        assertNull(LedLinkageRuleCodec.decode("not-json"))
    }

    @Test
    fun `invalid entries are skipped and durations are bounded`() {
        val decoded = LedLinkageRuleCodec.decode(
            """[{"id":"ok","enabled":true,"triggerType":"BRAKE","conditionValue":"gt_10","steps":[{"scene":"HAZARD","durationSeconds":999}]},{"id":"bad","triggerType":"UNKNOWN","conditionValue":"x","steps":[]}]"""
        )

        assertNotNull(decoded)
        assertEquals(1, decoded?.size)
        assertEquals(60, decoded?.single()?.steps?.single()?.durationSeconds)
    }
}
