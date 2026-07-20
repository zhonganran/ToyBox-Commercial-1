package com.byd.wjx.ui.automation

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.automation.ScheduleSpec
import com.byd.wjx.data.automation.minuteToHHmm
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.theme.*

@Composable
internal fun TriggerRow(
    index: Int,
    trigger: TriggerDef,
    places: List<PlaceEntity>,
    onUpdate: (TriggerDef) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
            .border(1.dp, CardBorder.copy(alpha = 0.74f), RoundedCornerShape(14.dp))
            .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted,
            modifier = Modifier.width(16.dp))

        when (trigger.kind) {
            "place_enter", "place_exit" -> PlaceTriggerControls(trigger, places, onUpdate)
            "time_of_day" -> TimeOfDayTriggerControls(trigger, onUpdate)
            "time_range" -> ScheduleTriggerControls(trigger, onUpdate)
            "interval" -> IntervalTriggerControls(trigger, onUpdate)
            "service_start" -> ServiceStartTriggerControls()
            "network_available" -> NetworkAvailableTriggerControls()
            "board_vehicle" -> SimpleEventTriggerControls("上车开门")
            else -> ParamTriggerControls(trigger, onUpdate)
        }

        Spacer(Modifier.weight(1f))

        ReorderArrows(onMoveUp = onMoveUp, onMoveDown = onMoveDown)
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Close, "delete", tint = Color(0xFFEF4444).copy(alpha = 0.72f), modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ParamTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit
) {
    val context = LocalContext.current
    // Param dropdown
    CatalogDropdown(
        selected = TRIGGER_PARAMS.find { it.param == trigger.param }?.name ?: trigger.param,
        items = TRIGGER_PARAMS.map { it.name },
        categories = TRIGGER_PARAMS.map { it.category },
        modifier = Modifier.width(150.dp),
        onSelect = { idx ->
            val p = TRIGGER_PARAMS[idx]
            onUpdate(trigger.copy(param = p.param, chineseName = p.name,
                displayName = "${p.name} ${trigger.operator} ${trigger.value}"))
        }
    )
    Spacer(Modifier.width(4.dp))

    // Operator dropdown
    var opExpanded by remember { mutableStateOf(false) }
    Box {
        Text(
            trigger.operator,
            fontSize = 13.sp, color = AccentOrange, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .clickable { opExpanded = true }
                .padding(8.dp, 6.dp)
                .width(30.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        DropdownMenu(expanded = opExpanded, onDismissRequest = { opExpanded = false }) {
            OPERATORS.forEach { op ->
                DropdownMenuItem(
                    text = { Text(op, fontWeight = FontWeight.Bold) },
                    onClick = {
                        opExpanded = false
                        onUpdate(trigger.copy(operator = op))
                    }
                )
            }
        }
    }
    Spacer(Modifier.width(4.dp))

    // Value: enum dropdown or text input with unit
    val paramOption = TRIGGER_PARAMS.find { it.param == trigger.param }
    if (paramOption?.enumValues != null) {
        // Enum dropdown
        var enumExpanded by remember { mutableStateOf(false) }
        val enumLabel = paramOption.enumValues.find { it.first == trigger.value }?.second ?: trigger.value
        Box {
            Text(
                enumLabel,
                fontSize = 13.sp, color = AccentGreen,
                modifier = Modifier
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { enumExpanded = true }
                    .padding(8.dp, 6.dp)
            )
            DropdownMenu(expanded = enumExpanded, onDismissRequest = { enumExpanded = false }) {
                paramOption.enumValues.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label, fontSize = 13.sp) },
                        onClick = {
                            enumExpanded = false
                            onUpdate(trigger.copy(value = value, operator = "=="))
                        }
                    )
                }
            }
        }
    } else {
        // Numeric input
        OutlinedTextField(
            value = trigger.value,
            onValueChange = { v -> onUpdate(trigger.copy(value = v)) },
            modifier = Modifier.width(70.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen, unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentGreen
            ),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
        if (paramOption?.unit?.isNotEmpty() == true) {
            Spacer(Modifier.width(4.dp))
            Text(paramOption.unit, fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun PlaceTriggerControls(
    trigger: TriggerDef,
    places: List<PlaceEntity>,
    onUpdate: (TriggerDef) -> Unit
) {
    val placeExists = places.any { it.id == trigger.placeId }
    val isStale = trigger.placeId != null && !placeExists

    Icon(
        Icons.Outlined.Place,
        contentDescription = null,
        tint = TextMuted,
        modifier = Modifier.size(16.dp)
    )
    Spacer(Modifier.width(4.dp))

    if (isStale || (trigger.placeId == null && places.isEmpty())) {
        // Show warning when the referenced place was deleted
        Text(
            stringResource(R.string.automation_trigger_place_deleted),
            fontSize = 13.sp,
            color = Color(0xFFEF4444),
            modifier = Modifier
                .background(Color(0xFFEF4444).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(8.dp, 6.dp)
        )
    } else {
        // Place-name dropdown
        var placeExpanded by remember { mutableStateOf(false) }
        val deletedPlaceholder = stringResource(R.string.automation_trigger_place_deleted_placeholder)
        Box(modifier = Modifier.width(150.dp)) {
            Text(
                trigger.placeName ?: deletedPlaceholder,
                fontSize = 13.sp, color = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { placeExpanded = true }
                    .padding(8.dp, 7.dp),
                maxLines = 1
            )
            val enterPrefix = stringResource(R.string.automation_trigger_place_enter_prefix)
            val exitPrefix = stringResource(R.string.automation_trigger_place_exit_prefix)
            DropdownMenu(expanded = placeExpanded, onDismissRequest = { placeExpanded = false }) {
                places.forEach { place ->
                    DropdownMenuItem(
                        text = { Text(place.name, fontSize = 13.sp) },
                        onClick = {
                            placeExpanded = false
                            val kindLabel = if (trigger.kind == "place_enter") enterPrefix else exitPrefix
                            onUpdate(trigger.copy(
                                placeId = place.id,
                                placeName = place.name,
                                displayName = "$kindLabel «${place.name}»"
                            ))
                        }
                    )
                }
            }
        }
    }
    Spacer(Modifier.width(4.dp))

    // Kind toggle pill: enter / exit.
    val isEnter = trigger.kind == "place_enter"
    val enterLabel = stringResource(R.string.automation_trigger_place_enter_label)
    val exitLabel = stringResource(R.string.automation_trigger_place_exit_label)
    val enterPrefix2 = stringResource(R.string.automation_trigger_place_enter_prefix)
    val exitPrefix2 = stringResource(R.string.automation_trigger_place_exit_prefix)
    val label = if (isEnter) enterLabel else exitLabel
    Box(
        modifier = Modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable {
                val newKind = if (isEnter) "place_exit" else "place_enter"
                val kindLabel = if (newKind == "place_enter") enterPrefix2 else exitPrefix2
                onUpdate(trigger.copy(
                    kind = newKind,
                    displayName = "$kindLabel «${trigger.placeName ?: "?"}»"
                ))
            }
            .padding(8.dp, 6.dp)
    ) {
        Text(label, color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimeOfDayTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit
) {
    val dayLabel = stringResource(R.string.automation_trigger_day)
    val nightLabel = stringResource(R.string.automation_trigger_night)
    val dawnLabel = stringResource(R.string.automation_trigger_dawn)
    val duskLabel = stringResource(R.string.automation_trigger_dusk)
    val phases = listOf("DAY" to dayLabel, "NIGHT" to nightLabel, "DAWN" to dawnLabel, "DUSK" to duskLabel)
    val current = phases.find { it.first == trigger.value.uppercase() } ?: phases[1]
    var expanded by remember { mutableStateOf(false) }

    Text(stringResource(R.string.automation_trigger_time_of_day_label), fontSize = 12.sp, color = TextMuted)
    Spacer(Modifier.width(4.dp))
    Box {
        Text(
            current.second,
            fontSize = 13.sp,
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(8.dp, 6.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            phases.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onUpdate(trigger.copy(value = value, displayName = label))
                    }
                )
            }
        }
    }
}

@Composable
private fun ScheduleTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit
) {
    val context = LocalContext.current
    val spec = remember(trigger.value) {
        ScheduleSpec.fromJson(trigger.value) ?: ScheduleSpec(8 * 60, 10 * 60, emptySet())
    }
    val isExact = spec.isExact

    fun push(newSpec: ScheduleSpec) {
        onUpdate(trigger.copy(value = newSpec.toJson(), displayName = scheduleDisplayName(context, newSpec)))
    }

    fun pickTime(currentMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            currentMinute / 60, currentMinute % 60, true
        ).show()
    }

    val dayLabels = listOf(
        1 to stringResource(R.string.automation_day_mon),
        2 to stringResource(R.string.automation_day_tue),
        3 to stringResource(R.string.automation_day_wed),
        4 to stringResource(R.string.automation_day_thu),
        5 to stringResource(R.string.automation_day_fri),
        6 to stringResource(R.string.automation_day_sat),
        7 to stringResource(R.string.automation_day_sun),
    )

    Column {
        // Mode toggle: exact vs range
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = isExact,
                onClick = { if (!isExact) push(spec.copy(toMinute = spec.fromMinute)) },
                label = { Text(stringResource(R.string.automation_schedule_mode_exact), fontSize = 12.sp) },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = !isExact,
                onClick = {
                    // Open a 2h window so "from" and "to" differ when switching to range.
                    if (isExact) push(spec.copy(toMinute = (spec.fromMinute + 120) % 1440))
                },
                label = { Text(stringResource(R.string.automation_schedule_mode_range), fontSize = 12.sp) },
            )
        }
        Spacer(Modifier.height(6.dp))
        // Time picker(s)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isExact) {
                Text(stringResource(R.string.automation_schedule_time_label) + " ", fontSize = 12.sp, color = TextMuted)
                TimeChip(minuteToHHmm(spec.fromMinute)) {
                    pickTime(spec.fromMinute) { push(spec.copy(fromMinute = it, toMinute = it)) }
                }
            } else {
                Text(stringResource(R.string.automation_schedule_from) + " ", fontSize = 12.sp, color = TextMuted)
                TimeChip(minuteToHHmm(spec.fromMinute)) {
                    pickTime(spec.fromMinute) { push(spec.copy(fromMinute = it)) }
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.automation_schedule_to) + " ", fontSize = 12.sp, color = TextMuted)
                TimeChip(minuteToHHmm(spec.toMinute)) {
                    pickTime(spec.toMinute) { push(spec.copy(toMinute = it)) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Days of week (none selected = every day)
        Text(stringResource(R.string.automation_schedule_days_label), fontSize = 11.sp, color = TextMuted)
        Spacer(Modifier.height(2.dp))
        Row {
            dayLabels.forEach { (d, label) ->
                DayChip(
                    label = label,
                    selected = d in spec.days,
                    onClick = {
                        val newDays = if (d in spec.days) spec.days - d else spec.days + d
                        push(spec.copy(days = newDays))
                    },
                )
                Spacer(Modifier.width(3.dp))
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        color = AccentGreen,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp, 6.dp)
    )
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) AccentGreen else TextMuted,
        modifier = Modifier
            .background(if (selected) AccentGreen.copy(alpha = 0.15f) else CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) AccentGreen else CardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 5.dp)
    )
}

