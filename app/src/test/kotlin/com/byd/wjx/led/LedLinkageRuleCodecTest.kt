package com.byd.wjx.led.ipixelfa

import com.byd.wjx.led.LedLinkageTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IpixelFaLinkageRuleCodecTest {
    @Test
    fun `rules round trip with order enabled state and active scene intact`() {
        val expected = listOf(
            IpixelFaLinkageRule(
                id = "first",
                enabled = false,
                triggerType = LedLinkageTriggerType.SPEED,
                conditionValue = "gt_60",
                steps = listOf(
                    IpixelFaLinkageStep(IpixelFaPreset.AUTOPILOT),
                ),
            ),
            IpixelFaLinkageRule(
                id = "second",
                triggerType = LedLinkageTriggerType.DOOR,
                conditionValue = "trunk",
                steps = listOf(IpixelFaLinkageStep(IpixelFaPreset.GET_OFF)),
            ),
        )

        assertEquals(expected, IpixelFaLinkageRuleCodec.decode(IpixelFaLinkageRuleCodec.encode(expected)))
    }

    @Test
    fun `empty list remains empty and does not restore defaults`() {
        assertEquals(emptyList<IpixelFaLinkageRule>(), IpixelFaLinkageRuleCodec.decode("[]"))
    }

    @Test
    fun `invalid JSON falls back through a null decode result`() {
        assertNull(IpixelFaLinkageRuleCodec.decode("not-json"))
    }

    @Test
    fun `invalid entries are skipped and legacy durations are ignored`() {
        val decoded = IpixelFaLinkageRuleCodec.decode(
            """[{"id":"ok","enabled":true,"triggerType":"BRAKE","conditionValue":"gt_10","steps":[{"presetId":20,"durationSeconds":999}]},{"id":"bad","triggerType":"UNKNOWN","conditionValue":"x","steps":[]}]"""
        )

        assertNotNull(decoded)
        assertEquals(1, decoded?.size)
        assertEquals(IpixelFaPreset.HAZARD_WARNING, decoded?.single()?.steps?.single()?.preset)
    }
}
