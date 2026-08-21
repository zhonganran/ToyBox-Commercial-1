package com.byd.wjx.led.mafu

import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.led.LedLinkageTriggerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
class MafuRuntimeCoordinatorTest {
    @Test
    fun `Mafu connects in carousel mode and retries restoring it after a condition ends`() = runTest {
        val preferenceState = MutableStateFlow(
            MafuPreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "YS LED",
                brightness = 12,
                remoteModel = MafuRemoteModel.YS_17_KEY,
                rules = listOf(
                    MafuLinkageRule(
                        id = "reverse",
                        triggerType = LedLinkageTriggerType.GEAR,
                        conditionValue = "R",
                        steps = listOf(MafuLinkageStep(MafuRemoteKey.KEY_2)),
                    )
                ),
            )
        )
        val preferences = mockk<MafuPreferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(MafuConnectionState())
        val bleClient = mockk<MafuBleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setScreenPower(true) } returns true
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any(), any()) } returns true
        coEvery { bleClient.playProgramList() } returnsMany listOf(true, false, true)

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedAdasVehicleSnapshot() } returns null

        val coordinator = MafuRuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        verify(exactly = 1) { bleClient.startAutoConnect() }

        connectionState.value = MafuConnectionState(
            phase = MafuConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
            name = "YS LED",
        )
        runCurrent()
        coVerifyOrder {
            bleClient.setScreenPower(true)
            bleClient.setBrightness(12)
            bleClient.playProgramList()
        }

        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))
        assertEquals(MafuRemoteKey.KEY_2, coordinator.runtime.value.currentRemoteKey)
        assertEquals(77, coordinator.runtime.value.lastProgramId)

        coordinator.onVehicleData(diParsData(gear = 4, powerState = 3))
        assertEquals(MafuRemoteKey.KEY_2, coordinator.runtime.value.currentRemoteKey)
        assertEquals("马夫节目列表恢复失败", coordinator.runtime.value.lastError)

        coordinator.onVehicleData(diParsData(gear = 4, powerState = 3))
        assertNull(coordinator.runtime.value.currentRemoteKey)
        assertNull(coordinator.runtime.value.lastProgramId)
        assertNull(coordinator.runtime.value.lastError)
        coVerify(exactly = 1) {
            bleClient.play(MafuRemoteKey.KEY_2, MafuRemoteModel.YS_17_KEY)
        }
        coVerify(exactly = 3) { bleClient.playProgramList() }

        coordinator.stop()
    }

    @Test
    fun `Mafu does not start its program list when opening the screen fails`() = runTest {
        val preferenceState = MutableStateFlow(
            MafuPreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
            )
        )
        val preferences = mockk<MafuPreferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(MafuConnectionState())
        val bleClient = mockk<MafuBleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setScreenPower(true) } returns false

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedAdasVehicleSnapshot() } returns null

        val coordinator = MafuRuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        connectionState.value = MafuConnectionState(
            phase = MafuConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
        )
        runCurrent()

        coVerify(exactly = 1) { bleClient.setScreenPower(true) }
        coVerify(exactly = 0) { bleClient.setBrightness(any()) }
        coVerify(exactly = 0) { bleClient.playProgramList() }
        assertEquals("马夫开屏命令发送失败", coordinator.runtime.value.lastError)

        coordinator.stop()
    }

    @Test
    fun `Mafu initialization retries from screen power after the retry interval`() = runTest {
        val preferenceState = MutableStateFlow(
            MafuPreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                brightness = 9,
            )
        )
        val preferences = mockk<MafuPreferences>()
        every { preferences.state } returns preferenceState
        val connectionState = MutableStateFlow(MafuConnectionState())
        val bleClient = mockk<MafuBleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setScreenPower(true) } returnsMany listOf(false, true)
        coEvery { bleClient.setBrightness(9) } returns true
        coEvery { bleClient.playProgramList() } returns true
        val parsReader = mockk<ParsReader>(relaxed = true)

        val coordinator = MafuRuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        connectionState.value = MafuConnectionState(
            phase = MafuConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
        )
        runCurrent()

        coVerify(exactly = 1) { bleClient.setScreenPower(true) }
        coVerify(exactly = 0) { bleClient.setBrightness(any()) }
        coVerify(exactly = 0) { bleClient.playProgramList() }
        assertEquals("马夫开屏命令发送失败", coordinator.runtime.value.lastError)

        advanceTimeBy(2_100L)
        runCurrent()

        coVerify(exactly = 2) { bleClient.setScreenPower(true) }
        coVerify(exactly = 1) { bleClient.setBrightness(9) }
        coVerify(exactly = 1) { bleClient.playProgramList() }
        assertNull(coordinator.runtime.value.lastError)

        coordinator.stop()
    }

    @Test
    fun `clearing Mafu rules restores the device program list`() = runTest {
        val reverseRule = MafuLinkageRule(
            id = "reverse",
            triggerType = LedLinkageTriggerType.GEAR,
            conditionValue = "R",
            steps = listOf(MafuLinkageStep(MafuRemoteKey.KEY_2)),
        )
        val preferenceState = MutableStateFlow(
            MafuPreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                remoteModel = MafuRemoteModel.YS_17_KEY,
                rules = listOf(reverseRule),
            )
        )
        val preferences = mockk<MafuPreferences>()
        every { preferences.state } returns preferenceState
        val connectionState = MutableStateFlow(MafuConnectionState())
        val bleClient = mockk<MafuBleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setScreenPower(true) } returns true
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.playProgramList() } returns true
        coEvery { bleClient.play(any(), any()) } returns true
        val parsReader = mockk<ParsReader>(relaxed = true)

        val coordinator = MafuRuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        connectionState.value = MafuConnectionState(
            phase = MafuConnectionPhase.READY,
            address = "AA:BB:CC:DD:EE:FF",
        )
        runCurrent()
        coordinator.onVehicleData(diParsData(gear = 2, powerState = 3))
        assertEquals(MafuRemoteKey.KEY_2, coordinator.runtime.value.currentRemoteKey)

        preferenceState.value = preferenceState.value.copy(rules = emptyList())
        runCurrent()

        assertNull(coordinator.runtime.value.currentRemoteKey)
        coVerify(exactly = 2) { bleClient.playProgramList() }

        coordinator.stop()
    }
}
