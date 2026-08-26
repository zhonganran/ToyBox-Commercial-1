package com.byd.wjx.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.byd.wjx.cluster.ClusterProjectionPreferences

@Composable
internal fun DisplaySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val activationAllowed = state.activation.activated || state.activation.trialActive
    val projectionPreferences = remember(context) { ClusterProjectionPreferences(context) }
    val projectionState = rememberClusterProjectionUiState(projectionPreferences, context)
    val vehicleControl = state.vehicleControl

    LaunchedEffect(Unit) {
        viewModel.refreshHeadlightControl()
    }
    HeadlightControlSection(
        availability = vehicleControl.headlightAvailability,
        mode = vehicleControl.headlightMode,
        loading = vehicleControl.headlightLoading,
        message = vehicleControl.headlightMessage,
        onRefresh = viewModel::refreshHeadlightControl,
        onModeSelected = viewModel::setHeadlightMode,
    )
    ExteriorSoundChannelSection(
        carProfile = vehicleControl.carProfile,
        onChannelChanged = viewModel::syncLockSoundNow,
    )
    SettingsKeyShortcutCoordinator(
        activationAllowed = activationAllowed,
        projectionEnabled = projectionState.enabled,
        projectionTriggerKey = projectionState.triggerKey,
    )
    ClusterProjectionSettingsSection(
        activationAllowed = activationAllowed,
        preferences = projectionPreferences,
        state = projectionState,
    )
}
