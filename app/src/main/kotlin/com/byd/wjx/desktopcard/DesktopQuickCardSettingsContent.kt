package com.byd.wjx.desktopcard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.TextPrimary

@Composable
fun DesktopQuickCardSettingsContent(
    viewModel: DesktopQuickCardSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCommandPicker by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importIcon)
    }
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshOverlayPermission()
        onPauseOrDispose { }
    }

    DesktopQuickCardSettingsPanel {
        DesktopQuickCardToggleRow(
            title = "显示悬浮图标",
            subtitle = "控制桌面悬浮图标是否显示",
            checked = state.config.enabled,
            onCheckedChange = viewModel::setEnabled,
        )
        HorizontalDivider(color = CardBorder)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("悬浮窗权限", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.overlayPermissionGranted) {
                        "已允许，可在所有界面显示"
                    } else {
                        "尚未允许，悬浮窗不会显示"
                    },
                    color = if (state.overlayPermissionGranted) AccentGreen else AccentOrange,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = viewModel::requestOverlayPermission,
                enabled = !state.permissionBusy,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(
                    if (state.overlayPermissionGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Add,
                    contentDescription = null,
                )
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(
                    if (state.overlayPermissionGranted) {
                        "重新检查"
                    } else if (state.permissionBusy) {
                        "授权中"
                    } else {
                        "拉起授权"
                    },
                )
            }
        }
    }

    DesktopQuickCardIconSection(
        icons = state.icons,
        selectedId = state.config.floatingIconId,
        iconSizeDp = state.config.floatingIconSizeDp,
        maxIconSizeDp = state.maxFloatingIconSizeDp,
        importBusy = state.iconImportBusy,
        message = state.iconMessage,
        onSelect = viewModel::setFloatingIconId,
        onSizeChange = viewModel::setFloatingIconSizeDp,
        onImport = { iconPicker.launch(arrayOf("image/*")) },
        onDelete = viewModel::deleteIcon,
    )

    DesktopQuickCardItemSection(
        title = "快捷功能",
        subtitle = "可添加车辆控制、仪表卡片1/2、桌面/仪表歌词开关和实时喊话",
        count = state.config.functionCommands.size,
        onAdd = { showCommandPicker = true },
    ) {
        state.config.functionCommands.forEachIndexed { index, command ->
            val item = DesktopQuickCardCommandCatalog.byCommand[command] ?: return@forEachIndexed
            DesktopQuickCardOrderedRow(
                title = item.label,
                subtitle = item.category,
                index = index,
                count = state.config.functionCommands.size,
                onUp = { viewModel.moveCommand(command, -1) },
                onDown = { viewModel.moveCommand(command, 1) },
                onDelete = { viewModel.removeCommand(command) },
            )
        }
    }

    DesktopQuickCardItemSection(
        title = "车外音效",
        subtitle = "同一音效再次点击会停止，播放继续走车外路由强确认",
        count = state.config.soundPaths.size,
        onAdd = {
            viewModel.refresh()
            showSoundPicker = true
        },
    ) {
        state.config.soundPaths.forEachIndexed { index, path ->
            val sound = state.sounds.firstOrNull { it.file.absolutePath == path }
            DesktopQuickCardOrderedRow(
                title = sound?.name ?: path.substringAfterLast('/').substringBeforeLast('.'),
                subtitle = sound?.sizeLabel ?: "本地音效",
                index = index,
                count = state.config.soundPaths.size,
                onUp = { viewModel.moveSound(path, -1) },
                onDown = { viewModel.moveSound(path, 1) },
                onDelete = { viewModel.removeSound(path) },
            )
        }
    }

    if (showCommandPicker) {
        DesktopQuickCardCommandPicker(
            selected = state.config.functionCommands.toSet(),
            onSelect = {
                viewModel.addCommand(it)
                showCommandPicker = false
            },
            onDismiss = { showCommandPicker = false },
        )
    }
    if (showSoundPicker) {
        DesktopQuickCardSoundPicker(
            sounds = state.sounds.filterNot { it.file.absolutePath in state.config.soundPaths },
            onSelect = {
                viewModel.addSound(it)
                showSoundPicker = false
            },
            onDismiss = { showSoundPicker = false },
        )
    }
}
