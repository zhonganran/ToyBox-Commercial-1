package com.byd.wjx.ui.automation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary

@Composable
internal fun AutomationEditorTriggerColumn(
    editing: EditingRule,
    onUpdate: (EditingRule.() -> EditingRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        OutlinedTextField(
            value = editing.name,
            onValueChange = { value -> onUpdate { copy(name = value) } },
            placeholder = {
                Text(stringResource(R.string.automation_rule_name_placeholder), color = TextMuted)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentGreen,
            ),
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(14.dp))
        AutomationEditorSectionHeader(stringResource(R.string.automation_section_when))
        Row {
            AutomationLogicChip(
                stringResource(R.string.automation_logic_and),
                editing.triggerLogic == "AND",
            ) {
                onUpdate { copy(triggerLogic = "AND") }
            }
            AutomationLogicChip(
                stringResource(R.string.automation_logic_or),
                editing.triggerLogic == "OR",
            ) {
                onUpdate { copy(triggerLogic = "OR") }
            }
        }
        Spacer(Modifier.height(8.dp))

        editing.triggers.forEachIndexed { index, trigger ->
            TriggerRow(
                index = index,
                trigger = trigger,
                onUpdate = { updated ->
                    onUpdate {
                        copy(triggers = triggers.toMutableList().apply { set(index, updated) })
                    }
                },
                onMoveUp = if (index > 0) {
                    { onUpdate { copy(triggers = triggers.moveItem(index, up = true)) } }
                } else {
                    null
                },
                onMoveDown = if (index < editing.triggers.lastIndex) {
                    { onUpdate { copy(triggers = triggers.moveItem(index, up = false)) } }
                } else {
                    null
                },
                onDelete = {
                    onUpdate {
                        copy(triggers = triggers.toMutableList().apply { removeAt(index) })
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
        }

        if (editing.triggers.size < MAX_TRIGGER_COUNT) {
            AddTriggerButton(
                onAddParam = {
                    val parameter = TRIGGER_PARAMS.first()
                    onUpdate {
                        copy(
                            triggers = triggers + TriggerDef(
                                parameter.param,
                                parameter.name,
                                ">",
                                "0",
                                parameter.name,
                            ),
                        )
                    }
                },
                onAddTimeOfDay = {
                    onUpdate { copy(triggers = triggers + newTimeOfDayTrigger()) }
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
                    onUpdate { copy(triggers = triggers + newServiceStartTrigger()) }
                },
                onAddNetworkAvailable = {
                    onUpdate { copy(triggers = triggers + newNetworkAvailableTrigger()) }
                },
            )
        }
    }
}

private const val MAX_TRIGGER_COUNT = 5
