package com.byd.wjx.ui.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationMediaActionTest {
    @Test
    fun `music action preserves source volume and loop`() {
        val action = newPlayMusicAction()
            .withAutomationMedia("content://media/song", "song.mp3")
            .withAutomationMediaVolume(65)
            .withAutomationMediaLoop(true)

        assertEquals("content://media/song", action.automationMediaPath())
        assertEquals("song.mp3", action.automationMediaFileName())
        assertEquals(65, action.automationMediaVolume())
        assertTrue(action.automationMediaLoop())
    }

    @Test
    fun `video volume is clamped and sentry action switches direction`() {
        val video = newPlayVideoAction().withAutomationMediaVolume(150)
        assertEquals(100, video.automationMediaVolume())
        assertFalse(video.automationMediaLoop())

        val sentry = newSentryModeAction().withSentryMode(false)
        assertEquals("sentry_off", sentry.command)
        assertEquals("off", sentry.payload)
        assertEquals("关闭哨兵模式", sentry.displayName)
    }
}
