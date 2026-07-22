package com.byd.wjx.ui.settings

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.cluster.ClusterMode
import com.byd.wjx.cluster.ClusterProjectionManager
import com.byd.wjx.cluster.ClusterProjectionPreferences
import com.byd.wjx.cluster.CENTER_OFFSET_PCT
import com.byd.wjx.cluster.MAX_OFFSET_PCT
import com.byd.wjx.cluster.MAX_PROJECTION_PCT
import com.byd.wjx.cluster.MIN_OFFSET_PCT
import com.byd.wjx.cluster.MIN_PROJECTION_PCT
import com.byd.wjx.cluster.MIN_SCALE_PCT
import com.byd.wjx.cluster.MAX_SCALE_PCT
import com.byd.wjx.cluster.DEFAULT_SCALE_PCT
import com.byd.wjx.cluster.NAVI_PACKAGE
import com.byd.wjx.cluster.ProjectionLayout
import com.byd.wjx.cluster.KeyShortcutActionType
import com.byd.wjx.cluster.KeyShortcutBinding
import com.byd.wjx.cluster.KeyShortcutPressType
import com.byd.wjx.cluster.KeyShortcutSettings
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContactSupport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.byd.wjx.cluster.DEFAULT_TRIGGER_KEYCODE
import com.byd.wjx.cluster.SteeringWheelKeyService
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import com.byd.wjx.R
import com.byd.wjx.audio.ExteriorChannelMode
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.components.AppLaunchPickerDialog
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.*
import java.io.File
import java.net.URL


internal data class KeyShortcutEditorState(
    val id: String = java.util.UUID.randomUUID().toString(),
    val keyCode: Int = 0,
    val pressType: KeyShortcutPressType = KeyShortcutPressType.Short,
    val actionType: KeyShortcutActionType = KeyShortcutActionType.PlaySound,
    val soundPath: String = "",
    val soundName: String = "",
    val enabled: Boolean = true,
)

internal fun KeyShortcutBinding.toEditorState(): KeyShortcutEditorState =
    KeyShortcutEditorState(
        id = id,
        keyCode = keyCode,
        pressType = pressType,
        actionType = actionType,
        soundPath = soundPath,
        soundName = soundName,
        enabled = enabled,
    )

internal fun KeyShortcutEditorState.toBinding(): KeyShortcutBinding =
    KeyShortcutBinding(
        id = id,
        keyCode = keyCode,
        pressType = pressType,
        actionType = actionType,
        soundPath = soundPath,
        soundName = soundName,
        enabled = enabled,
    )

