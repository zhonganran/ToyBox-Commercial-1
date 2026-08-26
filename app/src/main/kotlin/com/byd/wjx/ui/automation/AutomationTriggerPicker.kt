package com.byd.wjx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.automation.ScheduleSpec
import com.byd.wjx.data.automation.minuteToHHmm
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted

/** Groups available trigger types without coupling the rule editor to their default definitions. */
@Composable
internal fun AddTriggerButton(
    onAddParam: () -> Unit,
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
            contentAlignment = Alignment.Center,
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
                onClick = { menuExpanded = false; onAddParam() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_board_vehicle), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddBoardVehicle() },
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_trigger_group_time),
                AccentGreen,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_time_of_day), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddTimeOfDay() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_schedule), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddSchedule() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_interval), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddInterval() },
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_trigger_group_environment),
                AccentGreen,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_internet), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddNetworkAvailable() },
            )

            AutomationMenuGroupHeader(
                stringResource(R.string.automation_trigger_group_system),
                AccentGreen,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.automation_trigger_type_service_start), fontSize = 13.sp) },
                onClick = { menuExpanded = false; onAddServiceStart() },
            )
        }
    }
}

internal fun newServiceStartTrigger(): TriggerDef = TriggerDef(
    param = "ServiceStart",
    chineseName = "服务启动",
    operator = "==",
    value = "true",
    displayName = "超级玩具箱启动",
    kind = "service_start",
)

internal fun newNetworkAvailableTrigger(): TriggerDef = TriggerDef(
    param = "NetworkAvailable",
    chineseName = "网络可用",
    operator = "==",
    value = "true",
    displayName = "网络可用",
    kind = "network_available",
)

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

internal fun newTimeOfDayTrigger(): TriggerDef = TriggerDef(
    param = "TimeOfDay",
    chineseName = "时间段",
    operator = "==",
    value = "NIGHT",
    displayName = "夜晚",
    kind = "time_of_day",
)

internal fun newScheduleTrigger(): TriggerDef {
    val spec = ScheduleSpec(fromMinute = 8 * 60, toMinute = 10 * 60, days = emptySet())
    return TriggerDef(
        param = "Schedule",
        chineseName = "时间表",
        operator = "==",
        value = spec.toJson(),
        displayName = "${minuteToHHmm(spec.fromMinute)}-${minuteToHHmm(spec.toMinute)}",
        kind = "time_range",
    )
}
