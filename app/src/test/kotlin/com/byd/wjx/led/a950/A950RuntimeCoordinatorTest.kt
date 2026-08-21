package com.byd.wjx.led.a950

import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.led.LedLinkageTriggerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        coEvery { bleClient.resumeProgramList() } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null

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
        coVerify(exactly = 2) { bleClient.resumeProgramList() }
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
        coEvery { bleClient.resumeProgramList() } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null

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
        coVerify(exactly = 1) { bleClient.resumeProgramList() }
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
        coEvery { bleClient.resumeProgramList() } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null

        val coordinator = A950RuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        coordinator.stop()

        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))

        coVerify(exactly = 0) { bleClient.play(any()) }
    }

    @Test
    fun `released A950 condition retries restoring the device program list`() = runTest {
        val preferenceState = MutableStateFlow(
            A950PreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                brightness = 50,
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
        coEvery { bleClient.resumeProgramList() } returnsMany listOf(true, false, true)

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null

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
        assertEquals(A950Preset.REVERSING, coordinator.runtime.value.currentPreset)
        assertEquals("A950 节目列表恢复失败", coordinator.runtime.value.lastError)

        coordinator.onVehicleData(diParsData(gear = 4, powerState = 3))
        assertNull(coordinator.runtime.value.currentPreset)
        assertNull(coordinator.runtime.value.lastError)
        coVerify(exactly = 3) { bleClient.resumeProgramList() }

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
        coEvery { bleClient.setBrightness(45) } returns true
        coEvery { bleClient.resumeProgramList() } returnsMany listOf(false, true)
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
        coVerify(exactly = 1) { bleClient.resumeProgramList() }
        assertEquals("A950 节目列表恢复失败", coordinator.runtime.value.lastError)

        advanceTimeBy(2_100L)
        runCurrent()

        coVerify(exactly = 2) { bleClient.setBrightness(45) }
        coVerify(exactly = 2) { bleClient.resumeProgramList() }
        assertNull(coordinator.runtime.value.lastError)

        coordinator.stop()
    }

    @Test
    fun `clearing A950 rules restores the device program list`() = runTest {
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
        coEvery { bleClient.resumeProgramList() } returns true
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

        assertNull(coordinator.runtime.value.currentPreset)
        coVerify(exactly = 2) { bleClient.resumeProgramList() }

        coordinator.stop()
    }
}
