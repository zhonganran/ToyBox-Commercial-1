package com.byd.wjx.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.byd.wjx.openAppPermissionSettings
import com.byd.wjx.openAppSpecialPermissionSettings

@Composable
internal fun AppSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionHealth()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
        onOpenSpecialPermission = { permission ->
            openAppSpecialPermissionSettings(context, permission)
        },
        onOpenAppPermissionSettings = {
            openAppPermissionSettings(context)
        },
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
