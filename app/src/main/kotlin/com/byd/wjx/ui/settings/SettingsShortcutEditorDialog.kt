package com.byd.wjx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.cluster.KeyShortcutActionType
import com.byd.wjx.cluster.KeyShortcutPressType
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun KeyShortcutEditorDialog(
    editor: KeyShortcutEditorState,
    sounds: List<SoundLibrary.SoundItem>,
    onEditorChanged: (KeyShortcutEditorState) -> Unit,
    onLearnKey: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (KeyShortcutEditorState) -> Unit,
) {
    var error by remember(editor.id) { mutableStateOf<String?>(null) }
    val selectedKey = if (editor.keyCode > 0) steeringButtonLabel(editor.keyCode) else "未识别按键"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按键快捷操作") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("动作", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyShortcutChoiceChip(
                        label = "播放音效",
                        selected = editor.actionType == KeyShortcutActionType.PlaySound,
                        onClick = {
                            onEditorChanged(editor.copy(actionType = KeyShortcutActionType.PlaySound))
                        },
                    )
                    KeyShortcutChoiceChip(
                        label = "实时喊话",
                        selected = editor.actionType == KeyShortcutActionType.RealtimeHailing,
                        onClick = {
                            onEditorChanged(editor.copy(actionType = KeyShortcutActionType.RealtimeHailing))
                        },
                    )
                    KeyShortcutChoiceChip(
                        label = "后流媒体",
                        selected = editor.actionType == KeyShortcutActionType.RearStream,
                        onClick = {
                            onEditorChanged(editor.copy(actionType = KeyShortcutActionType.RearStream))
                        },
                    )
                }
                Text("触发", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyShortcutPressType.entries.forEach { pressType ->
                        KeyShortcutChoiceChip(
                            label = pressType.editorLabel(),
                            selected = editor.pressType == pressType,
                            onClick = { onEditorChanged(editor.copy(pressType = pressType)) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("按键", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(selectedKey, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Button(
                        onClick = onLearnKey,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceElevated),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 0.dp,
                        ),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("识别", color = TextPrimary, fontSize = 12.sp)
                    }
                }
                if (editor.actionType == KeyShortcutActionType.PlaySound) {
                    ShortcutSoundPicker(editor, sounds, onEditorChanged)
                }
                error?.let { Text(it, color = Color(0xFFE85D5D), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = validateKeyShortcutEditor(editor)
                    if (error == null) onSave(editor)
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ShortcutSoundPicker(
    editor: KeyShortcutEditorState,
    sounds: List<SoundLibrary.SoundItem>,
    onEditorChanged: (KeyShortcutEditorState) -> Unit,
) {
    Text("音效", color = TextSecondary, fontSize = 12.sp)
    if (sounds.isEmpty()) {
        Text("本地暂无音效", color = TextSecondary, fontSize = 12.sp)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sounds, key = { it.file.absolutePath }) { sound ->
                KeyShortcutChoiceChip(
                    label = sound.name,
                    selected = editor.soundPath == sound.file.absolutePath,
                    onClick = {
                        onEditorChanged(
                            editor.copy(
                                soundPath = sound.file.absolutePath,
                                soundName = sound.name,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun KeyShortcutChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
            containerColor = CardSurface,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected,
        ),
    )
}

internal fun validateKeyShortcutEditor(editor: KeyShortcutEditorState): String? = when {
    editor.keyCode <= 0 -> "请先识别按键"
    editor.actionType == KeyShortcutActionType.PlaySound && editor.soundPath.isBlank() ->
        "请选择一个音效"
    else -> null
}

private fun KeyShortcutPressType.editorLabel(): String = when (this) {
    KeyShortcutPressType.Short -> "按一下"
    KeyShortcutPressType.Double -> "按两下"
    KeyShortcutPressType.Long -> "长按"
}
