package com.byd.wjx.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.*
import java.io.File

@Composable
internal fun VehicleEventSoundSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importEventSound(uri)
    }
    LaunchedEffect(Unit) {
        viewModel.reloadLockSoundChoices()
    }
    SectionHeader(text = "车辆事件音效")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { importLauncher.launch(arrayOf("audio/*")) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = AccentContent),
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入音效到 WJX", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            LockSoundEventRow(
                event = LockSoundEvent.Lock,
                subtitle = "车辆锁止并伴随闪灯确认后播放",
                state = state,
                onEnabledChanged = { viewModel.setLockSoundEnabled(LockSoundEvent.Lock, it) },
                onSoundSelected = { viewModel.setLockSoundPath(LockSoundEvent.Lock, it.path) },
                onSoundDeleted = viewModel::deleteEventSound,
            )
            LockSoundEventRow(
                event = LockSoundEvent.Unlock,
                subtitle = "车辆解锁并伴随闪灯确认后播放",
                state = state,
                onEnabledChanged = { viewModel.setLockSoundEnabled(LockSoundEvent.Unlock, it) },
                onSoundSelected = { viewModel.setLockSoundPath(LockSoundEvent.Unlock, it.path) },
                onSoundDeleted = viewModel::deleteEventSound,
            )
            LockSoundEventRow(
                event = LockSoundEvent.Sentry,
                subtitle = "哨兵报警上报后播放",
                state = state,
                onEnabledChanged = { viewModel.setLockSoundEnabled(LockSoundEvent.Sentry, it) },
                onSoundSelected = { viewModel.setLockSoundPath(LockSoundEvent.Sentry, it.path) },
                onSoundDeleted = viewModel::deleteEventSound,
            )
            state.lockSoundStatus?.let {
                Text(
                    text = if (state.lockSoundSyncing) "同步中…" else it,
                    color = if (it.contains("不可用") || it.contains("暂无") || it.contains("不存在")) SocRed else AccentGreen,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun LockSoundEventRow(
    event: LockSoundEvent,
    subtitle: String,
    state: SettingsUiState,
    onEnabledChanged: (Boolean) -> Unit,
    onSoundSelected: (LockSoundChoice) -> Unit,
    onSoundDeleted: (String) -> Unit,
) {
    var picking by remember(event) { mutableStateOf(false) }
    val selectedPath = state.lockSound.path(event)
    val selected = state.lockSoundChoices.firstOrNull { it.path == selectedPath }
    val enabled = state.lockSound.enabled(event)
    val premiumAllowed = state.activation.activated || state.activation.trialActive

    if (picking) {
        LockSoundPickerDialog(
            title = event.title,
            choices = state.lockSoundChoices,
            selectedPath = selectedPath,
            onDismiss = { picking = false },
            onSelect = {
                picking = false
                onSoundSelected(it)
            },
            onDelete = onSoundDeleted,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
            .clickable(enabled = premiumAllowed) { picking = true }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(event.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            Text(
                if (!premiumAllowed) "需激活后使用"
                else selected?.let { "已选择：${it.name}" } ?: if (selectedPath.isBlank()) "未选择音效" else "文件不存在：${File(selectedPath).name}",
                color = if (!premiumAllowed) SocRed else if (selected != null) AccentBlue else TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { picking = true }, enabled = premiumAllowed) {
            Text("选择", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
            enabled = premiumAllowed,
            colors = bydSwitchColors(),
        )
    }
}

@Composable
private fun LockSoundPickerDialog(
    title: String,
    choices: List<LockSoundChoice>,
    selectedPath: String,
    onDismiss: () -> Unit,
    onSelect: (LockSoundChoice) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<LockSoundChoice?>(null) }

    pendingDelete?.let { choice ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除音效", color = TextPrimary) },
            text = {
                Text(
                    "确定永久删除“${choice.name}”吗？如果解锁、锁车、哨兵或智能联动正在使用它，对应功能可能停止播放。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(choice.path)
                    },
                ) {
                    Text("删除", color = SocRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardSurfaceElevated,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary) },
        text = {
            if (choices.isEmpty()) {
                Text(
                    "WJX 音效库为空。请从在线音效库直接下载，或在这里导入音效。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(choices, key = { it.path }) { choice ->
                        val selected = choice.path == selectedPath
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) AccentGreen.copy(alpha = 0.14f) else CardSurface,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { onSelect(choice) }
                                .padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(choice.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(choice.sizeLabel, color = TextMuted, fontSize = 11.sp)
                            }
                            if (selected) {
                                Text("已选", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { pendingDelete = choice }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "删除音效",
                                    tint = SocRed,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextSecondary)
            }
        },
        containerColor = CardSurfaceElevated,
    )
}
