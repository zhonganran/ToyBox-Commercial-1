package com.byd.wjx.ui.automation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.theme.*

@Composable
internal fun TriggerRow(
    index: Int,
    trigger: TriggerDef,
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
