package com.byd.wjx.led.mafu

import com.byd.wjx.led.LedLinkageTriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MafuLinkageRuleCodecTest {
    @Test
    fun `rules round trip with remote keys intact`() {
        val model = MafuRemoteModel.YS_20_KEY
        val expected = listOf(
            MafuLinkageRule(
                id = "first",
                enabled = false,
                triggerType = LedLinkageTriggerType.SPEED,
                conditionValue = "gt_60",
                steps = listOf(
                    MafuLinkageStep(MafuRemoteKey.KEY_4),
                ),
            )
        )

        assertEquals(expected, MafuLinkageRuleCodec.decode(MafuLinkageRuleCodec.encode(expected), model))
    }

    @Test
    fun `decode clamps keys to selected remote model`() {
        val decoded = MafuLinkageRuleCodec.decode(
            """[{"id":"rule","triggerType":"LIGHT","conditionValue":"hazard","steps":[{"remoteKey":20,"durationSeconds":999}]}]""",
            MafuRemoteModel.YS_9_KEY,
        )

        assertEquals(MafuRemoteKey.KEY_1, decoded?.single()?.steps?.single()?.remoteKey)
    }

    @Test
    fun `invalid JSON returns null`() {
        assertNull(MafuLinkageRuleCodec.decode("broken", MafuRemoteModel.YS_17_KEY))
    }
}
