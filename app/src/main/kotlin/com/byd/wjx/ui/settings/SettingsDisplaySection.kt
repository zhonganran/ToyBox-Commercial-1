package com.byd.wjx.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.byd.wjx.cluster.ClusterProjectionPreferences

@Composable
internal fun DisplaySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val activationAllowed = state.activation.activated || state.activation.trialActive
    val projectionPreferences = remember(context) { ClusterProjectionPreferences(context) }
    val projectionState = rememberClusterProjectionUiState(projectionPreferences, context)
    val vehicleProfile = state.vehicleProfile

    ExteriorSoundChannelSection(
        carProfile = vehicleProfile.carProfile,
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
    BlindSpotSettingsSection(activationAllowed = activationAllowed)
}