@Composable
internal fun KeyShortcutSection(
    bindings: List<KeyShortcutBinding>,
    activationAllowed: Boolean,
    onAdd: () -> Unit,
    onEdit: (KeyShortcutBinding) -> Unit,
    onEnabledChanged: (KeyShortcutBinding, Boolean) -> Unit,
    onDelete: (KeyShortcutBinding) -> Unit,
) {
    SectionHeader(text = "按键快捷操作")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("方向盘快捷键", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (activationAllowed) "${bindings.size} 条规则" else "试用已结束，激活后可用",
                        color = if (activationAllowed) TextSecondary else SocRed,
                        fontSize = 12.sp,
                    )
                }
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("添加", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (bindings.isEmpty()) {
                Text("暂无快捷操作", color = TextSecondary, fontSize = 12.sp)
            } else {
                bindings.forEach { binding ->
                    KeyShortcutRow(
                        binding = binding,
                        onClick = { onEdit(binding) },
                        onEnabledChanged = { onEnabledChanged(binding, it) },
                        onDelete = { onDelete(binding) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyShortcutRow(
    binding: KeyShortcutBinding,
    onClick: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val keyLabel = steeringButtonLabel(binding.keyCode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = when (binding.actionType) {
                    KeyShortcutActionType.PlaySound -> "按键播放音效"
                    KeyShortcutActionType.RealtimeHailing -> "按键实时喊话"
                    KeyShortcutActionType.StreamingRearMirror -> "按键流媒体后视镜"
                },
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(keyLabel)
                    append(" · ")
                    append(binding.pressType.label)
                    if (binding.actionType == KeyShortcutActionType.PlaySound) {
                        append(" · ")
                        append(binding.soundName.ifBlank { "未选择音效" })
                    }
                },
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = binding.enabled,
            onCheckedChange = onEnabledChanged,
            colors = bydSwitchColors(),
        )
        TextButton(onClick = onDelete) {
            Text("删除", color = Color(0xFFE85D5D), fontSize = 12.sp)
        }
    }
}

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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("动作", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyShortcutChoiceChip(
                        label = "播放音效",
                        selected = editor.actionType == KeyShortcutActionType.PlaySound,
                        onClick = { onEditorChanged(editor.copy(actionType = KeyShortcutActionType.PlaySound)) },
                    )
                    KeyShortcutChoiceChip(
                        label = "实时喊话",
                        selected = editor.actionType == KeyShortcutActionType.RealtimeHailing,
                        onClick = { onEditorChanged(editor.copy(actionType = KeyShortcutActionType.RealtimeHailing)) },
                    )
                    KeyShortcutChoiceChip(
                        label = "流媒体后视镜",
                        selected = editor.actionType == KeyShortcutActionType.StreamingRearMirror,
                        onClick = {
                            onEditorChanged(
                                editor.copy(actionType = KeyShortcutActionType.StreamingRearMirror),
                            )
                        },
                    )
                }

                Text("触发", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyShortcutChoiceChip(
                        label = "按一下",
                        selected = editor.pressType == KeyShortcutPressType.Short,
                        onClick = { onEditorChanged(editor.copy(pressType = KeyShortcutPressType.Short)) },
                    )
                    KeyShortcutChoiceChip(
                        label = "按两下",
                        selected = editor.pressType == KeyShortcutPressType.Double,
                        onClick = { onEditorChanged(editor.copy(pressType = KeyShortcutPressType.Double)) },
                    )
                    KeyShortcutChoiceChip(
                        label = "长按",
                        selected = editor.pressType == KeyShortcutPressType.Long,
                        onClick = { onEditorChanged(editor.copy(pressType = KeyShortcutPressType.Long)) },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("识别", color = TextPrimary, fontSize = 12.sp)
                    }
                }

                if (editor.actionType == KeyShortcutActionType.PlaySound) {
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
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                error?.let {
                    Text(it, color = Color(0xFFE85D5D), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        editor.keyCode <= 0 -> "请先识别按键"
                        editor.actionType == KeyShortcutActionType.PlaySound && editor.soundPath.isBlank() -> "请选择一个音效"
                        else -> null
                    }
                    if (error == null) onSave(editor)
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun KeyShortcutChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
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

/** App label for the cluster picker row; falls back to the package name when not installed/resolvable. */
internal fun resolveAppLabel(context: Context, pkg: String): String =
    try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

/** Human label for a steering-wheel keycode: known name, else "button (code N)". */
@Composable
internal fun steeringButtonLabel(keyCode: Int): String {
    val res = com.byd.wjx.cluster.knownButtonNameRes(keyCode)
    return if (res != 0) stringResource(res)
    else stringResource(R.string.steering_button_unknown, keyCode)
}

private sealed interface LearnUiState {
    data object Waiting : LearnUiState
    data class Rejected(val keyCode: Int) : LearnUiState
    data class Captured(val keyCode: Int) : LearnUiState
    data object TimedOut : LearnUiState
}

/**
 * Learn-the-button dialog. Puts SteeringWheelKeyService into learn mode while open and collects the
 * captured key from its StateFlow (same process). States: Waiting → (Rejected loops) → Captured
 * (confirm) / TimedOut. learnMode is always cleared on dispose.
 */
@Composable
internal fun LearnButtonDialog(
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    preferLongPress: Boolean = false,
) {
    var state by remember { mutableStateOf<LearnUiState>(LearnUiState.Waiting) }

    // Clear learn mode AND the captured value on close, so a later reopen can't latch a stale capture.
    DisposableEffect(Unit) {
        onDispose {
            SteeringWheelKeyService.learnMode = false
            SteeringWheelKeyService.learnPreferLongPress = false
            SteeringWheelKeyService.capturedKey.value = null
        }
    }

    // Arm capture, then react ONLY to fresh emissions. The StateFlow replays its current value to a
    // new collector, so we reset it to null first and filterNotNull() drops both that reset and any
    // stale value left from a previous session — reopening therefore always starts at Waiting.
    LaunchedEffect(Unit) {
        SteeringWheelKeyService.capturedKey.value = null
        SteeringWheelKeyService.learnPreferLongPress = preferLongPress
        SteeringWheelKeyService.learnMode = true
        SteeringWheelKeyService.capturedKey
            .filterNotNull()
            .collect { r ->
                state = if (r.assignable) {
                    SteeringWheelKeyService.learnMode = false
                    LearnUiState.Captured(r.keyCode)
                } else {
                    LearnUiState.Rejected(r.keyCode)
                }
            }
    }

    // Timeout while still waiting/rejected (no assignable capture yet).
    LaunchedEffect(state) {
        if (state is LearnUiState.Waiting || state is LearnUiState.Rejected) {
            delay(10_000)
            if (state is LearnUiState.Waiting || state is LearnUiState.Rejected) {
                SteeringWheelKeyService.learnMode = false
                state = LearnUiState.TimedOut
            }
        }
    }

    // "Again": re-arm. The collector above keeps running (keyed on Unit), so just reset + Waiting.
    val restart = {
        SteeringWheelKeyService.capturedKey.value = null
        SteeringWheelKeyService.learnPreferLongPress = preferLongPress
        SteeringWheelKeyService.learnMode = true
        state = LearnUiState.Waiting
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.learn_button_dialog_title)) },
        text = {
            when (val s = state) {
                is LearnUiState.Waiting -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentGreen)
                    Text(stringResource(R.string.learn_button_waiting))
                }
                is LearnUiState.Rejected -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.learn_button_rejected))
                    Text(steeringButtonLabel(s.keyCode), color = TextSecondary, fontSize = 12.sp)
                }
                is LearnUiState.Captured -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.learn_button_captured))
                    Text(
                        "${steeringButtonLabel(s.keyCode)} (${s.keyCode})",
                        color = TextPrimary, fontWeight = FontWeight.Medium,
                    )
                }
                is LearnUiState.TimedOut -> Text(stringResource(R.string.learn_button_timeout))
            }
        },
        confirmButton = {
            when (val s = state) {
                is LearnUiState.Captured -> TextButton(onClick = { onSave(s.keyCode) }) {
                    Text(stringResource(R.string.learn_button_save))
                }
                is LearnUiState.TimedOut -> TextButton(onClick = restart) {
                    Text(stringResource(R.string.learn_button_again))
                }
                else -> {}
            }
        },
        dismissButton = {
            when (state) {
                is LearnUiState.Captured -> TextButton(onClick = restart) {
                    Text(stringResource(R.string.learn_button_again))
                }
                else -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.learn_button_cancel))
                }
            }
        },
    )
}

@Composable
internal fun ClusterSizeSlider(
    label: String,
    pct: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    onFinished: () -> Unit,
    min: Int = MIN_PROJECTION_PCT,
    max: Int = MAX_PROJECTION_PCT,
    description: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (enabled) TextPrimary else TextMuted, fontSize = 14.sp)
            Text(
                "$pct%",
                color = if (enabled) AccentGreen else TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (description != null) {
            Text(description, color = TextSecondary, fontSize = 12.sp)
        }
        Slider(
            value = pct.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onFinished,
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min) / 2 - 1,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
            ),
        )
    }
}
