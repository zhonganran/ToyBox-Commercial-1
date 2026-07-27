package com.byd.wjx.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.R
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun AutomationScreen(
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filtered = remember(state.rules, state.filter) {
        when (state.filter) {
            RuleFilter.ALL -> state.rules
            RuleFilter.ENABLED -> state.rules.filter { it.enabled }
            RuleFilter.DISABLED -> state.rules.filter { !it.enabled }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 22.dp),
    ) {
        AutomationSceneHeader(
            filter = state.filter,
            onFilterChanged = viewModel::setFilter,
            onOpenJournal = { viewModel.showJournal() },
            onCreate = { viewModel.openNewRule() },
        )
        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            LinkageEmptyState(
                hasRules = state.rules.isNotEmpty(),
                onCreate = { viewModel.openNewRule() },
            )
        } else {
            val featuredRule = filtered.firstOrNull { it.enabled } ?: filtered.first()
            FeaturedAutomationRule(
                rule = featuredRule,
                onClick = { viewModel.openEditRule(featuredRule) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.42f)
                    .height(116.dp),
            )
            AutomationConnector(modifier = Modifier.fillMaxWidth().height(54.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(198.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(filtered, key = { it.id }) { rule ->
                    SceneRuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleEnabled(rule) },
                        onClick = { viewModel.openEditRule(rule) },
                        onEdit = { viewModel.openEditRule(rule) },
                        onDuplicate = { viewModel.duplicateRule(rule) },
                        onDelete = { viewModel.requestDelete(rule.id) },
                        modifier = Modifier.width(354.dp).fillMaxHeight(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            AutomationSummaryStrip(
                total = state.rules.size,
                enabled = state.rules.count { it.enabled },
                logs = state.logs,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
            Spacer(Modifier.height(12.dp))
            RecentAutomationLogs(
                logs = state.logs.take(3),
                onOpenJournal = { viewModel.showJournal() },
                modifier = Modifier.fillMaxWidth().height(112.dp),
            )
            Spacer(Modifier.height(2.dp))
        }
    }

    // Editor dialog
    if (state.showEditor) {
        EditorDialog(
            editing = state.editing,
            places = state.places,
            editorError = state.editorError,
            onUpdate = { viewModel.updateEditing(it) },
            onSave = { viewModel.saveRule() },
            onTestAction = viewModel::executeNow,
            onDismiss = { viewModel.closeEditor() }
        )
    }

    // Journal dialog
    if (state.showJournal) {
        JournalDialog(
            logs = state.logs,
            onDismiss = { viewModel.hideJournal() }
        )
    }

    // Delete confirmation
    state.showDeleteConfirm?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.automation_delete_confirm_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.automation_delete_confirm_text), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.automation_delete_button), color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }
}
