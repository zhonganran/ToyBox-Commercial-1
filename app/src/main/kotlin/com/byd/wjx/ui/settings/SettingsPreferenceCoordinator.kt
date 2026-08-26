package com.byd.wjx.ui.settings

import com.byd.wjx.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

data class AppPreferenceSettings(
    val themeMode: String,
    val appWallpaper: String,
    val tripGpsRecordingEnabled: Boolean,
)

@Singleton
class SettingsPreferenceCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend fun load(): AppPreferenceSettings = AppPreferenceSettings(
        themeMode = settingsRepository.getThemeMode(),
        appWallpaper = settingsRepository.getAppWallpaper(),
        tripGpsRecordingEnabled = settingsRepository.isTripGpsRecordingEnabled(),
    )

    suspend fun setThemeMode(mode: String) {
        settingsRepository.setThemeMode(mode)
    }

    suspend fun setAppWallpaper(wallpaper: String) {
        settingsRepository.setAppWallpaper(wallpaper)
    }

    suspend fun setTripGpsRecordingEnabled(enabled: Boolean) {
        settingsRepository.setTripGpsRecordingEnabled(enabled)
    }
}
