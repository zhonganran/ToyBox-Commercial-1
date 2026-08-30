package com.byd.wjx.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.adbrescue.AdbRescueManager
import com.byd.wjx.adbrescue.AdbRescuePreferences
import com.byd.wjx.adbrescue.AdbRescueRuntimeCoordinator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AdbRescueSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rescue catches tick failures and ends on wall clock deadline`() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.elapsedRealtime = { testScheduler.currentTime }
        coEvery { fixture.runtime.ensureEnabled(match { it.startsWith("RESCUE_TICK_") }) } throws
            IllegalStateException("helper offline")

        fixture.viewModel.startRescue()
        advanceTimeBy(180_001L)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertFalse(state.rescueRunning)
        assertTrue(state.rescueAttempt > 0)
        assertTrue(state.message.orEmpty().contains("helper offline"))
    }

    private fun fixture(): Fixture {
        val manager = mockk<AdbRescueManager>()
        val runtime = mockk<AdbRescueRuntimeCoordinator>()
        val preferences = mockk<AdbRescuePreferences>()
        every { preferences.isGuardEnabled() } returns false
        every { preferences.setGuardEnabled(any()) } returns true
        every { manager.recordEvent(any()) } just runs
        val viewModel = AdbRescueSettingsViewModel(
            context = ApplicationProvider.getApplicationContext<Context>(),
            manager = manager,
            runtimeCoordinator = runtime,
            preferences = preferences,
        )
        return Fixture(viewModel, runtime)
    }

    private data class Fixture(
        val viewModel: AdbRescueSettingsViewModel,
        val runtime: AdbRescueRuntimeCoordinator,
    )
}
