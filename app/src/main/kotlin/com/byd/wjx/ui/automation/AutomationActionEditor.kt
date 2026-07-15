package com.byd.wjx.ui.automation

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.components.AppLaunchPickerDialog
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.*
import org.json.JSONObject

@Composable
internal fun ActionRow(
    index: Int,
    action: ActionDef,
    places: List<PlaceEntity>,
    onUpdate: (ActionDef) -> Unit,
    onTest: (String) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
            .border(1.dp, AccentTeal.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentTeal,
            modifier = Modifier.width(16.dp))

        when (action.kind) {
            "notification_silent", "notification_sound" ->
                NotificationActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "app_launch" ->
                AppLaunchActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "call" ->
                CallActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "navigate" ->
                NavigateActionControls(action = action, places = places, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "url" ->
                UrlActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "delay" ->
                DelayActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "media_volume" ->
                MediaVolumeActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "outside_sound" ->
                OutsideSoundActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "play_music", "play_video" ->
                AutomationMediaActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "sentry_mode" ->
                SentryModeActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "http" ->
                HttpActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "stop_outside_sound", "stop_music", "stop_video", "go_home" ->
                SimpleActionControls(action = action, modifier = Modifier.weight(1f))
            "auto_door" ->
                AutoDoorActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            else -> // "param" (default)
                ParamActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
        }

        // Fire this vehicle command immediately for live testing.
        // Only for param (vehicle) actions; result is shown via Toast + logcat status line.
        if (action.kind == "param" && action.command.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { onTest(action.command) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.PlayArrow, "立即执行", tint = AccentGreen, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        ReorderArrows(onMoveUp = onMoveUp, onMoveDown = onMoveDown)
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Close, "delete", tint = Color(0xFFEF4444).copy(alpha = 0.72f), modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ParamActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    CatalogDropdown(
        selected = ACTION_COMMANDS.find { it.command == action.command }?.localizedName(context) ?: action.displayName,
        items = ACTION_COMMANDS.map { it.localizedName(context) },
        categories = ACTION_COMMANDS.map { it.localizedCategory(context) },
        modifier = modifier,
        onSelect = { idx ->
            val a = ACTION_COMMANDS[idx]
            onUpdate(ActionDef(a.command, a.localizedName(context)))
        }
    )
}

// Delay option keys — labels are resolved at runtime via stringResource
private val DELAY_OPTION_MS = listOf(500L, 1000L, 2000L, 3000L, 5000L, 10000L)

@Composable
private fun DelayActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMs = action.payload?.toLongOrNull() ?: 1000L
    val label0_5s = stringResource(R.string.automation_delay_0_5s)
    val label1s = stringResource(R.string.automation_delay_1s)
    val label2s = stringResource(R.string.automation_delay_2s)
    val label3s = stringResource(R.string.automation_delay_3s)
    val label5s = stringResource(R.string.automation_delay_5s)
    val label10s = stringResource(R.string.automation_delay_10s)
    val delayLabels = listOf(
        500L to label0_5s,
        1000L to label1s,
        2000L to label2s,
        3000L to label3s,
        5000L to label5s,
        10000L to label10s
    )
    // Pre-build display names for onClick lambdas (stringResource cannot be called in non-Composable onClick)
    val delayDisplayNames = delayLabels.associate { (ms, lbl) ->
        ms to stringResource(R.string.automation_delay_display_name, lbl)
    }
    val currentLabel = delayLabels.find { it.first == currentMs }?.second ?: "${currentMs}ms"
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Pause,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.automation_action_delay_label), fontSize = 13.sp, color = TextMuted)
        Spacer(Modifier.width(6.dp))
        Box {
            Text(
                currentLabel,
                fontSize = 13.sp,
                color = AccentTeal,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(8.dp, 6.dp)
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                delayLabels.forEach { (ms, label) ->
                    DropdownMenuItem(
                        text = { Text(label, fontSize = 13.sp) },
                        onClick = {
                            expanded = false
                            onUpdate(action.copy(
                                payload = ms.toString(),
                                displayName = delayDisplayNames[ms] ?: label
                            ))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SentryModeActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = action.payload != "off"
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text("哨兵模式", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        AutoChip(label = "开启", selected = enabled) { onUpdate(action.withSentryMode(true)) }
        Spacer(Modifier.width(6.dp))
        AutoChip(label = "关闭", selected = !enabled) { onUpdate(action.withSentryMode(false)) }
    }
}

@Composable
private fun AutoDoorActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = action.autoDoorActionOption() ?: AUTO_DOOR_ACTIONS.first()
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Bolt,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.automation_action_auto_door_label), fontSize = 13.sp, color = TextMuted)
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            Text(
                selected.label,
                fontSize = 13.sp,
                color = AccentTeal,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(8.dp, 7.dp),
                maxLines = 1
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AUTO_DOOR_ACTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, fontSize = 13.sp) },
                        onClick = {
                            expanded = false
                            onUpdate(action.withAutoDoorAction(option))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleActionControls(
    action: ActionDef,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Bolt, null, tint = AccentTeal, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(action.displayName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HttpActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            Text(
                action.httpMethod(),
                color = AccentTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(CardSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                methods.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method, fontSize = 13.sp) },
                        onClick = {
                            expanded = false
                            onUpdate(action.withHttp(method, action.httpUrl()))
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(7.dp))
        OutlinedTextField(
            value = action.httpUrl(),
            onValueChange = { onUpdate(action.withHttp(action.httpMethod(), it)) },
            placeholder = { Text("https://", fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentTeal,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            modifier = Modifier.weight(1f),
        )
    }
}

// --- Catalog Dropdown (with category headers) ---

@Composable
internal fun CatalogDropdown(
    selected: String,
    items: List<String>,
    categories: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Text(
            selected,
            fontSize = 13.sp, color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(8.dp, 7.dp),
            maxLines = 1
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxHeight(0.5f)
        ) {
            var lastCat = ""
            items.forEachIndexed { idx, item ->
                val cat = categories[idx]
                if (cat != lastCat) {
                    lastCat = cat
                    DropdownMenuItem(
                        text = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted) },
                        onClick = {},
                        enabled = false
                    )
                }
                DropdownMenuItem(
                    text = { Text(item, fontSize = 13.sp) },
                    onClick = { expanded = false; onSelect(idx) }
                )
            }
        }
    }
}

// --- Journal Dialog ---


@Composable
internal fun AddActionButton(
    onAddParam: () -> Unit,
    onAddNotification: (silent: Boolean) -> Unit,
    onAddAppLaunch: () -> Unit,
    onAddCall: () -> Unit,
    onAddNavigate: () -> Unit,
    onAddUrl: () -> Unit,
    onAddDelay: () -> Unit,
    onAddMediaVolume: () -> Unit,
    onAddOutsideSound: () -> Unit,
    onAddStopOutsideSound: () -> Unit,
    onAddGoHome: () -> Unit,
    onAddHttp: () -> Unit,
    onAddPlayMusic: () -> Unit,
    onAddStopMusic: () -> Unit,
    onAddPlayVideo: () -> Unit,
    onAddStopVideo: () -> Unit,
    onAddSentryMode: () -> Unit,
    onAddAutoDoor: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showOverlayPrompt by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceElevated.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                .border(1.dp, AccentTeal.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .clickable { menuExpanded = true }
                .padding(12.dp, 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.automation_add_action_button), fontSize = 12.sp, color = TextMuted)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_dplus_command), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddParam() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_notification_silent), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddNotification(true) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_notification_sound), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddNotification(false)
                    if (!android.provider.Settings.canDrawOverlays(context)) {
                        showOverlayPrompt = true
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_app_launch), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddAppLaunch() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_call), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddCall() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_navigate), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddNavigate() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_url), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddUrl() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_delay), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddDelay() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_media_volume), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddMediaVolume() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_outside_sound), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddOutsideSound() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_outside_sound), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddStopOutsideSound() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_go_home), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddGoHome() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_http), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddHttp() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_play_music), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddPlayMusic() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_music), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddStopMusic() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_play_video), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddPlayVideo() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_stop_video), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddStopVideo() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_sentry_mode), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddSentryMode() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_action_auto_door), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddAutoDoor() }
            )
        }
    }

    if (showOverlayPrompt) {
        AlertDialog(
            onDismissRequest = { showOverlayPrompt = false },
            containerColor = CardSurface,
            title = { Text(stringResource(R.string.automation_overlay_permission_title), color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    stringResource(R.string.automation_overlay_permission_text),
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayPrompt = false
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }) {
                    Text(stringResource(R.string.automation_overlay_open_settings), color = AccentGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPrompt = false }) {
                    Text(stringResource(R.string.automation_overlay_later), color = TextSecondary)
                }
            }
        )
    }
}

internal fun newDelayAction(context: android.content.Context): ActionDef = ActionDef(
    command = "delay_1000",
    displayName = localized("延迟 1 秒", "延迟 1 秒", "延迟 1 秒", context),
    kind = "delay",
    payload = "1000"
)

internal fun newMediaVolumeAction(context: android.content.Context): ActionDef = ActionDef(
    command = "media_volume",
    displayName = localized("媒体音量：2", "媒体音量：2", "媒体音量：2", context),
    kind = "media_volume",
    payload = "2"
)
