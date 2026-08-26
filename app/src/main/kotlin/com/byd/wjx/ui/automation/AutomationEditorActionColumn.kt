package com.byd.wjx.ui.automation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.byd.wjx.R
import com.byd.wjx.data.local.entity.ActionDef

@Composable
internal fun AutomationEditorActionColumn(
    editing: EditingRule,
    onUpdate: (EditingRule.() -> EditingRule) -> Unit,
    onTestAction: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AutomationEditorActionList(
            title = stringResource(R.string.automation_section_then),
            actions = editing.actions,
            onActionsChange = { updated -> onUpdate { copy(actions = updated) } },
            onTestAction = onTestAction,
        )
        Spacer(Modifier.height(20.dp))
        AutomationEditorActionList(
            title = stringResource(R.string.automation_section_on_exit),
            hint = stringResource(R.string.automation_section_on_exit_hint),
            actions = editing.exitActions,
            onActionsChange = { updated -> onUpdate { copy(exitActions = updated) } },
            onTestAction = onTestAction,
        )
        Spacer(Modifier.height(20.dp))
        AutomationEditorExecutionSettings(editing = editing, onUpdate = onUpdate)
    }
}
