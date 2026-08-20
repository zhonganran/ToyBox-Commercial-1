package com.byd.wjx.led.ipixelfa

import com.byd.wjx.led.LedLinkageTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IpixelFaLinkageRuleCodecTest {
    @Test
    fun `rules round trip with order enabled state and scene steps intact`() {
        val expected = listOf(
            IpixelFaLinkageRule(
                id = "first",
                enabled = false,
                triggerType = LedLinkageTriggerType.SPEED,
                conditionValue = "gt_60",
                steps = listOf(
                    IpixelFaLinkageStep(IpixelFaPreset.AUTOPILOT, 3),
                    IpixelFaLinkageStep(IpixelFaPreset.KEEP_DISTANCE, 7),
                ),
            ),
            IpixelFaLinkageRule(
                id = "second",
                triggerType = LedLinkageTriggerType.DOOR,
                conditionValue = "trunk",
                steps = listOf(IpixelFaLinkageStep(IpixelFaPreset.GET_OFF, 5)),
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
    fun `invalid entries are skipped and durations are bounded`() {
        val decoded = IpixelFaLinkageRuleCodec.decode(
            """[{"id":"ok","enabled":true,"triggerType":"BRAKE","conditionValue":"gt_10","steps":[{"presetId":20,"durationSeconds":999}]},{"id":"bad","triggerType":"UNKNOWN","conditionValue":"x","steps":[]}]"""
        )

        assertNotNull(decoded)
        assertEquals(1, decoded?.size)
        assertEquals(60, decoded?.single()?.steps?.single()?.durationSeconds)
    }
}
