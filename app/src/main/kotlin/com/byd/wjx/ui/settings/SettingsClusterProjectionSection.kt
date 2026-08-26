package com.byd.wjx.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.cluster.ClusterMode
import com.byd.wjx.cluster.ClusterProjectionManager
import com.byd.wjx.cluster.ClusterProjectionPreferenceSnapshot
import com.byd.wjx.cluster.ClusterProjectionPreferences
import com.byd.wjx.cluster.MAX_OFFSET_PCT
import com.byd.wjx.cluster.MAX_SCALE_PCT
import com.byd.wjx.cluster.MIN_OFFSET_PCT
import com.byd.wjx.cluster.MIN_SCALE_PCT
import com.byd.wjx.cluster.ProjectionLayout
import com.byd.wjx.ui.components.AppLaunchPickerDialog
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import dagger.hilt.android.EntryPointAccessors

internal class ClusterProjectionUiState(
    snapshot: ClusterProjectionPreferenceSnapshot,
    initialTargetLabel: String,
) {
    var enabled by mutableStateOf(snapshot.enabled)
    var widthPct by mutableIntStateOf(snapshot.widthPct)
    var heightPct by mutableIntStateOf(snapshot.heightPct)
    var offsetXPct by mutableIntStateOf(snapshot.offsetXPct)
    var offsetYPct by mutableIntStateOf(snapshot.offsetYPct)
    var scalePct by mutableIntStateOf(snapshot.scalePct)
    var targetPackage by mutableStateOf(snapshot.targetPackage)
    var targetLabel by mutableStateOf(initialTargetLabel)
    var triggerKey by mutableIntStateOf(snapshot.triggerKeyCode)
    var layout by mutableStateOf(snapshot.layout)
}

@Composable
internal fun rememberClusterProjectionUiState(
    preferences: ClusterProjectionPreferences,
    context: Context,
): ClusterProjectionUiState {
    val snapshot = remember(preferences) { preferences.snapshot() }
    return remember(preferences) {
        ClusterProjectionUiState(
            snapshot = snapshot,
            initialTargetLabel = snapshot.targetLabel
                ?: resolveAppLabel(context, snapshot.targetPackage),
        )
    }
}

@Composable
internal fun ClusterProjectionSettingsSection(
    activationAllowed: Boolean,
    preferences: ClusterProjectionPreferences,
    state: ClusterProjectionUiState,
) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ClusterEntryPoint::class.java,
        )
    }
    var pickingApp by remember { mutableStateOf(false) }
    var learning by remember { mutableStateOf(false) }

    SectionHeader(text = stringResource(R.string.settings_display_mirror_header))
    ProjectionEnabledCard(
        enabled = state.enabled,
        onEnabledChange = { requested ->
            if (requested && !activationAllowed) {
                state.enabled = false
                preferences.setEnabled(false)
                Toast.makeText(context, "按键映射到仪表盘需要激活后使用", Toast.LENGTH_SHORT).show()
            } else {
                state.enabled = requested
                preferences.setEnabled(requested)
                if (!requested) {
                    ClusterProjectionManager.setMode(
                        context,
                        ClusterMode.OFF,
                        entryPoint.helperClient(),
                        entryPoint.helperBootstrap(),
                    )
                    ClusterProjectionManager.stopDuplicateProjectionFallback()
                } else {
                    ClusterProjectionManager.enableStarControl(
                        entryPoint.helperClient(),
                        entryPoint.helperBootstrap(),
                    )
                    if (state.layout == ProjectionLayout.DUPLICATE) {
                        ClusterProjectionManager.prepareDuplicateProjection(context)
                    }
                }
            }
        },
    )
    if (state.enabled) {
        ProjectionTriggerCard(
            triggerKey = state.triggerKey,
            onClick = {
                ClusterProjectionManager.enableStarControl(
                    entryPoint.helperClient(),
                    entryPoint.helperBootstrap(),
                )
                learning = true
            },
        )
    }
    if (learning) {
        LearnButtonDialog(
            onSave = { code ->
                state.triggerKey = code
                ClusterProjectionManager.setTriggerKeyCode(context, code)
                learning = false
            },
            onDismiss = { learning = false },
        )
    }

    ProjectionLayoutSection(
        selected = state.layout,
        onSelected = { layout ->
            state.layout = layout
            preferences.setLayout(layout)
            if (layout == ProjectionLayout.DUPLICATE) {
                if (state.enabled) ClusterProjectionManager.prepareDuplicateProjection(context)
            } else {
                ClusterProjectionManager.stopDuplicateProjectionFallback()
            }
            ClusterProjectionManager.reproject(
                context,
                entryPoint.helperClient(),
                entryPoint.helperBootstrap(),
            )
        },
    )
    if (state.layout == ProjectionLayout.INDEPENDENT) {
        ProjectionTargetAppCard(state.targetLabel) { pickingApp = true }
        if (pickingApp) {
            AppLaunchPickerDialog(
                currentPackage = state.targetPackage,
                onDismiss = { pickingApp = false },
                onSelect = { packageName, label ->
                    state.targetPackage = packageName
                    state.targetLabel = label
                    preferences.setTarget(packageName, label)
                    pickingApp = false
                },
            )
        }
        ProjectionGeometrySection(
            enabled = state.enabled,
            state = state,
            applyGeometry = {
                preferences.setGeometry(
                    state.widthPct,
                    state.heightPct,
                    state.offsetXPct,
                    state.offsetYPct,
                    state.scalePct,
                )
                ClusterProjectionManager.reproject(
                    context,
                    entryPoint.helperClient(),
                    entryPoint.helperBootstrap(),
                )
            },
        )
    } else {
        Text(
            stringResource(R.string.settings_display_duplicate_fullscreen_note),
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ProjectionEnabledCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = AccentGreen)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_display_mirror_title),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.settings_display_mirror_desc),
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = bydSwitchColors(),
            )
        }
    }
}

