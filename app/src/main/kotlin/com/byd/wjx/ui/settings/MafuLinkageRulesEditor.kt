package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.byd.wjx.led.mafu.MafuLinkageRule
import com.byd.wjx.led.mafu.MafuLinkageRuleFactory
import com.byd.wjx.led.mafu.MafuLinkageStep
import com.byd.wjx.led.mafu.MafuRemoteKey
import com.byd.wjx.led.mafu.MafuRemoteModel
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
internal fun MafuLinkageRulesSection(
    rules: List<MafuLinkageRule>,
    remoteModel: MafuRemoteModel,
    currentRemoteKey: MafuRemoteKey?,
    onSave: (MafuLinkageRule) -> Boolean,
    onDelete: (String) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    var editorState by remember { mutableStateOf<MafuRuleEditorState?>(null) }

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
                        text = currentRemoteKey?.let {
                            "当前：${it.title} · 节目 ${remoteModel.programId(it)}"
                        } ?: "当前：无",
                        color = AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(
                    onClick = {
                        editorState = MafuRuleEditorState(
                            rule = MafuLinkageRuleFactory.newRule(UUID.randomUUID().toString(), remoteModel),
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
                    MafuRuleRow(
                        rule = rule,
                        remoteModel = remoteModel,
                        canMoveUp = index > 0,
                        canMoveDown = index < rules.lastIndex,
                        onEdit = { editorState = MafuRuleEditorState(rule, false) },
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
        MafuRuleEditorDialog(
            initialRule = editor.rule,
            remoteModel = remoteModel,
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
private fun MafuRuleRow(
    rule: MafuLinkageRule,
    remoteModel: MafuRemoteModel,
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
                rule.steps.joinToString("  →  ") {
                    "${it.remoteKey.title}(${remoteModel.programId(it.remoteKey)}) ${it.durationSeconds}秒"
                },
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
private fun MafuRuleEditorDialog(
    initialRule: MafuLinkageRule,
    remoteModel: MafuRemoteModel,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (MafuLinkageRule) -> Boolean,
) {
    var draft by remember(initialRule.id, remoteModel) {
        mutableStateOf(initialRule.normalized(remoteModel))
    }
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
                    MafuChoiceField(
                        label = "触发类型",
                        selected = draft.triggerType,
                        options = LedLinkageTriggerType.entries.map { it to it.title },
                    ) { type ->
                        draft = draft.copy(
                            triggerType = type,
                            conditionValue = LedLinkageCatalog.options(type).first().value,
                        )
                    }
                    MafuChoiceField(
                        label = "触发条件",
                        selected = draft.conditionValue,
                        options = LedLinkageCatalog.options(draft.triggerType).map { it.value to it.title },
                    ) { draft = draft.copy(conditionValue = it) }

                    HorizontalDivider(color = CardBorder)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("遥控节目序列", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                if (draft.steps.size < 8) {
                                    draft = draft.copy(
                                        steps = draft.steps + MafuLinkageStep(remoteModel.supportedKeys.first())
                                    )
                                }
                            },
                            enabled = draft.steps.size < 8,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(5.dp))
                            Text("添加节目", fontSize = 12.sp)
                        }
                    }

                    draft.steps.forEachIndexed { index, step ->
                        MafuStepEditor(
                            index = index,
                            step = step,
                            remoteModel = remoteModel,
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
                        onClick = { saveError = !onSave(draft.normalized(remoteModel)) },
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

@Composable
private fun MafuStepEditor(
    index: Int,
    step: MafuLinkageStep,
    remoteModel: MafuRemoteModel,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onChange: (MafuLinkageStep) -> Unit,
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
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移节目", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移节目", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除节目",
                    tint = AccentOrange,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        MafuChoiceField(
            label = "遥控按键",
            selected = step.remoteKey,
            options = remoteModel.supportedKeys.map {
                it to "${it.title} · 节目 ${remoteModel.programId(it)}"
            },
        ) { onChange(step.copy(remoteKey = it)) }
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
private fun <T> MafuChoiceField(
    label: String,
    selected: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        androidx.compose.foundation.layout.Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Text(
                    selectedTitle,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, title) ->
                    DropdownMenuItem(
                        text = { Text(title, fontSize = 12.sp) },
                        onClick = {
                            expanded = false
                            onSelected(value)
                        },
                    )
                }
            }
        }
    }
}

private data class MafuRuleEditorState(
    val rule: MafuLinkageRule,
    val isNew: Boolean,
)
