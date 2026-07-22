package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun EditorDialog(
    editing: EditingRule,
    places: List<PlaceEntity>,
    editorError: String?,
    onUpdate: (EditingRule.() -> EditingRule) -> Unit,
    onSave: () -> Unit,
    onTestAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var repeatIntervalInput by rememberSaveable(editing.id, editing.isNew) {
        mutableStateOf(editing.repeatIntervalSeconds.toString())
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.88f)
                .background(CardSurface.copy(alpha = 0.98f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.74f), RoundedCornerShape(24.dp))
        ) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (editing.isNew) stringResource(R.string.automation_editor_new_rule_title) else editing.name,
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Close, "close", tint = TextMuted)
                }
            }
            HorizontalDivider(color = CardBorder)

            // Two columns
            Row(modifier = Modifier.weight(1f)) {
                // Left: Name + Triggers
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp, 12.dp)
                ) {
                    OutlinedTextField(
                        value = editing.name,
                        onValueChange = { v -> onUpdate { copy(name = v) } },
                        placeholder = { Text(stringResource(R.string.automation_rule_name_placeholder), color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentGreen
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(14.dp))
                    SectionHeader(stringResource(R.string.automation_section_when))

                    // AND/OR toggle
                    Row {
                        LogicChip(stringResource(R.string.automation_logic_and), editing.triggerLogic == "AND") {
                            onUpdate { copy(triggerLogic = "AND") }
                        }
                        LogicChip(stringResource(R.string.automation_logic_or), editing.triggerLogic == "OR") {
                            onUpdate { copy(triggerLogic = "OR") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    editing.triggers.forEachIndexed { idx, trigger ->
                        TriggerRow(
                            index = idx,
                            trigger = trigger,
                            places = places,
                            onUpdate = { newTrigger ->
                                onUpdate {
                                    copy(triggers = triggers.toMutableList().apply { set(idx, newTrigger) })
                                }
                            },
                            onMoveUp = if (idx > 0) {
                                { onUpdate { copy(triggers = triggers.moveItem(idx, up = true)) } }
                            } else null,
                            onMoveDown = if (idx < editing.triggers.lastIndex) {
                                { onUpdate { copy(triggers = triggers.moveItem(idx, up = false)) } }
                            } else null,
                            onDelete = {
                                onUpdate {
                                    copy(triggers = triggers.toMutableList().apply { removeAt(idx) })
                                }
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    if (editing.triggers.size < 5) {
                        AddTriggerButton(
                            places = places,
                            onAddParam = {
                                val p = TRIGGER_PARAMS.first()
                                onUpdate {
                                    copy(triggers = triggers + TriggerDef(p.param, p.name, ">", "0", p.name))
                                }
                            },
                            onAddPlace = { place ->
                                onUpdate { copy(triggers = triggers + newPlaceTrigger(place, context)) }
                            },
                            onAddTimeOfDay = {
                                onUpdate { copy(triggers = triggers + newTimeOfDayTrigger(context)) }
                            },
                            onAddSchedule = {
                                onUpdate { copy(triggers = triggers + newScheduleTrigger()) }
                            },
                            onAddInterval = {
                                onUpdate { copy(triggers = triggers + newIntervalTrigger()) }
                            },
                            onAddBoardVehicle = {
                                onUpdate { copy(triggers = triggers + newBoardVehicleTrigger()) }
                            },
                            onAddServiceStart = {
                                onUpdate { copy(triggers = triggers + newServiceStartTrigger(context)) }
                            },
                            onAddNetworkAvailable = {
                                onUpdate { copy(triggers = triggers + newNetworkAvailableTrigger(context)) }
                            }
                        )
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(CardBorder)
                )

                // Right: Actions + Settings
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp, 12.dp)
                ) {
                    SectionHeader(stringResource(R.string.automation_section_then))

                    editing.actions.forEachIndexed { idx, action ->
                        ActionRow(
                            index = idx,
                            action = action,
                            places = places,
                            onUpdate = { newAction ->
                                onUpdate {
                                    copy(actions = actions.toMutableList().apply { set(idx, newAction) })
                                }
                            },
                            onTest = onTestAction,
                            onMoveUp = if (idx > 0) {
                                { onUpdate { copy(actions = actions.moveItem(idx, up = true)) } }
                            } else null,
                            onMoveDown = if (idx < editing.actions.lastIndex) {
                                { onUpdate { copy(actions = actions.moveItem(idx, up = false)) } }
                            } else null,
                            onDelete = {
                                onUpdate {
                                    copy(actions = actions.toMutableList().apply { removeAt(idx) })
                                }
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    if (editing.actions.size < 10) {
                        AddActionButton { action ->
                            onUpdate { copy(actions = actions + action) }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionHeader(stringResource(R.string.automation_section_on_exit))
                    Text(
                        stringResource(R.string.automation_section_on_exit_hint),
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    editing.exitActions.forEachIndexed { idx, action ->
                        ActionRow(
                            index = idx,
                            action = action,
                            places = places,
                            onUpdate = { newAction ->
                                onUpdate {
                                    copy(exitActions = exitActions.toMutableList().apply { set(idx, newAction) })
                                }
                            },
                            onTest = onTestAction,
                            onMoveUp = if (idx > 0) {
                                { onUpdate { copy(exitActions = exitActions.moveItem(idx, up = true)) } }
                            } else null,
                            onMoveDown = if (idx < editing.exitActions.lastIndex) {
                                { onUpdate { copy(exitActions = exitActions.moveItem(idx, up = false)) } }
                            } else null,
                            onDelete = {
                                onUpdate {
                                    copy(exitActions = exitActions.toMutableList().apply { removeAt(idx) })
                                }
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    if (editing.exitActions.size < 10) {
                        AddActionButton { action ->
                            onUpdate { copy(exitActions = exitActions + action) }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionHeader(stringResource(R.string.automation_section_settings))

                    // Cooldown
                    SettingRow(stringResource(R.string.automation_setting_cooldown)) {
                        OutlinedTextField(
                            value = editing.cooldownSeconds.toString(),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { sec ->
                                    onUpdate {
                                        copy(
                                            cooldownSeconds = sec.coerceIn(
                                                AUTOMATION_COOLDOWN_MIN_SECONDS,
                                                AUTOMATION_COOLDOWN_MAX_SECONDS
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.width(70.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen, unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentGreen
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.automation_setting_unit_sec), fontSize = 12.sp, color = TextMuted)
                        Spacer(Modifier.width(6.dp))
                        Text("0-60", fontSize = 11.sp, color = TextMuted)
                    }
                    Spacer(Modifier.height(4.dp))

                    SettingRow(stringResource(R.string.automation_setting_repeat_interval)) {
                        OutlinedTextField(
                            value = repeatIntervalInput,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.all(Char::isDigit)) {
                                    repeatIntervalInput = value.take(4)
                                    val seconds = repeatIntervalInput.toIntOrNull() ?: 0
                                    onUpdate {
                                        copy(repeatIntervalSeconds = seconds.coerceIn(0, AUTOMATION_REPEAT_MAX_SECONDS))
                                    }
                                }
                            },
                            modifier = Modifier.width(78.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentGreen,
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("秒", fontSize = 12.sp, color = TextMuted)
                        Spacer(Modifier.width(6.dp))
                        Text("0=关闭", fontSize = 11.sp, color = TextMuted)
                    }
                    Spacer(Modifier.height(4.dp))

                    // Require park
                    SettingRow(stringResource(R.string.automation_setting_park_only)) {
                        Switch(
                            checked = editing.requirePark,
                            onCheckedChange = { v -> onUpdate { copy(requirePark = v) } },
                            colors = bydSwitchColors(),
                        )
                    }

                    // Confirm before execute
                    SettingRow(stringResource(R.string.automation_setting_confirm_before)) {
                        Switch(
                            checked = editing.confirmBeforeExecute,
                            onCheckedChange = { v -> onUpdate { copy(confirmBeforeExecute = v) } },
                            colors = bydSwitchColors(),
                        )
                    }

                    // Fire once per trip
                    SettingRow(stringResource(R.string.automation_setting_once_per_trip)) {
                        Switch(
                            checked = editing.fireOncePerTrip,
                            onCheckedChange = { v -> onUpdate { copy(fireOncePerTrip = v) } },
                            colors = bydSwitchColors(),
                        )
                    }
                }
            }

            // Footer
            HorizontalDivider(color = CardBorder)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp, 10.dp)
            ) {
                editorError?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    ) { Text(stringResource(R.string.automation_cancel_button)) }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp),
                        enabled = editing.hasMinimumSaveFields()
                    ) { Text(stringResource(R.string.automation_save_button), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// --- Trigger Row ---

/** Up/down reorder arrows shared by trigger and action rows. A null callback = boundary, shown dimmed and disabled. */
@Composable
internal fun ReorderArrows(onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?) {
    IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Outlined.KeyboardArrowUp, "上移",
            tint = TextSecondary.copy(alpha = if (onMoveUp != null) 1f else 0.25f), modifier = Modifier.size(18.dp))
    }
    IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Outlined.KeyboardArrowDown, "下移",
            tint = TextSecondary.copy(alpha = if (onMoveDown != null) 1f else 0.25f), modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun AutoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.height(44.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = AccentContent,
            containerColor = CardSurfaceElevated.copy(alpha = 0.86f),
            labelColor = TextSecondary
        ),
        shape = RoundedCornerShape(999.dp),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = CardBorder.copy(alpha = 0.58f),
            selectedBorderColor = Color.Transparent,
            enabled = true, selected = selected
        )
    )
}

@Composable
private fun LogicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) AccentGreen else CardSurface,
                RoundedCornerShape(10.dp)
            )
            .border(if (selected) 0.dp else 1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(10.dp, 5.dp)
    ) {
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) AccentContent else TextSecondary
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = AccentGreen,
        modifier = Modifier
            .background(AccentGreen.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
internal fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}
