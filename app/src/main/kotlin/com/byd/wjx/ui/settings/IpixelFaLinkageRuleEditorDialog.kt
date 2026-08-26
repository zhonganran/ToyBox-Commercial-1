package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.byd.wjx.led.LedLinkageCatalog
import com.byd.wjx.led.LedLinkageTriggerType
import com.byd.wjx.led.ipixelfa.IpixelFaLinkageRule
import com.byd.wjx.led.ipixelfa.IpixelFaLinkageStep
import com.byd.wjx.led.ipixelfa.IpixelFaPreset
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun IpixelFaLinkageRuleEditorDialog(
    initialRule: IpixelFaLinkageRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (IpixelFaLinkageRule) -> Boolean,
) {
    var draft by remember(initialRule.id) { mutableStateOf(initialRule) }
    var saveError by remember(initialRule.id) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CardSurfaceElevated,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp).heightIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    if (isNew) "新建联动规则" else "编辑联动规则",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LedLinkageChoiceField(
                        label = "触发类型",
                        selected = draft.triggerType,
                        options = LedLinkageTriggerType.entries.map { it to it.title },
                    ) { type ->
                        draft = draft.copy(
                            triggerType = type,
                            conditionValue = LedLinkageCatalog.options(type).first().value,
                        )
                    }
                    LedLinkageChoiceField(
                        label = "触发条件",
                        selected = draft.conditionValue,
                        options = LedLinkageCatalog.options(draft.triggerType).map { it.value to it.title },
                    ) { draft = draft.copy(conditionValue = it) }

                    HorizontalDivider(color = CardBorder)
                    Text(
                        "联动画面（条件满足时持续播放）",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val step = draft.steps.firstOrNull() ?: IpixelFaLinkageStep(IpixelFaPreset.TEMPORARY_STOP)
                    IpixelFaStepEditor(
                        step = step,
                        onChange = { draft = draft.copy(steps = listOf(it)) },
                    )
                }

                RuleEditorActions(
                    saveError = saveError,
                    onDismiss = onDismiss,
                    onSave = { saveError = !onSave(draft.normalized()) },
                )
            }
        }
    }
}

@Composable
private fun IpixelFaStepEditor(
    step: IpixelFaLinkageStep,
    onChange: (IpixelFaLinkageStep) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("选择要持续播放的画面", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        IpixelFaPresetChoiceField(
            selected = step.preset,
            onSelected = { onChange(step.copy(preset = it)) },
        )
    }
}

@Composable
private fun IpixelFaPresetChoiceField(
    selected: IpixelFaPreset,
    onSelected: (IpixelFaPreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CardBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        ) {
            IpixelFaPresetPreview(
                preset = selected,
                modifier = Modifier.size(width = 144.dp, height = 24.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    "画面 ${selected.presetId.toString().padStart(2, '0')}",
                    color = TextMuted,
                    fontSize = 10.sp,
                )
                Text(selected.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = "展开画面", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 560.dp),
            scrollState = rememberScrollState(),
        ) {
            IpixelFaPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IpixelFaPresetPreview(
                                preset = preset,
                                modifier = Modifier.size(width = 144.dp, height = 24.dp),
                            )
                            Column {
                                Text(
                                    text = preset.presetId.toString().padStart(2, '0'),
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                )
                                Text(preset.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(preset)
                    },
                )
            }
        }
    }
}

@Composable
internal fun RuleEditorActions(
    saveError: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (saveError) {
            Text(
                "同一触发类型和条件只能保留一条规则",
                color = AccentOrange,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Text("取消")
        }
        Spacer(Modifier.size(10.dp))
        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("保存")
        }
    }
}
