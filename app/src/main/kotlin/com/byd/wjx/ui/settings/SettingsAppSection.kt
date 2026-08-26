package com.byd.wjx.ui.settings

import androidx.compose.runtime.Composable

@Composable
internal fun AppSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsAppearanceSection(
        preferences = state.appPreferences,
        onThemeModeChange = viewModel::setThemeMode,
        onWallpaperChange = viewModel::setAppWallpaper,
    )
    SettingsDataSection(
        preferences = state.appPreferences,
        configStatus = state.maintenance.configStatus,
        onTripGpsRecordingChange = viewModel::setTripGpsRecordingEnabled,
        onExportConfig = viewModel::exportConfig,
        onRestoreConfig = viewModel::restoreConfig,
    )
    SettingsPermissionHealthSection(
        maintenance = state.maintenance,
        onRefresh = viewModel::refreshPermissionHealth,
        onRepair = viewModel::repairPermissions,
    )
    SettingsDiagnosticLogSection(
        maintenance = state.maintenance,
        onUploadLogs = viewModel::uploadLogs,
    )
    SettingsAboutSection(
        maintenance = state.maintenance,
        onAutoCheckUpdatesChange = viewModel::setAutoCheckUpdates,
        onCheckForUpdates = viewModel::showUpdateDialog,
    )
}
