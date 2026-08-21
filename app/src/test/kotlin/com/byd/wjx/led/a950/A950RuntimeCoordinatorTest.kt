package com.byd.wjx.led.a950

import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.led.LedLinkageTriggerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class A950RuntimeCoordinatorTest {
    @Test
    fun `A950 reconnect initializes brightness without sending an implicit scene`() = runTest {
        val preferenceState = MutableStateFlow(
            A950PreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "A950 LED",
                brightness = 63,
                rules = listOf(
                    A950LinkageRule(
                        id = "reverse",
                        triggerType = LedLinkageTriggerType.GEAR,
                        conditionValue = "R",
                        steps = listOf(A950LinkageStep(A950Preset.REVERSING)),
                    )
                ),
            )
        )
        val preferences = mockk<A950Preferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(A950ConnectionState())
        val bleClient = mockk<A950BleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedAdasVehicleSnapshot() } returns null

        val coordinator = A950RuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        verify(exactly = 1) { bleClient.startAutoConnect() }

        connectionState.value = A950ConnectionState(
            phase = A950ConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
            name = "A950 LED",
        )
        runCurrent()
        assertNull(coordinator.runtime.value.currentPreset)

        coordinator.onVehicleData(diParsData(gear = 4, powerState = 3))
        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))
        assertEquals(A950Preset.REVERSING, coordinator.runtime.value.currentPreset)
        coVerify(exactly = 1) { bleClient.play(A950Preset.REVERSING) }

        connectionState.value = A950ConnectionState(phase = A950ConnectionPhase.IDLE)
        runCurrent()
        connectionState.value = A950ConnectionState(
            phase = A950ConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
            name = "A950 LED",
        )
        runCurrent()

        assertNull(coordinator.runtime.value.currentPreset)
        coVerify(exactly = 2) { bleClient.setBrightness(63) }
        coVerify(exactly = 1) { bleClient.play(any()) }

        coordinator.stop()
    }

    @Test
    fun `first verified A950 binding initializes exactly once`() = runTest {
        val preferenceState = MutableStateFlow(A950PreferenceSnapshot())
        val preferences = mockk<A950Preferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(A950ConnectionState())
        val bleClient = mockk<A950BleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedAdasVehicleSnapshot() } returns null

        val coordinator = A950RuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()

        connectionState.value = A950ConnectionState(
            phase = A950ConnectionPhase.DISCOVERING,
            address = "11:22:33:44:55:66",
            name = "A950 LED",
        )
        preferenceState.value = A950PreferenceSnapshot(
            enabled = true,
            deviceAddress = "11:22:33:44:55:66",
            deviceName = "A950 LED",
            brightness = 48,
        )
        connectionState.value = connectionState.value.copy(phase = A950ConnectionPhase.READY)
        runCurrent()

        coVerify(exactly = 1) { bleClient.setBrightness(48) }
        coVerify(exactly = 0) { bleClient.play(any()) }
        assertNull(coordinator.runtime.value.currentPreset)

        coordinator.stop()
    }

    @Test
    fun `vehicle data received after A950 runtime stops cannot dispatch a command`() = runTest {
        val preferenceState = MutableStateFlow(
            A950PreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                rules = listOf(
                    A950LinkageRule(
                        id = "reverse",
                        triggerType = LedLinkageTriggerType.GEAR,
                        conditionValue = "R",
                        steps = listOf(A950LinkageStep(A950Preset.REVERSING)),
                    )
                ),
            )
        )
        val preferences = mockk<A950Preferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(
            A950ConnectionState(
                phase = A950ConnectionPhase.READY,
                address = "AA:BB:CC:DD:EE:FF",
            )
        )
        val bleClient = mockk<A950BleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedAdasVehicleSnapshot() } returns null

        val coordinator = A950RuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        coordinator.stop()

        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))

        coVerify(exactly = 0) { bleClient.play(any()) }
    }

    @Test
    fun `released A950 condition uploads and shows the configured graffiti once`() = runTest {
        val graffiti = File.createTempFile("a950-release", ".gif").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        val preferenceState = MutableStateFlow(
            A950PreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                brightness = 50,
                graffitiPayloadPath = graffiti.absolutePath,
                graffitiImageVersion = 1L,
                rules = listOf(
                    A950LinkageRule(
                        id = "reverse",
                        triggerType = LedLinkageTriggerType.GEAR,
                        conditionValue = "R",
                        steps = listOf(A950LinkageStep(A950Preset.REVERSING)),
                    )
                ),
            )
        )
        val preferences = mockk<A950Preferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(A950ConnectionState())
        val bleClient = mockk<A950BleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true
        coEvery { bleClient.uploadGraffiti(any()) } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedAdasVehicleSnapshot() } returns null

        val coordinator = A950RuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        connectionState.value = A950ConnectionState(
            phase = A950ConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
            name = "A950 LED",
        )
        runCurrent()

        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))
        assertEquals(A950Preset.REVERSING, coordinator.runtime.value.currentPreset)

        coordinator.onVehicleData(diParsData(gear = 4, powerState = 3))
        runCurrent()
        assertNull(coordinator.runtime.value.currentPreset)
        assertTrue(coordinator.runtime.value.showingGraffiti)
        assertFalse(coordinator.runtime.value.graffitiUploadInProgress)
        assertNull(coordinator.runtime.value.lastError)
        coVerify(exactly = 1) { bleClient.play(A950Preset.REVERSING) }
        coVerify(exactly = 1) { bleClient.uploadGraffiti(byteArrayOf(1, 2, 3, 4)) }

        coordinator.onVehicleData(diParsData(gear = 4, powerState = 3))
        runCurrent()
        coVerify(exactly = 1) { bleClient.uploadGraffiti(any()) }

        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))
        assertEquals(A950Preset.REVERSING, coordinator.runtime.value.currentPreset)
        assertFalse(coordinator.runtime.value.showingGraffiti)
        assertNull(coordinator.runtime.value.lastError)
        coVerify(exactly = 2) { bleClient.play(A950Preset.REVERSING) }

        coordinator.stop()
    }

    @Test
    fun `new A950 condition cancels an in flight graffiti upload before playing`() = runTest {
        val graffiti = File.createTempFile("a950-cancel", ".gif").apply {
            writeBytes(ByteArray(32) { it.toByte() })
            deleteOnExit()
        }
        val preferenceState = MutableStateFlow(
            A950PreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                graffitiPayloadPath = graffiti.absolutePath,
                graffitiImageVersion = 1L,
                rules = listOf(
                    A950LinkageRule(
                        id = "reverse",
                        triggerType = LedLinkageTriggerType.GEAR,
                        conditionValue = "R",
                        steps = listOf(A950LinkageStep(A950Preset.REVERSING)),
                    )
                ),
            )
        )
        val preferences = mockk<A950Preferences>()
        every { preferences.state } returns preferenceState
        val connectionState = MutableStateFlow(A950ConnectionState())
        val uploadStarted = CompletableDeferred<Unit>()
        val uploadCancelled = CompletableDeferred<Unit>()
        val bleClient = mockk<A950BleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true
        coEvery { bleClient.uploadGraffiti(any()) } coAnswers {
            uploadStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                uploadCancelled.complete(Unit)
            }
        }
        val parsReader = mockk<ParsReader>(relaxed = true)
        val coordinator = A950RuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        connectionState.value = A950ConnectionState(
            phase = A950ConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
        )
        runCurrent()

        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))
        coordinator.onVehicleData(diParsData(gear = 4, powerState = 3))
        runCurrent()
        assertTrue(uploadStarted.isCompleted)
        assertTrue(coordinator.runtime.value.graffitiUploadInProgress)

        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))

        assertTrue(uploadCancelled.isCompleted)
        assertEquals(A950Preset.REVERSING, coordinator.runtime.value.currentPreset)
        assertFalse(coordinator.runtime.value.graffitiUploadInProgress)
        coVerify(exactly = 2) { bleClient.play(A950Preset.REVERSING) }
        coordinator.stop()
    }

    @Test
    fun `A950 initialization retries after the retry interval`() = runTest {
        val preferenceState = MutableStateFlow(
            A950PreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                brightness = 45,
            )
        )
        val preferences = mockk<A950Preferences>()
        every { preferences.state } returns preferenceState
        val connectionState = MutableStateFlow(A950ConnectionState())
        val bleClient = mockk<A950BleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(45) } returnsMany listOf(false, true)
        val parsReader = mockk<ParsReader>(relaxed = true)

        val coordinator = A950RuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        connectionState.value = A950ConnectionState(
            phase = A950ConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
        )
        runCurrent()

        coVerify(exactly = 1) { bleClient.setBrightness(45) }
        assertEquals("亮度设置发送失败", coordinator.runtime.value.lastError)

        advanceTimeBy(2_100L)
        runCurrent()

        coVerify(exactly = 2) { bleClient.setBrightness(45) }
        assertNull(coordinator.runtime.value.lastError)

        coordinator.stop()
    }

    @Test
    fun `clearing A950 rules leaves the last remote scene displayed`() = runTest {
        val reverseRule = A950LinkageRule(
            id = "reverse",
            triggerType = LedLinkageTriggerType.GEAR,
            conditionValue = "R",
            steps = listOf(A950LinkageStep(A950Preset.REVERSING)),
        )
        val preferenceState = MutableStateFlow(
            A950PreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                rules = listOf(reverseRule),
            )
        )
        val preferences = mockk<A950Preferences>()
        every { preferences.state } returns preferenceState
        val connectionState = MutableStateFlow(A950ConnectionState())
        val bleClient = mockk<A950BleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true
        val parsReader = mockk<ParsReader>(relaxed = true)

        val coordinator = A950RuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        connectionState.value = A950ConnectionState(
            phase = A950ConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
        )
        runCurrent()
        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))
        assertEquals(A950Preset.REVERSING, coordinator.runtime.value.currentPreset)

        preferenceState.value = preferenceState.value.copy(rules = emptyList())
        runCurrent()

        assertEquals(A950Preset.REVERSING, coordinator.runtime.value.currentPreset)
        coVerify(exactly = 1) { bleClient.play(A950Preset.REVERSING) }

        coordinator.stop()
    }
}
