package com.byd.wjx.desktopcard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

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
            title = "启用车辆控制与音效卡片",
            subtitle = "启用后在所有界面持续显示，不再自动隐藏",
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
                    if (state.overlayPermissionGranted) "已允许，可在所有界面显示" else "尚未允许，悬浮窗不会显示",
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
                Text(if (state.overlayPermissionGranted) "重新检查" else if (state.permissionBusy) "授权中" else "拉起授权")
            }
        }
    }

    DesktopQuickCardIconSection(
        icons = state.icons,
        selectedId = state.config.floatingIconId,
        importBusy = state.iconImportBusy,
        message = state.iconMessage,
        onSelect = viewModel::setFloatingIconId,
        onImport = { iconPicker.launch(arrayOf("image/*")) },
        onDelete = viewModel::deleteIcon,
    )

    DesktopQuickCardItemSection(
        title = "车辆控制",
        subtitle = "点击卡片时复用智能联动的安全控制链路",
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
        onAdd = { viewModel.refresh(); showSoundPicker = true },
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
            onSelect = { viewModel.addCommand(it); showCommandPicker = false },
            onDismiss = { showCommandPicker = false },
        )
    }
    if (showSoundPicker) {
        DesktopQuickCardSoundPicker(
            sounds = state.sounds.filterNot { it.file.absolutePath in state.config.soundPaths },
            onSelect = { viewModel.addSound(it); showSoundPicker = false },
            onDismiss = { showSoundPicker = false },
        )
    }
}

@Composable
private fun DesktopQuickCardIconSection(
    icons: List<DesktopQuickCardIcon>,
    selectedId: String,
    importBusy: Boolean,
    message: String?,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
) {
    DesktopQuickCardSettingsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("悬浮图标", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "内置 2 个图标；自定义图片数量不限，GIF 动图会保持动画",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Button(
                onClick = onImport,
                enabled = !importBusy,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Text(if (importBusy) "导入中" else "添加")
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(icons, key = DesktopQuickCardIcon::id) { icon ->
                Column(
                    modifier = Modifier.width(82.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(CardBorder.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .border(
                                width = if (selectedId == icon.id) 2.dp else 1.dp,
                                color = if (selectedId == icon.id) AccentGreen else CardBorder,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(icon.id) }
                            .padding(5.dp),
                    ) {
                        DesktopQuickCardIconImage(
                            icon = icon,
                            contentDescription = icon.label,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (icon.custom) {
                            IconButton(
                                onClick = { onDelete(icon.id) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(25.dp)
                                    .background(CardSurfaceElevated.copy(alpha = 0.9f), RoundedCornerShape(7.dp)),
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "删除${icon.label}",
                                    tint = AccentOrange,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    Text(
                        icon.label,
                        color = if (selectedId == icon.id) AccentGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (selectedId == icon.id) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        message?.let {
            Text(it, color = if (it.contains("失败") || it.contains("无法")) AccentOrange else AccentGreen, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DesktopQuickCardSettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun DesktopQuickCardToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = bydSwitchColors())
    }
}

@Composable
private fun DesktopQuickCardItemSection(
    title: String,
    subtitle: String,
    count: Int,
    onAdd: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DesktopQuickCardSettingsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$title（$count）", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("添加")
            }
        }
        if (count == 0) {
            Text("尚未添加$title", color = TextMuted, fontSize = 13.sp)
        } else {
            content()
        }
    }
}

@Composable
private fun DesktopQuickCardOrderedRow(
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBorder.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            color = AccentGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onUp, enabled = index > 0) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移", tint = if (index > 0) AccentBlue else TextMuted)
        }
        IconButton(onClick = onDown, enabled = index < count - 1) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移", tint = if (index < count - 1) AccentBlue else TextMuted)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = AccentOrange)
        }
    }
}

@Composable
private fun DesktopQuickCardCommandPicker(
    selected: Set<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val categories = DesktopQuickCardCommandCatalog.all.map { it.category }.distinct()
    var category by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    val candidates = DesktopQuickCardCommandCatalog.all.filter { it.category == category && it.command !in selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加车辆控制", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(categories) { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(alpha = 0.24f)),
                        )
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(candidates, key = { it.command }) { item ->
                        TextButton(onClick = { onSelect(item.command) }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.label, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(item.command, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        containerColor = CardSurfaceElevated,
    )
}

@Composable
private fun DesktopQuickCardSoundPicker(
    sounds: List<SoundLibrary.SoundItem>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加车外音效", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            if (sounds.isEmpty()) {
                Text("WJX 文件夹中暂无可添加的音效", color = TextSecondary)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(sounds, key = { it.file.absolutePath }) { sound ->
                        TextButton(onClick = { onSelect(sound.file.absolutePath) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = AccentOrange)
                            Spacer(Modifier.padding(horizontal = 5.dp))
                            Text(sound.name, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sound.sizeLabel, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        containerColor = CardSurfaceElevated,
    )
}
