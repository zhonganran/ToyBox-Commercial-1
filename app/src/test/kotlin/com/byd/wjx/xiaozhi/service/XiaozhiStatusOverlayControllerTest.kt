package com.byd.wjx.xiaozhi.service

import com.byd.wjx.xiaozhi.domain.XiaozhiPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaozhiStatusOverlayControllerTest {
    @Test
    fun `active conversation phases are visible on desktop`() {
        listOf(
            XiaozhiPhase.Connecting,
            XiaozhiPhase.Listening,
            XiaozhiPhase.Thinking,
            XiaozhiPhase.Speaking,
        ).forEach { phase ->
            assertTrue(xiaozhiStatusPresentation(phase).visible)
        }
    }

    @Test
    fun `inactive phases do not leave a desktop overlay behind`() {
        listOf(
            XiaozhiPhase.NotConfigured,
            XiaozhiPhase.Idle,
            XiaozhiPhase.Error,
        ).forEach { phase ->
            assertFalse(xiaozhiStatusPresentation(phase).visible)
        }
    }

    @Test
    fun `listening presentation explains automatic silence timeout`() {
        val presentation = xiaozhiStatusPresentation(XiaozhiPhase.Listening)

        assertTrue(presentation.title.contains("正在聆听"))
        assertTrue(presentation.detail.contains("15秒"))
    }

    @Test
    fun `local wake listener stays invisible while waiting on desktop`() {
        val presentation = xiaozhiWakeStatusPresentation(
            running = true,
            status = "本地监听中",
        )

        assertFalse(presentation.visible)
    }

    @Test
    fun `desktop indicator appears only after local wake match`() {
        val presentation = xiaozhiWakeStatusPresentation(
            running = true,
            status = "已唤醒，正在打开自动对话",
        )

        assertTrue(presentation.visible)
        assertTrue(presentation.title.contains("已唤醒"))
    }

    @Test
    fun `stopped wake service leaves no standby indicator`() {
        assertFalse(
            xiaozhiWakeStatusPresentation(
                running = false,
                status = "已停止",
            ).visible,
        )
    }

    @Test
    fun `active conversation takes priority over wake standby`() {
        val conversation = xiaozhiStatusPresentation(XiaozhiPhase.Speaking)
        val wake = xiaozhiWakeStatusPresentation(true, "已唤醒，正在打开自动对话")

        assertEquals(conversation, selectXiaozhiStatusPresentation(conversation, wake))
    }
}
