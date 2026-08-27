package com.byd.wjx.ui.automation

import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.data.local.entity.TriggerDef

data class TriggerParamOption(
    val param: String,
    val name: String,
    val unit: String = "",
    val category: String,
    val enumValues: List<Pair<String, String>>? = null,
)

data class ActionOption(
    val command: String,
    val name: String,
    val category: String,
)

data class AutoDoorActionOption(
    val side: String,
    val commandValue: Int,
    val label: String,
)

enum class RuleFilter { ALL, ENABLED, DISABLED }

const val AUTOMATION_COOLDOWN_MIN_SECONDS = 0
const val AUTOMATION_COOLDOWN_MAX_SECONDS = 60
const val AUTOMATION_REPEAT_MIN_SECONDS = 2
const val AUTOMATION_REPEAT_MAX_SECONDS = 3600
const val AUTOMATION_RULE_LIMIT = 50

data class EditingRule(
    val id: Long = 0,
    val name: String = "",
    val triggerLogic: String = "AND",
    val triggers: List<TriggerDef> = emptyList(),
    val actions: List<ActionDef> = emptyList(),
    val exitActions: List<ActionDef> = emptyList(),
    val cooldownSeconds: Int = 60,
    val repeatIntervalSeconds: Int = 0,
    val requirePark: Boolean = false,
    val confirmBeforeExecute: Boolean = false,
    val fireOncePerTrip: Boolean = false,
    val isNew: Boolean = true,
)

internal fun EditingRule.hasMinimumSaveFields(): Boolean =
    name.isNotBlank() &&
        triggers.isNotEmpty() &&
        (actions.isNotEmpty() || exitActions.isNotEmpty())

internal fun EditingRule.withActionPhase(
    updatedActions: List<ActionDef>,
    exitPhase: Boolean,
): EditingRule {
    val alreadyHadTilt = (actions + exitActions).any { it.kind == "mirror_tilt" }
    var updated = if (exitPhase) copy(exitActions = updatedActions) else copy(actions = updatedActions)
    if (alreadyHadTilt || (updated.actions + updated.exitActions).none { it.kind == "mirror_tilt" }) {
        return updated
    }
    if (!exitPhase && updated.exitActions.none { it.kind == "mirror_restore" }) {
        updated = updated.copy(
            exitActions = if (updated.exitActions.isEmpty()) {
                listOf(newDelayAction(), newMirrorRestoreAction())
            } else {
                updated.exitActions + newMirrorRestoreAction()
            },
        )
    }
    return updated.copy(
        cooldownSeconds = 0,
        repeatIntervalSeconds = 0,
        requirePark = false,
        confirmBeforeExecute = false,
        fireOncePerTrip = false,
    )
}

data class AutomationUiState(
    val rules: List<RuleEntity> = emptyList(),
    val filter: RuleFilter = RuleFilter.ALL,
    val logs: List<RuleLogEntity> = emptyList(),
    val showEditor: Boolean = false,
    val showJournal: Boolean = false,
    val editing: EditingRule = EditingRule(),
    val showDeleteConfirm: Long? = null,
    val editorError: String? = null,
)
