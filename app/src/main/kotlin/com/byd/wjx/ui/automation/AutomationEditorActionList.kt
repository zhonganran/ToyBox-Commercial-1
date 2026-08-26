package com.byd.wjx.ui.automation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.ui.theme.TextMuted

@Composable
internal fun AutomationEditorActionList(
    title: String,
    hint: String? = null,
    actions: List<ActionDef>,
    onActionsChange: (List<ActionDef>) -> Unit,
    onTestAction: (ActionDef) -> Unit,
) {
    AutomationEditorSectionHeader(title)
    if (hint != null) {
        Text(
            hint,
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    actions.forEachIndexed { index, action ->
        ActionRow(
            index = index,
            action = action,
            onUpdate = { updated ->
                onActionsChange(actions.toMutableList().apply { set(index, updated) })
            },
            onTest = onTestAction,
            onMoveUp = if (index > 0) {
                { onActionsChange(actions.moveItem(index, up = true)) }
            } else {
                null
            },
            onMoveDown = if (index < actions.lastIndex) {
                { onActionsChange(actions.moveItem(index, up = false)) }
            } else {
                null
            },
            onDelete = {
                onActionsChange(actions.toMutableList().apply { removeAt(index) })
            },
        )
        Spacer(Modifier.height(4.dp))
    }
    if (actions.size < MAX_ACTION_COUNT) {
        AddActionButton { action -> onActionsChange(actions + action) }
    }
}

private const val MAX_ACTION_COUNT = 10
