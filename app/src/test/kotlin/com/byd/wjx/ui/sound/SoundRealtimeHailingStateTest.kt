package com.byd.wjx.ui.sound

import com.byd.wjx.audio.RealtimeHailingPhase
import com.byd.wjx.audio.RealtimeHailingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundRealtimeHailingStateTest {
    @Test
    fun `starting clears sound playback and exposes busy state`() {
        val state = SoundUiState(playingPath = "/sound.mp3").withRealtimeHailingState(
            RealtimeHailingState(phase = RealtimeHailingPhase.Starting),
        )

        assertFalse(state.realtimeHailingRunning)
        assertTrue(state.realtimeHailingBusy)
        assertNull(state.playingPath)
        assertEquals("正在准备实时喊话", state.message)
    }

    @Test
    fun `running and stopping remain distinct UI states`() {
        val running = SoundUiState().withRealtimeHailingState(
            RealtimeHailingState(phase = RealtimeHailingPhase.Running),
        )
        val stopping = running.withRealtimeHailingState(
            RealtimeHailingState(phase = RealtimeHailingPhase.Stopping),
        )

        assertTrue(running.realtimeHailingRunning)
        assertFalse(running.realtimeHailingBusy)
        assertTrue(stopping.realtimeHailingRunning)
        assertTrue(stopping.realtimeHailingBusy)
    }

    @Test
    fun `error surfaces service message and clears active flags`() {
        val state = SoundUiState(
            realtimeHailingRunning = true,
            realtimeHailingBusy = true,
        ).withRealtimeHailingState(
            RealtimeHailingState(
                phase = RealtimeHailingPhase.Error,
                message = "车外喊话通道打开失败",
            ),
        )

        assertFalse(state.realtimeHailingRunning)
        assertFalse(state.realtimeHailingBusy)
        assertEquals("车外喊话通道打开失败", state.message)
    }

    @Test
    fun `snapshot restores flags without replacing current message`() {
        val state = SoundUiState(message = "已导入音效").withRealtimeHailingSnapshot(
            RealtimeHailingState(phase = RealtimeHailingPhase.Running),
        )

        assertTrue(state.realtimeHailingRunning)
        assertFalse(state.realtimeHailingBusy)
        assertEquals("已导入音效", state.message)
    }
}
