package com.byd.wjx.ui.automation

import com.byd.wjx.data.local.entity.ActionDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationInsideSoundActionTest {
    @Test
    fun `inside sound payload keeps library selection and loop state`() {
        val action = ActionDef(
            command = "inside_sound",
            displayName = "车内播放：提示音",
            kind = "inside_sound",
            payload = """{"path":"/sdcard/WJX/提示音.mp3","name":"提示音","loop":false}""",
        )

        assertEquals("/sdcard/WJX/提示音.mp3", action.insideSoundPath())
        assertEquals("提示音", action.insideSoundName())
        assertFalse(action.insideSoundLoop())
        assertEquals(30, action.insideSoundVolume())

        val looping = action.withInsideSoundLoop(true)
        assertTrue(looping.insideSoundLoop())
        assertEquals("车内播放：提示音（循环）", looping.displayName)
        assertEquals(action.insideSoundPath(), looping.insideSoundPath())

        val quieter = looping.withInsideSoundVolume(20)
        assertEquals(20, quieter.insideSoundVolume())
        assertTrue(quieter.insideSoundLoop())
        assertEquals(action.insideSoundPath(), quieter.insideSoundPath())
        assertEquals(100, quieter.withInsideSoundVolume(150).insideSoundVolume())
    }
}
