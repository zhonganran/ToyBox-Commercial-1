package com.byd.wjx.ui.automation

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.byd.wjx.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.data.automation.ScheduleSpec
import com.byd.wjx.data.automation.minuteToHHmm
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.components.AppLaunchPickerDialog
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutomationScreen(
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filtered = remember(state.rules, state.filter) {
        when (state.filter) {
            RuleFilter.ALL -> state.rules
            RuleFilter.ENABLED -> state.rules.filter { it.enabled }
            RuleFilter.DISABLED -> state.rules.filter { !it.enabled }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 22.dp),
    ) {
        AutomationSceneHeader(
            filter = state.filter,
            onFilterChanged = viewModel::setFilter,
            onOpenJournal = { viewModel.showJournal() },
            onCreate = { viewModel.openNewRule() },
        )
        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            LinkageEmptyState(
                hasRules = state.rules.isNotEmpty(),
                onCreate = { viewModel.openNewRule() },
            )
        } else {
            val featuredRule = filtered.firstOrNull { it.enabled } ?: filtered.first()
            FeaturedAutomationRule(
                rule = featuredRule,
                onClick = { viewModel.openEditRule(featuredRule) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.42f)
                    .height(116.dp),
            )
            AutomationConnector(modifier = Modifier.fillMaxWidth().height(54.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(198.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(filtered, key = { it.id }) { rule ->
                    SceneRuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleEnabled(rule) },
                        onClick = { viewModel.openEditRule(rule) },
                        onEdit = { viewModel.openEditRule(rule) },
                        onDuplicate = { viewModel.duplicateRule(rule) },
                        onDelete = { viewModel.requestDelete(rule.id) },
                        modifier = Modifier.width(354.dp).fillMaxHeight(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            AutomationSummaryStrip(
                total = state.rules.size,
                enabled = state.rules.count { it.enabled },
                logs = state.logs,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
            Spacer(Modifier.height(12.dp))
            RecentAutomationLogs(
                logs = state.logs.take(3),
                onOpenJournal = { viewModel.showJournal() },
                modifier = Modifier.fillMaxWidth().height(112.dp),
            )
            Spacer(Modifier.height(2.dp))
        }
    }

    // Editor dialog
    if (state.showEditor) {
        EditorDialog(
            editing = state.editing,
            places = state.places,
            editorError = state.editorError,
            onUpdate = { viewModel.updateEditing(it) },
            onSave = { viewModel.saveRule() },
            onTestAction = { viewModel.executeNow(it) },
            onDismiss = { viewModel.closeEditor() }
        )
    }

    // Journal dialog
    if (state.showJournal) {
        JournalDialog(
            logs = state.logs,
            onDismiss = { viewModel.hideJournal() }
        )
    }

    // Delete confirmation
    state.showDeleteConfirm?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.automation_delete_confirm_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.automation_delete_confirm_text), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.automation_delete_button), color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }
}

// --- Rule editor ---

@Composable
private fun EditorDialog(
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
                                    copy(triggers = triggers + TriggerDef(p.param, p.chineseName, ">", "0", p.localizedName(context)))
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
                            },
                            onAddButtonPress = {
                                onUpdate { copy(triggers = triggers + newButtonPressTrigger(1)) }
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
                        AddActionButton(
                            onAddParam = {
                                val a = ACTION_COMMANDS.first()
                                onUpdate { copy(actions = actions + ActionDef(a.command, a.localizedName(context))) }
                            },
                            onAddNotification = { silent ->
                                onUpdate { copy(actions = actions + newNotificationAction(silent, context)) }
                            },
                            onAddAppLaunch = {
                                onUpdate { copy(actions = actions + newAppLaunchAction(context)) }
                            },
                            onAddCall = {
                                onUpdate { copy(actions = actions + newCallAction(context)) }
                            },
                            onAddNavigate = {
                                onUpdate { copy(actions = actions + newNavigateAction(context)) }
                            },
                            onAddUrl = {
                                onUpdate { copy(actions = actions + newUrlAction(context)) }
                            },
                            onAddDelay = {
                                onUpdate { copy(actions = actions + newDelayAction(context)) }
                            },
                            onAddMediaVolume = {
                                onUpdate { copy(actions = actions + newMediaVolumeAction(context)) }
                            },
                            onAddOutsideSound = {
                                onUpdate { copy(actions = actions + newOutsideSoundAction(context)) }
                            },
                            onAddStopOutsideSound = {
                                onUpdate { copy(actions = actions + newStopOutsideSoundAction()) }
                            },
                            onAddGoHome = {
                                onUpdate { copy(actions = actions + newGoHomeAction()) }
                            },
                            onAddHttp = {
                                onUpdate { copy(actions = actions + newHttpAction()) }
                            },
                            onAddPlayMusic = {
                                onUpdate { copy(actions = actions + newPlayMusicAction()) }
                            },
                            onAddStopMusic = {
                                onUpdate { copy(actions = actions + newStopMusicAction()) }
                            },
                            onAddPlayVideo = {
                                onUpdate { copy(actions = actions + newPlayVideoAction()) }
                            },
                            onAddStopVideo = {
                                onUpdate { copy(actions = actions + newStopVideoAction()) }
                            },
                            onAddSentryMode = {
                                onUpdate { copy(actions = actions + newSentryModeAction()) }
                            },
                            onAddAutoDoor = {
                                onUpdate { copy(actions = actions + newAutoDoorAction()) }
                            }
                        )
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
                        AddActionButton(
                            onAddParam = {
                                val a = ACTION_COMMANDS.first()
                                onUpdate { copy(exitActions = exitActions + ActionDef(a.command, a.localizedName(context))) }
                            },
                            onAddNotification = { silent ->
                                onUpdate { copy(exitActions = exitActions + newNotificationAction(silent, context)) }
                            },
                            onAddAppLaunch = {
                                onUpdate { copy(exitActions = exitActions + newAppLaunchAction(context)) }
                            },
                            onAddCall = {
                                onUpdate { copy(exitActions = exitActions + newCallAction(context)) }
                            },
                            onAddNavigate = {
                                onUpdate { copy(exitActions = exitActions + newNavigateAction(context)) }
                            },
                            onAddUrl = {
                                onUpdate { copy(exitActions = exitActions + newUrlAction(context)) }
                            },
                            onAddDelay = {
                                onUpdate { copy(exitActions = exitActions + newDelayAction(context)) }
                            },
                            onAddMediaVolume = {
                                onUpdate { copy(exitActions = exitActions + newMediaVolumeAction(context)) }
                            },
                            onAddOutsideSound = {
                                onUpdate { copy(exitActions = exitActions + newOutsideSoundAction(context)) }
                            },
                            onAddStopOutsideSound = {
                                onUpdate { copy(exitActions = exitActions + newStopOutsideSoundAction()) }
                            },
                            onAddGoHome = {
                                onUpdate { copy(exitActions = exitActions + newGoHomeAction()) }
                            },
                            onAddHttp = {
                                onUpdate { copy(exitActions = exitActions + newHttpAction()) }
                            },
                            onAddPlayMusic = {
                                onUpdate { copy(exitActions = exitActions + newPlayMusicAction()) }
                            },
                            onAddStopMusic = {
                                onUpdate { copy(exitActions = exitActions + newStopMusicAction()) }
                            },
                            onAddPlayVideo = {
                                onUpdate { copy(exitActions = exitActions + newPlayVideoAction()) }
                            },
                            onAddStopVideo = {
                                onUpdate { copy(exitActions = exitActions + newStopVideoAction()) }
                            },
                            onAddSentryMode = {
                                onUpdate { copy(exitActions = exitActions + newSentryModeAction()) }
                            },
                            onAddAutoDoor = {
                                onUpdate { copy(exitActions = exitActions + newAutoDoorAction()) }
                            },
                        )
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
                        enabled = editing.name.isNotBlank() && editing.triggers.isNotEmpty() && editing.actions.isNotEmpty()
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