/** Readable schedule label; first token is the time so the rule summary stays compact. */
private fun scheduleDisplayName(context: android.content.Context, spec: ScheduleSpec): String {
    val time = if (spec.isExact) {
        minuteToHHmm(spec.fromMinute)
    } else {
        "${minuteToHHmm(spec.fromMinute)}-${minuteToHHmm(spec.toMinute)}"
    }
    val days = scheduleDaysShort(context, spec.days)
    return if (days.isEmpty()) time else "$time $days"
}

private fun scheduleDaysShort(context: android.content.Context, days: Set<Int>): String {
    if (days.isEmpty() || days.size == 7) return ""
    val labels = mapOf(
        1 to R.string.automation_day_mon,
        2 to R.string.automation_day_tue,
        3 to R.string.automation_day_wed,
        4 to R.string.automation_day_thu,
        5 to R.string.automation_day_fri,
        6 to R.string.automation_day_sat,
        7 to R.string.automation_day_sun,
    )
    return days.sorted().joinToString(",") { context.getString(labels.getValue(it)) }
}

@Composable
private fun IntervalTriggerControls(
    trigger: TriggerDef,
    onUpdate: (TriggerDef) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Timer, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("每", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = trigger.value,
            onValueChange = { value ->
                value.toIntOrNull()?.let { seconds ->
                    val safe = seconds.coerceIn(15, 300)
                    onUpdate(trigger.copy(value = safe.toString(), displayName = "每 $safe 秒"))
                }
            },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentGreen,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.width(6.dp))
        Text("秒（15-300）", fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun SimpleEventTriggerControls(label: String) {
    Icon(Icons.Outlined.Bolt, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(label, fontSize = 13.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
}

@Composable
private fun ServiceStartTriggerControls() {
    Icon(
        Icons.Outlined.PlayArrow,
        contentDescription = null,
        tint = AccentGreen,
        modifier = Modifier.size(16.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
        stringResource(R.string.automation_trigger_service_start),
        fontSize = 13.sp,
        color = AccentGreen,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun NetworkAvailableTriggerControls() {
    Icon(
        Icons.Outlined.Wifi,
        contentDescription = null,
        tint = AccentGreen,
        modifier = Modifier.size(16.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
        stringResource(R.string.automation_trigger_internet),
        fontSize = 13.sp,
        color = AccentGreen,
        fontWeight = FontWeight.Bold
    )
}

// --- Action Row ---


@Composable
internal fun AddTriggerButton(
    places: List<PlaceEntity>,
    onAddParam: () -> Unit,
    onAddPlace: (PlaceEntity) -> Unit,
    onAddTimeOfDay: () -> Unit,
    onAddSchedule: () -> Unit,
    onAddInterval: () -> Unit,
    onAddBoardVehicle: () -> Unit,
    onAddServiceStart: () -> Unit,
    onAddNetworkAvailable: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceElevated.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                .border(1.dp, AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .clickable { menuExpanded = true }
                .padding(12.dp, 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.automation_add_condition_button), fontSize = 12.sp, color = TextMuted)
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.fillMaxHeight(0.72f),
        ) {
            AutomationMenuGroupHeader(
                stringResource(R.string.automation_trigger_group_vehicle),
                AccentGreen,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_param), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddParam() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_board_vehicle), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddBoardVehicle()
                }
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_trigger_group_time),
                AccentGreen,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_time_of_day), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddTimeOfDay()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_schedule), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddSchedule()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_interval), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddInterval()
                }
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_trigger_group_environment),
                AccentGreen,
            )
            val firstPlace = places.firstOrNull()
            DropdownMenuItem(
                text = {
                    if (firstPlace != null) {
                        Text(stringResource(R.string.automation_trigger_type_place), fontSize = 13.sp)
                    } else {
                        Column {
                            Text(stringResource(R.string.automation_trigger_type_place), fontSize = 13.sp, color = TextSecondary)
                            Text(stringResource(R.string.automation_trigger_type_place_empty_hint), fontSize = 11.sp, color = TextMuted)
                        }
                    }
                },
                onClick = {
                    if (firstPlace != null) {
                        menuExpanded = false
                        onAddPlace(firstPlace)
                    }
                },
                enabled = firstPlace != null
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_internet), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddNetworkAvailable()
                }
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_trigger_group_system),
                AccentGreen,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_service_start), fontSize = 13.sp) },
                onClick = {
                    menuExpanded = false
                    onAddServiceStart()
                }
            )
        }
    }
}

