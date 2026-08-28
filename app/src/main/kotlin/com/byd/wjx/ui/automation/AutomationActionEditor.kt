package com.byd.wjx.ui.automation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.domain.vehicle.VehicleCommandSyntax
import com.byd.wjx.ui.theme.*
import kotlin.math.roundToInt

@Composable
internal fun ActionRow(
    index: Int,
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    onTest: (ActionDef) -> Unit,
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
                NavigateActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "url" ->
                UrlActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "delay" ->
                DelayActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "media_volume" ->
                MediaVolumeActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "outside_sound" ->
                OutsideSoundActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "inside_sound" ->
                InsideSoundActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "play_music", "play_video" ->
                AutomationMediaActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "sentry_mode" ->
                SentryModeActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "http" ->
                HttpActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            "stop_inside_sound", "stop_outside_sound", "stop_music", "stop_video", "go_home" ->
                SimpleActionControls(action = action, modifier = Modifier.weight(1f))
            "auto_door" ->
                AutoDoorActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
            else -> // "param" (default)
                ParamActionControls(action = action, onUpdate = onUpdate, modifier = Modifier.weight(1f))
        }

        // Vehicle commands and cabin sounds can use the exact saved action path for live testing.
        val canTest = (action.kind == "param" && action.command.isNotBlank()) ||
            (action.kind == "inside_sound" && action.insideSoundPath().isNotBlank())
        if (canTest) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { onTest(action) }, modifier = Modifier.size(34.dp)) {
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
    val numericSpec = remember(action.command) { vehicleNumericActionSpec(action.command) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CatalogDropdown(
            selected = numericSpec?.label
                ?: ACTION_COMMANDS.find { it.command == action.command }?.name
                ?: action.displayName,
            items = ACTION_COMMANDS.map { it.name },
            categories = ACTION_COMMANDS.map { it.category },
            modifier = Modifier.weight(1f),
            onSelect = { idx ->
                val a = ACTION_COMMANDS[idx]
                onUpdate(ActionDef(a.command, a.name))
            }
        )
        numericSpec?.let { spec ->
            Spacer(Modifier.width(6.dp))
            VehicleNumericValueChip(spec = spec) { value ->
                onUpdate(
                    action.copy(
                        command = spec.commandFor(value),
                        displayName = "${spec.label}：${spec.valueText(value)}",
                    )
                )
            }
        }
    }
}

private data class VehicleNumericActionSpec(
    val label: String,
    val current: Int,
    val min: Int,
    val max: Int,
    val unit: String,
    val commandFor: (Int) -> String,
    val valueLabels: List<String>? = null,
) {
    fun valueText(value: Int): String =
        valueLabels?.getOrNull(value - min) ?: "$value$unit"
}

private fun vehicleNumericActionSpec(command: String): VehicleNumericActionSpec? {
    val parsed = VehicleCommandSyntax.parseNumeric(command) ?: return null
    return when (parsed) {
        is VehicleCommandSyntax.WindowPosition -> {
            val label = when (parsed.target) {
                VehicleCommandSyntax.WindowTarget.DRIVER -> "主驾车窗位置"
                VehicleCommandSyntax.WindowTarget.PASSENGER -> "副驾车窗位置"
                VehicleCommandSyntax.WindowTarget.REAR_LEFT -> "后左车窗位置"
                VehicleCommandSyntax.WindowTarget.REAR_RIGHT -> "后右车窗位置"
                VehicleCommandSyntax.WindowTarget.ALL -> "全部车窗位置"
                VehicleCommandSyntax.WindowTarget.FRONT -> "前排车窗位置"
                VehicleCommandSyntax.WindowTarget.REAR -> "后排车窗位置"
            }
            VehicleNumericActionSpec(label, parsed.value, 0, 100, "%", parsed::commandFor)
        }
        is VehicleCommandSyntax.Temperature -> VehicleNumericActionSpec(
            if (parsed.passenger) "副驾温度" else "主驾温度",
            parsed.value,
            16,
            30,
            "℃",
            parsed::commandFor,
        )
        is VehicleCommandSyntax.FanLevel ->
            VehicleNumericActionSpec("风量档位", parsed.value, 0, 7, "档", parsed::commandFor)
        is VehicleCommandSyntax.SunshadePosition ->
            VehicleNumericActionSpec("遮阳帘位置", parsed.value, 0, 100, "%", parsed::commandFor)
        is VehicleCommandSyntax.AmbientBrightness ->
            VehicleNumericActionSpec("氛围灯亮度", parsed.value, 1, 4, "档", parsed::commandFor)
        is VehicleCommandSyntax.HeadlightHeight -> VehicleNumericActionSpec(
            label = "前照灯高度",
            current = parsed.value,
            min = 0,
            max = 5,
            unit = "",
            commandFor = parsed::commandFor,
            valueLabels = listOf("最近", "较近", "近", "远", "较远", "最远"),
        )
        is VehicleCommandSyntax.Massage -> VehicleNumericActionSpec(
            "${parsed.seat.token}按摩：${parsed.mode.token}",
            parsed.value,
            1,
            3,
            "档",
            parsed::commandFor,
        )
    }
}

@Composable
private fun VehicleNumericValueChip(
    spec: VehicleNumericActionSpec,
    onValue: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Text(
        text = spec.valueText(spec.current),
        color = AccentTeal,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, AccentTeal.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable { editing = true }
            .padding(horizontal = 8.dp, vertical = 7.dp),
    )

    if (editing) {
        var pending by remember(spec.current) { mutableIntStateOf(spec.current) }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = CardSurface,
            title = { Text(spec.label, color = TextPrimary, fontSize = 16.sp) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        spec.valueText(pending),
                        color = AccentTeal,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Slider(
                        value = pending.toFloat(),
                        onValueChange = { pending = it.roundToInt().coerceIn(spec.min, spec.max) },
                        valueRange = spec.min.toFloat()..spec.max.toFloat(),
                        steps = (spec.max - spec.min - 1).coerceAtLeast(0),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onValue(pending); editing = false }) {
                    Text("确定", color = AccentGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
        )
    }
}

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
