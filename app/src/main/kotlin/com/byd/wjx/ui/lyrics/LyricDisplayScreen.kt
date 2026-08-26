package com.byd.wjx.ui.lyrics

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Independent settings for the desktop and instrument-cluster lyric overlays. */
@Composable
fun LyricDisplayScreen(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    scrollEnabled: Boolean = true,
    viewModel: LyricDisplayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun requestOverlayPermission() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun setModeEnabled(target: LyricAdjustmentTarget, enabled: Boolean) {
        when (
            viewModel.setModeEnabled(
                target = target,
                enabled = enabled,
                overlayPermissionGranted = Settings.canDrawOverlays(context),
            )
        ) {
            LyricModeChangeResult.APPLIED -> Unit
            LyricModeChangeResult.ACTIVATION_REQUIRED ->
                Toast.makeText(context, "歌词组件需要激活后使用", Toast.LENGTH_SHORT).show()
            LyricModeChangeResult.OVERLAY_PERMISSION_REQUIRED -> requestOverlayPermission()
        }
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val scrollModifier = if (scrollEnabled) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = modifier
            .then(scrollModifier)
            .padding(horizontal = 58.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeader) LyricDisplayHeader()

        LyricSettingsPanel(
            state = uiState.panelState(),
            actions = LyricPanelActions(
                onDesktopEnabledChange = { setModeEnabled(LyricAdjustmentTarget.DESKTOP, it) },
                onClusterEnabledChange = { setModeEnabled(LyricAdjustmentTarget.CLUSTER, it) },
                onExpandedChange = viewModel::setExpanded,
                onAllowOtherAppsChange = viewModel::setAllowOtherApps,
                onOpenPlaybackPermission = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onResetClusterPosition = viewModel::resetClusterPosition,
                onLineCountChange = viewModel::setLineCount,
                onFontScaleChange = viewModel::setFontScale,
                onTextColorChange = viewModel::setTextColor,
                onHighlightColorChange = viewModel::setHighlightColor,
                onFontStyleChange = viewModel::setFontStyle,
                onMotionPresetChange = viewModel::setMotionPreset,
                onClusterXChange = viewModel::setClusterX,
                onClusterYChange = viewModel::setClusterY,
                onSyncOffsetChange = viewModel::setSyncOffset,
            ),
        )
    }
}
