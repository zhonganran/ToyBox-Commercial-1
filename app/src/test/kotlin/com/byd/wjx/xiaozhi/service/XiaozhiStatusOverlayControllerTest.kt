package com.byd.wjx.xiaozhi.service

import com.byd.wjx.xiaozhi.domain.XiaozhiPhase
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
}