internal fun newPlaceTrigger(place: PlaceEntity, context: android.content.Context): TriggerDef {
    return TriggerDef(
        param = "Place",
        chineseName = "位置",
        operator = "==",
        value = "enter",
        displayName = "进入「${place.name}」",
        kind = "place_enter",
        placeId = place.id,
        placeName = place.name
    )
}

internal fun newServiceStartTrigger(context: android.content.Context): TriggerDef {
    return TriggerDef(
        param = "ServiceStart",
        chineseName = "服务启动",
        operator = "==",
        value = "true",
        displayName = "超级玩具箱启动",
        kind = "service_start"
    )
}

internal fun newNetworkAvailableTrigger(context: android.content.Context): TriggerDef {
    return TriggerDef(
        param = "NetworkAvailable",
        chineseName = "网络可用",
        operator = "==",
        value = "true",
        displayName = "网络可用",
        kind = "network_available"
    )
}

internal fun newIntervalTrigger(): TriggerDef = TriggerDef(
    param = "Interval",
    chineseName = "定时间隔",
    operator = "==",
    value = "60",
    displayName = "每 60 秒",
    kind = "interval",
)

internal fun newBoardVehicleTrigger(): TriggerDef = TriggerDef(
    param = "BoardVehicle",
    chineseName = "上车开门",
    operator = "==",
    value = "true",
    displayName = "上车开门",
    kind = "board_vehicle",
)

internal fun newTimeOfDayTrigger(context: android.content.Context): TriggerDef {
    return TriggerDef(
        param = "TimeOfDay",
        chineseName = "时间段",
        operator = "==",
        value = "NIGHT",
        displayName = "夜晚",
        kind = "time_of_day"
    )
}

internal fun newScheduleTrigger(): TriggerDef {
    // Default: 08:00-10:00 window, every day. displayName carries only the time
    // (no days), so no context is needed here.
    val spec = ScheduleSpec(fromMinute = 8 * 60, toMinute = 10 * 60, days = emptySet())
    return TriggerDef(
        param = "Schedule",
        chineseName = "时间表",
        operator = "==",
        value = spec.toJson(),
        displayName = "${minuteToHHmm(spec.fromMinute)}-${minuteToHHmm(spec.toMinute)}",
        kind = "time_range"
    )
}

// --- Helpers ---
