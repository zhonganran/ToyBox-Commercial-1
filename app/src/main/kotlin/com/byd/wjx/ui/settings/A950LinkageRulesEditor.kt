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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.byd.wjx.led.LedLinkageCatalog
import com.byd.wjx.led.LedLinkageTriggerType
import com.byd.wjx.led.a950.A950LinkageRule
import com.byd.wjx.led.a950.A950LinkageRuleFactory
import com.byd.wjx.led.a950.A950LinkageStep
import com.byd.wjx.led.a950.A950Preset
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.util.UUID

@Composable
internal fun A950LinkageRulesSection(
    rules: List<A950LinkageRule>,
    currentPreset: A950Preset?,
    onSave: (A950LinkageRule) -> Boolean,
    onDelete: (String) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    var editorState by remember { mutableStateOf<A950RuleEditorState?>(null) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("智能联动规则", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "当前画面：${currentPreset?.title ?: "无"}",
                        color = AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    currentPreset?.let {
                        Spacer(Modifier.size(6.dp))
                        A950PresetPreview(
                            preset = it,
                            modifier = Modifier.widthIn(max = 180.dp),
                        )
                    }
                }
                Button(
                    onClick = {
                        editorState = A950RuleEditorState(
                            rule = A950LinkageRuleFactory.newRule(UUID.randomUUID().toString()),
                            isNew = true,
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("新建规则")
                }
            }

            if (rules.isEmpty()) {
                Text("暂无联动规则", color = TextMuted, fontSize = 12.sp)
            } else {
                rules.forEachIndexed { index, rule ->
                    if (index > 0) HorizontalDivider(color = CardBorder.copy(alpha = 0.65f))
                    A950RuleRow(
                        rule = rule,
                        canMoveUp = index > 0,
                        canMoveDown = index < rules.lastIndex,
                        onEdit = { editorState = A950RuleEditorState(rule = rule, isNew = false) },
                        onDelete = { onDelete(rule.id) },
                        onEnabledChange = { onEnabledChange(rule.id, it) },
                        onMoveUp = { onMove(rule.id, -1) },
                        onMoveDown = { onMove(rule.id, 1) },
                    )
                }
            }
        }
    }

    editorState?.let { editor ->
        A950RuleEditorDialog(
            initialRule = editor.rule,
            isNew = editor.isNew,
            onDismiss = { editorState = null },
            onSave = {
                if (onSave(it)) {
                    editorState = null
                    true
                } else {
                    false
                }
            },
        )
    }
}

@Composable
private fun A950RuleRow(
    rule: A950LinkageRule,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rule.title,
                color = if (rule.enabled) TextPrimary else TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                rule.steps.joinToString("  →  ") { "${it.preset.title} ${it.durationSeconds}秒" },
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移规则", modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移规则", modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑规则", tint = AccentBlue, modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "删除规则",
                tint = AccentOrange,
                modifier = Modifier.size(19.dp),
            )
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = onEnabledChange,
            colors = bydSwitchColors(),
            modifier = Modifier.size(width = 46.dp, height = 30.dp),
        )
    }
}

@Composable
private fun A950RuleEditorDialog(
    initialRule: A950LinkageRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (A950LinkageRule) -> Boolean,
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
                    ChoiceField(
                        label = "触发类型",
                        selected = draft.triggerType,
                        options = LedLinkageTriggerType.entries.map { it to it.title },
                    ) { type ->
                        draft = draft.copy(
                            triggerType = type,
                            conditionValue = LedLinkageCatalog.options(type).first().value,
                        )
                    }
                    ChoiceField(
                        label = "触发条件",
                        selected = draft.conditionValue,
                        options = LedLinkageCatalog.options(draft.triggerType).map { it.value to it.title },
                    ) { draft = draft.copy(conditionValue = it) }

                    HorizontalDivider(color = CardBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("画面序列", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                if (draft.steps.size < 8) {
                                    draft = draft.copy(
                                        steps = draft.steps + A950LinkageStep(
                                            A950Preset.TEMPORARY_STOP
                                        )
                                    )
                                }
                            },
                            enabled = draft.steps.size < 8,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(5.dp))
                            Text("添加画面", fontSize = 12.sp)
                        }
                    }

                    draft.steps.forEachIndexed { index, step ->
                        A950StepEditor(
                            index = index,
                            step = step,
                            canMoveUp = index > 0,
                            canMoveDown = index < draft.steps.lastIndex,
                            canDelete = draft.steps.size > 1,
                            onChange = { changed ->
                                draft = draft.copy(
                                    steps = draft.steps.toMutableList().apply { this[index] = changed }
                                )
                            },
                            onMove = { offset ->
                                val steps = draft.steps.toMutableList()
                                val item = steps.removeAt(index)
                                steps.add(index + offset, item)
                                draft = draft.copy(steps = steps)
                            },
                            onDelete = {
                                draft = draft.copy(steps = draft.steps.filterIndexed { i, _ -> i != index })
                            },
                        )
                    }
                }

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
                        onClick = { saveError = !onSave(draft.normalized()) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

private data class A950RuleEditorState(
    val rule: A950LinkageRule,
    val isNew: Boolean,
)

@Composable
private fun A950StepEditor(
    index: Int,
    step: A950LinkageStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onChange: (A950LinkageStep) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("步骤 ${index + 1}", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移画面", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移画面", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除画面",
                    tint = AccentOrange,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        A950PresetChoiceField(
            selected = step.preset,
            onSelected = { onChange(step.copy(preset = it)) },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("持续时间", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onChange(step.copy(durationSeconds = step.durationSeconds - 1)) },
                enabled = step.durationSeconds > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = "减少一秒", modifier = Modifier.size(18.dp))
            }
            Text(
                "${step.durationSeconds} 秒",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 48.dp),
            )
            IconButton(
                onClick = { onChange(step.copy(durationSeconds = step.durationSeconds + 1)) },
                enabled = step.durationSeconds < 60,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "增加一秒", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun A950PresetChoiceField(
    selected: A950Preset,
    onSelected: (A950Preset) -> Unit,
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
            A950PresetPreview(
                preset = selected,
                modifier = Modifier.size(width = 108.dp, height = 18.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("画面 ${selected.slotNumber.toString().padStart(2, '0')}", color = TextMuted, fontSize = 10.sp)
                Text(selected.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = "展开画面", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            A950Preset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            A950PresetPreview(
                                preset = preset,
                                modifier = Modifier.size(width = 144.dp, height = 24.dp),
                            )
                            Column {
                                Text(
                                    text = preset.slotNumber.toString().padStart(2, '0'),
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
private fun <T> ChoiceField(
    label: String,
    selected: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = options.firstOrNull { it.first == selected }?.second.orEmpty()

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CardBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, color = TextMuted, fontSize = 10.sp)
                Text(selectedTitle, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = "展开$label", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(
                    text = { Text(title, color = TextPrimary) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                )
            }
        }
    }
}
