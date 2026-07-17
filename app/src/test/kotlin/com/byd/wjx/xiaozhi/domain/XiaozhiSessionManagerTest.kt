package com.byd.wjx.xiaozhi.domain

import android.content.Context
import com.byd.wjx.audio.AudioOwner
import com.byd.wjx.audio.AudioUsageCoordinator
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.audio.WjxRealtimeHailingService
import com.byd.wjx.xiaozhi.audio.XiaozhiAudioEngine
import com.byd.wjx.xiaozhi.audio.XiaozhiFeedbackPlayer
import com.byd.wjx.xiaozhi.data.XiaozhiPreferences
import com.byd.wjx.xiaozhi.protocol.XiaozhiListeningMode
import com.byd.wjx.xiaozhi.protocol.XiaozhiProvisioningClient
import com.byd.wjx.xiaozhi.protocol.XiaozhiServerEvent
import com.byd.wjx.xiaozhi.protocol.XiaozhiWebSocketClient
import com.byd.wjx.xiaozhi.service.XiaozhiVoiceService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XiaozhiSessionManagerTest {
    private val context = mockk<Context>(relaxed = true)
    private val preferences = mockk<XiaozhiPreferences>()
    private val provisioningClient = mockk<XiaozhiProvisioningClient>()
    private val webSocketClient = mockk<XiaozhiWebSocketClient>()
    private val audioEngine = mockk<XiaozhiAudioEngine>()
    private val feedbackPlayer = mockk<XiaozhiFeedbackPlayer>(relaxed = true)
    private val config = MutableStateFlow(
        XiaozhiConfig(
            websocketUrl = "wss://xiaozhi.example/ws",
            token = "token",
            deviceId = "device",
            clientId = "client",
        ),
    )

    @Before
    fun setUp() {
        mockkObject(XiaozhiVoiceService.Companion)
        mockkObject(WjxOutsidePlayService.Companion)
        mockkObject(WjxRealtimeHailingService.Companion)
        every { context.applicationContext } returns context
        every { preferences.config } returns config
        every { XiaozhiVoiceService.ensureRunning(any()) } returns true
        justRun { XiaozhiVoiceService.stop(any()) }
        justRun { WjxOutsidePlayService.stop(any(), any()) }
        every { WjxRealtimeHailingService.stop(any(), any()) } returns false
        every { webSocketClient.isReady() } returns true
        every { webSocketClient.startListening(any()) } returns true
        every { webSocketClient.abort() } returns true
        justRun { webSocketClient.disconnect(any()) }
        every { audioEngine.acquireFocus(any()) } returns Result.success(Unit)
        every { audioEngine.configurePlayback(any(), any()) } returns Result.success(Unit)
        every { audioEngine.startRecording(any(), any()) } returns Result.success(Unit)
        justRun { audioEngine.stopRecording() }
        justRun { audioEngine.stopPlayback() }
        justRun { audioEngine.stopAll() }
        justRun { audioEngine.abandonFocus() }
        every { audioEngine.finishPlayback(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (invocation.args[0] as () -> Unit).invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun responseTimeoutReleasesConversationAndReturnsError() = runTest {
        val audioUsage = AudioUsageCoordinator()
        val manager = manager(audioUsage)

        manager.startConversation()
        runCurrent()
        advanceTimeBy(321L)
        runCurrent()
        assertEquals(XiaozhiPhase.Listening, manager.state.value.phase)

        manager.onEvent(XiaozhiServerEvent.Stt("今天天气怎么样"))
        runCurrent()
        assertEquals(XiaozhiPhase.Thinking, manager.state.value.phase)

        advanceTimeBy(45_001L)
        runCurrent()

        assertEquals(XiaozhiPhase.Error, manager.state.value.phase)
        assertFalse(audioUsage.isActive(AudioOwner.XiaozhiConversation))
        verify(exactly = 1) { feedbackPlayer.playFailureTone() }
    }

    @Test
    fun wakeConversationReturnsToAutomaticListeningAfterReply() = runTest {
        val audioUsage = AudioUsageCoordinator()
        val manager = manager(audioUsage)

        manager.startWakeConversation()
        runCurrent()
        advanceTimeBy(321L)
        runCurrent()
        assertEquals(XiaozhiPhase.Listening, manager.state.value.phase)
        assertTrue(manager.state.value.autoListening)

        manager.onEvent(XiaozhiServerEvent.Stt("你好"))
        manager.onEvent(XiaozhiServerEvent.Tts("start", null))
        manager.onEvent(XiaozhiServerEvent.Tts("stop", null))
        runCurrent()
        assertEquals(XiaozhiPhase.Connecting, manager.state.value.phase)

        advanceTimeBy(421L)
        runCurrent()

        assertEquals(XiaozhiPhase.Listening, manager.state.value.phase)
        assertTrue(manager.state.value.autoListening)
        verify(atLeast = 2) { webSocketClient.startListening(XiaozhiListeningMode.Auto) }
    }

    @Test
    fun speakingTimeoutCannotLeaveConversationPermanentlyStuck() = runTest {
        val audioUsage = AudioUsageCoordinator()
        val manager = manager(audioUsage)

        manager.startConversation()
        runCurrent()
        advanceTimeBy(321L)
        runCurrent()
        manager.onEvent(XiaozhiServerEvent.Tts("start", null))
        runCurrent()
        assertEquals(XiaozhiPhase.Speaking, manager.state.value.phase)

        advanceTimeBy(90_001L)
        runCurrent()

        assertEquals(XiaozhiPhase.Error, manager.state.value.phase)
        assertFalse(audioUsage.isActive(AudioOwner.XiaozhiConversation))
        verify(exactly = 1) { feedbackPlayer.playFailureTone() }
    }

    private fun kotlinx.coroutines.test.TestScope.manager(
        audioUsage: AudioUsageCoordinator,
    ): XiaozhiSessionManager = XiaozhiSessionManager(
        context = context,
        preferences = preferences,
        provisioningClient = provisioningClient,
        webSocketClient = webSocketClient,
        audioEngine = audioEngine,
        feedbackPlayer = feedbackPlayer,
        audioUsage = audioUsage,
        applicationScope = backgroundScope,
    )
}
