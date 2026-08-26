package com.byd.wjx.ui.automation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary

@Composable
internal fun AutomationEditorExecutionSettings(
    editing: EditingRule,
    onUpdate: (EditingRule.() -> EditingRule) -> Unit,
) {
    var repeatIntervalInput by rememberSaveable(editing.id, editing.isNew) {
        mutableStateOf(editing.repeatIntervalSeconds.toString())
    }
    AutomationEditorSectionHeader(stringResource(R.string.automation_section_settings))
    SettingRow(stringResource(R.string.automation_setting_cooldown)) {
        OutlinedTextField(
            value = editing.cooldownSeconds.toString(),
            onValueChange = { value ->
                value.toIntOrNull()?.let { seconds ->
                    onUpdate {
                        copy(
                            cooldownSeconds = seconds.coerceIn(
                                AUTOMATION_COOLDOWN_MIN_SECONDS,
                                AUTOMATION_COOLDOWN_MAX_SECONDS,
                            ),
                        )
                    }
                }
            },
            modifier = Modifier.width(70.dp),
            colors = numericFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center),
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
                        copy(
                            repeatIntervalSeconds = seconds.coerceIn(
                                0,
                                AUTOMATION_REPEAT_MAX_SECONDS,
                            ),
                        )
                    }
                }
            },
            modifier = Modifier.width(78.dp),
            colors = numericFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.width(4.dp))
        Text("秒", fontSize = 12.sp, color = TextMuted)
        Spacer(Modifier.width(6.dp))
        Text("0=关闭", fontSize = 11.sp, color = TextMuted)
    }
    Spacer(Modifier.height(4.dp))

    SettingRow(stringResource(R.string.automation_setting_park_only)) {
        Switch(
            checked = editing.requirePark,
            onCheckedChange = { enabled -> onUpdate { copy(requirePark = enabled) } },
            colors = bydSwitchColors(),
        )
    }
    SettingRow(stringResource(R.string.automation_setting_confirm_before)) {
        Switch(
            checked = editing.confirmBeforeExecute,
            onCheckedChange = { enabled -> onUpdate { copy(confirmBeforeExecute = enabled) } },
            colors = bydSwitchColors(),
        )
    }
    SettingRow(stringResource(R.string.automation_setting_once_per_trip)) {
        Switch(
            checked = editing.fireOncePerTrip,
            onCheckedChange = { enabled -> onUpdate { copy(fireOncePerTrip = enabled) } },
            colors = bydSwitchColors(),
        )
    }
}

@Composable
private fun numericFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = CardBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentGreen,
)
