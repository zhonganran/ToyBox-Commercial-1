package com.byd.wjx.ui.settings

import com.byd.wjx.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferenceCoordinatorTest {
    @Test
    fun `load returns normalized app preferences from repository`() = runTest {
        val repository = mockk<SettingsRepository>()
        coEvery { repository.getThemeMode() } returns "dark"
        coEvery { repository.getAppWallpaper() } returns "tech_night"
        coEvery { repository.isTripGpsRecordingEnabled() } returns false
        val coordinator = SettingsPreferenceCoordinator(repository)

        val settings = coordinator.load()

        assertEquals(
            AppPreferenceSettings(
                themeMode = "dark",
                appWallpaper = "tech_night",
                tripGpsRecordingEnabled = false,
            ),
            settings,
        )
    }

    @Test
    fun `save operations delegate each preference to repository`() = runTest {
        val repository = mockk<SettingsRepository>(relaxed = true)
        val coordinator = SettingsPreferenceCoordinator(repository)

        coordinator.setThemeMode("light")
        coordinator.setAppWallpaper("sunrise_lake")
        coordinator.setTripGpsRecordingEnabled(false)

        coVerify(exactly = 1) { repository.setThemeMode("light") }
        coVerify(exactly = 1) { repository.setAppWallpaper("sunrise_lake") }
        coVerify(exactly = 1) { repository.setTripGpsRecordingEnabled(false) }
    }
}
