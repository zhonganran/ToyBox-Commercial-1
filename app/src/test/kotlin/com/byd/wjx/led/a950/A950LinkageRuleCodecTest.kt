package com.byd.wjx.led.a950

import com.byd.wjx.led.LedLinkageTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class A950LinkageRuleCodecTest {
    @Test
    fun `rules round trip with order enabled state and active scene intact`() {
        val expected = listOf(
            A950LinkageRule(
                id = "first",
                enabled = false,
                triggerType = LedLinkageTriggerType.SPEED,
                conditionValue = "gt_60",
                steps = listOf(
                    A950LinkageStep(A950Preset.AUTOPILOT),
                ),
            ),
            A950LinkageRule(
                id = "second",
                triggerType = LedLinkageTriggerType.DOOR,
                conditionValue = "trunk",
                steps = listOf(A950LinkageStep(A950Preset.OPEN_DOOR_CAREFULLY)),
            ),
        )

        assertEquals(expected, A950LinkageRuleCodec.decode(A950LinkageRuleCodec.encode(expected)))
    }

    @Test
    fun `empty list remains empty and does not restore defaults`() {
        assertEquals(emptyList<A950LinkageRule>(), A950LinkageRuleCodec.decode("[]"))
    }

    @Test
    fun `invalid JSON falls back through a null decode result`() {
        assertNull(A950LinkageRuleCodec.decode("not-json"))
    }

    @Test
    fun `invalid entries are skipped and legacy durations are ignored`() {
        val decoded = A950LinkageRuleCodec.decode(
            """[{"id":"ok","enabled":true,"triggerType":"BRAKE","conditionValue":"gt_10","steps":[{"commandCode":19,"durationSeconds":999}]},{"id":"bad","triggerType":"UNKNOWN","conditionValue":"x","steps":[]}]"""
        )

        assertNotNull(decoded)
        assertEquals(1, decoded?.size)
        assertEquals(A950Preset.HAZARD_WARNING, decoded?.single()?.steps?.single()?.preset)
    }
}