@Composable
private fun ProjectionTriggerCard(triggerKey: Int, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = AccentGreen)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_display_button_title),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(steeringButtonLabel(triggerKey), color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            Text(
                stringResource(R.string.settings_display_button_change),
                color = AccentGreen,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ProjectionLayoutSection(
    selected: ProjectionLayout,
    onSelected: (ProjectionLayout) -> Unit,
) {
    SectionHeader(text = stringResource(R.string.settings_display_projection_layout_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip(
                    stringResource(R.string.settings_display_projection_layout_independent),
                    selected == ProjectionLayout.INDEPENDENT,
                ) { onSelected(ProjectionLayout.INDEPENDENT) }
                UnitChip(
                    stringResource(R.string.settings_display_projection_layout_duplicate),
                    selected == ProjectionLayout.DUPLICATE,
                ) { onSelected(ProjectionLayout.DUPLICATE) }
            }
            Text(
                if (selected == ProjectionLayout.DUPLICATE) {
                    stringResource(R.string.settings_display_projection_layout_duplicate_desc)
                } else {
                    stringResource(R.string.settings_display_projection_layout_independent_desc)
                },
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ProjectionTargetAppCard(label: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Apps, contentDescription = null, tint = AccentGreen)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_display_app_title),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(label, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProjectionGeometrySection(
    enabled: Boolean,
    state: ClusterProjectionUiState,
    applyGeometry: () -> Unit,
) {
    SectionHeader(text = stringResource(R.string.settings_display_size_header))
    ClusterSizeSlider(
        stringResource(R.string.settings_display_size_width),
        state.widthPct,
        enabled,
        { state.widthPct = it },
        applyGeometry,
        description = stringResource(R.string.settings_display_size_width_desc),
    )
    ClusterSizeSlider(
        stringResource(R.string.settings_display_size_height),
        state.heightPct,
        enabled,
        { state.heightPct = it },
        applyGeometry,
        description = stringResource(R.string.settings_display_size_height_desc),
    )
    ClusterSizeSlider(
        stringResource(R.string.settings_display_offset_x),
        state.offsetXPct,
        enabled,
        { state.offsetXPct = it },
        applyGeometry,
        MIN_OFFSET_PCT,
        MAX_OFFSET_PCT,
        description = stringResource(R.string.settings_display_offset_x_desc),
    )
    ClusterSizeSlider(
        stringResource(R.string.settings_display_offset_y),
        state.offsetYPct,
        enabled,
        { state.offsetYPct = it },
        applyGeometry,
        MIN_OFFSET_PCT,
        MAX_OFFSET_PCT,
        description = stringResource(R.string.settings_display_offset_y_desc),
    )
    ClusterSizeSlider(
        stringResource(R.string.settings_display_scale),
        state.scalePct,
        enabled,
        { state.scalePct = it },
        applyGeometry,
        MIN_SCALE_PCT,
        MAX_SCALE_PCT,
        description = stringResource(R.string.settings_display_scale_desc),
    )
}
