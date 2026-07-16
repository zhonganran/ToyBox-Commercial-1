package com.byd.wjx.led

import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.diParsData
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LedRuntimeCoordinatorTest {
    @Test
    fun `reconnect clears the previous scene and sends default smile first`() = runTest {
        val preferenceState = MutableStateFlow(
            LedPreferenceSnapshot(
                enabled = true,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "LED",
                brightness = 63,
            )
        )
        val preferences = mockk<LedPreferences>()
        every { preferences.state } returns preferenceState

        val connectionState = MutableStateFlow(LedConnectionState())
        val bleClient = mockk<LedBleClient>(relaxed = true)
        every { bleClient.connection } returns connectionState
        coEvery { bleClient.setBrightness(any()) } returns true
        coEvery { bleClient.play(any()) } returns true

        val parsReader = mockk<ParsReader>()
        coEvery { parsReader.fetchLedMotionVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBrakeVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedBodyVehicleSnapshot() } returns null
        coEvery { parsReader.fetchLedAdasVehicleSnapshot() } returns null

        val coordinator = LedRuntimeCoordinator(preferences, bleClient, parsReader)
        coordinator.start(backgroundScope)
        runCurrent()
        verify(exactly = 1) { bleClient.startAutoConnect() }

        connectionState.value = LedConnectionState(phase = LedConnectionPhase.READY)
        runCurrent()
        assertEquals(LedScene.DEFAULT, coordinator.runtime.value.currentScene)

        coordinator.onVehicleData(diParsData(gear = 4, powerState = 3))
        coordinator.onVehicleData(diParsData(gear = 1, powerState = 3))
        assertEquals(LedScene.TEMPORARY_STOP, coordinator.runtime.value.currentScene)
        coVerify(exactly = 1) { bleClient.play(LedScene.TEMPORARY_STOP) }

        connectionState.value = LedConnectionState(phase = LedConnectionPhase.IDLE)
        runCurrent()
        connectionState.value = LedConnectionState(phase = LedConnectionPhase.READY)
        runCurrent()

        assertEquals(LedScene.DEFAULT, coordinator.runtime.value.currentScene)
        coVerify(exactly = 2) { bleClient.setBrightness(63) }
        coVerify(exactly = 2) { bleClient.play(LedScene.DEFAULT) }

        coordinator.stop()
    }
}
