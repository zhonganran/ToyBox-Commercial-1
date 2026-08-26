package com.byd.wjx.audio

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RealtimeHailingStateTest {
    @After
    fun resetState() {
        RealtimeHailingStateStore.publish(RealtimeHailingState())
    }

    @Test
    fun `state intent round trips phase message and channel`() {
        val source = RealtimeHailingState(
            phase = RealtimeHailingPhase.Starting,
            message = "正在准备实时喊话",
            channel = ExteriorChannel.Channel0,
        )

        val parsed = RealtimeHailingStateContract.fromIntent(
            RealtimeHailingStateContract.intent("com.byd.wjx", source),
        )

        assertEquals(source, parsed)
    }

    @Test
    fun `state intent rejects unknown action and phase`() {
        assertNull(RealtimeHailingStateContract.fromIntent(Intent("other")))
        assertNull(
            RealtimeHailingStateContract.fromIntent(
                Intent(RealtimeHailingStateContract.ACTION_STATE).putExtra("state", "unknown"),
            ),
        )
    }

    @Test
    fun `service command intents round trip without exposing wire strings`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertEquals(
            RealtimeHailingServiceCommand.Start(ExteriorChannel.Channel0),
            RealtimeHailingServiceCommandContract.fromIntent(
                RealtimeHailingServiceCommandContract.startIntent(context, ExteriorChannel.Channel0),
            ),
        )
        assertEquals(
            RealtimeHailingServiceCommand.Stop(notifyIfIdle = true),
            RealtimeHailingServiceCommandContract.fromIntent(
                RealtimeHailingServiceCommandContract.stopIntent(context, notifyIfIdle = true),
            ),
        )
        assertEquals(
            RealtimeHailingServiceCommand.UpdateTuning,
            RealtimeHailingServiceCommandContract.fromIntent(
                RealtimeHailingServiceCommandContract.updateTuningIntent(context),
            ),
        )
    }

    @Test
    fun `start and stop requests are idempotent`() {
        assertTrue(RealtimeHailingStateStore.requestStart(ExteriorChannel.Channel0))
        assertFalse(RealtimeHailingStateStore.requestStart(ExteriorChannel.Channel1))
        assertEquals(RealtimeHailingPhase.Starting, RealtimeHailingStateStore.snapshot().phase)
        assertEquals(ExteriorChannel.Channel0, RealtimeHailingStateStore.snapshot().channel)

        assertTrue(RealtimeHailingStateStore.requestStop())
        assertFalse(RealtimeHailingStateStore.requestStop())
        assertEquals(RealtimeHailingPhase.Stopping, RealtimeHailingStateStore.snapshot().phase)
    }

    @Test
    fun `error is inactive and permits retry`() {
        RealtimeHailingStateStore.publish(
            RealtimeHailingState(
                phase = RealtimeHailingPhase.Error,
                message = "启动失败",
            ),
        )

        assertFalse(RealtimeHailingStateStore.snapshot().isActive)
        assertTrue(RealtimeHailingStateStore.requestStart(ExteriorChannel.Channel1))
        assertEquals(RealtimeHailingPhase.Starting, RealtimeHailingStateStore.snapshot().phase)
    }

    @Test
    fun `phase exposes stable running and busy semantics`() {
        assertTrue(RealtimeHailingPhase.Starting.isBusy)
        assertTrue(RealtimeHailingPhase.Stopping.isBusy)
        assertTrue(RealtimeHailingPhase.Running.isRunning)
        assertFalse(RealtimeHailingPhase.Error.isActive)
        assertFalse(RealtimeHailingPhase.Idle.isBusy)
    }

    @Test
    fun `start request only matches its requested channel`() {
        val state = RealtimeHailingState(
            phase = RealtimeHailingPhase.Starting,
            channel = ExteriorChannel.Channel0,
        )

        assertTrue(state.isStartRequestFor(ExteriorChannel.Channel0))
        assertFalse(state.isStartRequestFor(ExteriorChannel.Channel1))
        assertFalse(state.copy(phase = RealtimeHailingPhase.Stopping).isStartRequestFor(ExteriorChannel.Channel0))
    }
}
