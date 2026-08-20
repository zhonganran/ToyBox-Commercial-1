package com.byd.wjx.led.ipixelfa

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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IpixelFaRuntimeCoordinatorTest {
    @Test
    fun `FA reconnect initializes brightness without sending an implicit scene`() = runTest {
        val preferenceState = MutableStateFlow(
            IpixelFaPreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "FA LED",
                brightness = 63,
                rules = listOf(
                    IpixelFaLinkageRule(
                        id = "reverse",
                        triggerType = LedLinkageTriggerType.GEAR,
                        conditionValue = "R",
                        steps = listOf(IpixelFaLinkageStep(IpixelFaPreset.REVERSING)),
                    )
                ),
            )
        )
        val preferences = mockk<IpixelFaPreferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(IpixelFaConnectionState())
        val bleClient = mockk<IpixelFaBleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null

        val coordinator = IpixelFaRuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        verify(exactly = 1) { bleClient.startAutoConnect() }

        connectionState.value = IpixelFaConnectionState(
            phase = IpixelFaConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
            name = "FA LED",
        )
        runCurrent()
        assertNull(coordinator.runtime.value.currentPreset)

        coordinator.onVehicleData(diParsData(gear = 4, powerState = 3))
        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))
        assertEquals(IpixelFaPreset.REVERSING, coordinator.runtime.value.currentPreset)
        coVerify(exactly = 1) { bleClient.play(IpixelFaPreset.REVERSING) }

        connectionState.value = IpixelFaConnectionState(phase = IpixelFaConnectionPhase.IDLE)
        runCurrent()
        connectionState.value = IpixelFaConnectionState(
            phase = IpixelFaConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
            name = "FA LED",
        )
        runCurrent()

        assertNull(coordinator.runtime.value.currentPreset)
        coVerify(exactly = 2) { bleClient.setBrightness(63) }
        coVerify(exactly = 1) { bleClient.play(any()) }

        coordinator.stop()
    }

    @Test
    fun `first verified FA binding initializes exactly once`() = runTest {
        val preferenceState = MutableStateFlow(IpixelFaPreferenceSnapshot())
        val preferences = mockk<IpixelFaPreferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(IpixelFaConnectionState())
        val bleClient = mockk<IpixelFaBleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null

        val coordinator = IpixelFaRuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()

        connectionState.value = IpixelFaConnectionState(
            phase = IpixelFaConnectionPhase.DISCOVERING,
            address = "11:22:33:44:55:66",
            name = "FA LED",
        )
        preferenceState.value = IpixelFaPreferenceSnapshot(
            enabled = true,
            deviceAddress = "11:22:33:44:55:66",
            deviceName = "FA LED",
            brightness = 48,
        )
        connectionState.value = connectionState.value.copy(phase = IpixelFaConnectionPhase.READY)
        runCurrent()

        coVerify(exactly = 1) { bleClient.setBrightness(48) }
        coVerify(exactly = 0) { bleClient.play(any()) }
        assertNull(coordinator.runtime.value.currentPreset)

        coordinator.stop()
    }

    @Test
    fun `vehicle data received after FA runtime stops cannot dispatch a command`() = runTest {
        val preferenceState = MutableStateFlow(
            IpixelFaPreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                rules = listOf(
                    IpixelFaLinkageRule(
                        id = "reverse",
                        triggerType = LedLinkageTriggerType.GEAR,
                        conditionValue = "R",
                        steps = listOf(IpixelFaLinkageStep(IpixelFaPreset.REVERSING)),
                    )
                ),
            )
        )
        val preferences = mockk<IpixelFaPreferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(
            IpixelFaConnectionState(
                phase = IpixelFaConnectionPhase.READY,
                address = "AA:BB:CC:DD:EE:FF",
            )
        )
        val bleClient = mockk<IpixelFaBleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null

        val coordinator = IpixelFaRuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        coordinator.stop()

        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))

        coVerify(exactly = 0) { bleClient.play(any()) }
    }
}
