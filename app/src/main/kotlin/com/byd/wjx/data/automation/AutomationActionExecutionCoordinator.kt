package com.byd.wjx.data.automation

import android.util.Log
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.log.WjxFileLogger
import org.json.JSONArray
import org.json.JSONObject

internal class AutomationActionExecutionCoordinator(
    private val ruleRepository: AutomationRuleRepository,
    private val journalRepository: AutomationJournalRepository,
    private val actionDispatcher: ActionDispatcher,
    private val vehicleStateRepository: VehicleStateRepository,
) {
    suspend fun executeConfirmed(
        rule: RuleEntity,
        actions: List<ActionDef>,
        snapshot: String,
        data: DiParsData?,
        phase: String,
    ) {
        val currentRule = ruleRepository.find(rule.id)?.takeIf(RuleEntity::enabled)
        if (currentRule == null || !sameAutomationExecutionDefinition(rule, currentRule)) {
            recordConfirmationOutcome(
                rule = rule,
                snapshot = snapshot,
                triggeredAt = System.currentTimeMillis(),
                result = "stale_confirmation",
                phase = phase,
            )
            WjxFileLogger.i(TAG, "ignore stale confirmation rule='${rule.name}' phase=$phase")
            return
        }
        executeAndLog(currentRule, actions, snapshot, data, phase)
    }

    suspend fun executeAndLog(
        rule: RuleEntity,
        actions: List<ActionDef>,
        snapshot: String,
        data: DiParsData?,
        phase: String,
    ) {
        val results = JSONArray()
        var allSuccess = true
        for ((index, action) in actions.withIndex()) {
            val liveData = vehicleStateRepository.actionSnapshot.value?.data
                ?: data
                ?: vehicleStateRepository.fullSnapshot.value?.data
            if (rule.requirePark && liveData?.gear != PARK_GEAR) {
                results.put(JSONObject().apply {
                    put("command", action.command)
                    put("displayName", action.displayName)
                    put("kind", action.kind)
                    put("success", false)
                    put("reason", "车辆已离开P挡，已停止执行")
                })
                allSuccess = false
                WjxFileLogger.w(
                    TAG,
                    "park-only execution stopped rule='${rule.name}' phase=$phase gear=${liveData?.gear}",
                )
                break
            }
            val result = actionDispatcher.dispatch(
                action = action.withAutoParkingSoundAllowance(rule),
                data = liveData,
                owner = automationActionOwner(rule.id, phase, index, action),
            )
            results.put(JSONObject().apply {
                put("command", action.command)
                put("displayName", action.displayName)
                put("kind", action.kind)
                put("success", result.success)
                if (result.reason != null) put("reason", result.reason)
            })
            if (!result.success) allSuccess = false
        }
        journalRepository.record(
            RuleLogEntity(
                ruleId = rule.id,
                ruleName = rule.name,
                triggeredAt = System.currentTimeMillis(),
                triggersSnapshot = snapshot,
                actionsResult = results.toString(),
                success = allSuccess,
            ),
        )
        WjxFileLogger.i(TAG, "Rule '${rule.name}' executed: success=$allSuccess results=$results")
    }

    suspend fun recordConfirmationOutcome(
        rule: RuleEntity,
        snapshot: String,
        triggeredAt: Long,
        result: String,
        phase: String? = null,
    ) {
        val resultJson = JSONArray().put(JSONObject().apply {
            put("result", result)
            if (phase != null) put("phase", phase)
        })
        journalRepository.record(
            RuleLogEntity(
                ruleId = rule.id,
                ruleName = rule.name,
                triggeredAt = triggeredAt,
                triggersSnapshot = snapshot,
                actionsResult = resultJson.toString(),
                success = false,
            ),
        )
    }

    fun stopPhaseMedia(ruleId: Long, actions: List<ActionDef>, ownerPhase: String) {
        actions.forEachIndexed { index, action ->
            if (!action.shouldStopWithPhase()) return@forEachIndexed
            val owner = automationActionOwner(ruleId, ownerPhase, index, action)
                ?: return@forEachIndexed
            if (action.kind == "outside_sound") {
                actionDispatcher.stopOutsideSound(owner)
            } else {
                actionDispatcher.stopTransientMedia(action.kind, owner)
            }
            WjxFileLogger.i(
                TAG,
                "stop phase media: ruleId=$ruleId phase=$ownerPhase kind=${action.kind} owner=$owner",
            )
            Log.i(TAG, "Stopped phase media ruleId=$ruleId phase=$ownerPhase owner=$owner")
        }
    }

    private fun ActionDef.shouldStopWithPhase(): Boolean =
        (kind == "inside_sound" && automationMediaLoop()) ||
            (kind == "outside_sound" && automationMediaLoop()) ||
            kind == "play_video" ||
            (kind == "play_music" && automationMediaLoop())

    private fun ActionDef.automationMediaLoop(): Boolean = try {
        JSONObject(payload ?: "{}").optBoolean("loop", false)
    } catch (_: Exception) {
        false
    }

    private fun ActionDef.withAutoParkingSoundAllowance(rule: RuleEntity): ActionDef {
        if (kind != "outside_sound" || !rule.hasActiveAutoParkingTrigger()) return this
        val updatedPayload = try {
            JSONObject(payload ?: "{}")
        } catch (_: Exception) {
            return this
        }
        updatedPayload.put("allowDuringAutoParking", true)
        return copy(payload = updatedPayload.toString())
    }

    private fun RuleEntity.hasActiveAutoParkingTrigger(): Boolean =
        TriggerDef.listFromJson(triggers).any { trigger ->
            trigger.kind == "param" &&
                trigger.param == "AdasApaParkingStatus" &&
                trigger.operator == "==" &&
                trigger.value.toDoubleOrNull() == AUTO_PARKING_ACTIVE_VALUE
        }

    companion object {
        private const val TAG = "AutomationEngine"
        private const val PARK_GEAR = 1
        private const val AUTO_PARKING_ACTIVE_VALUE = 4.0
    }
}

internal fun sameAutomationExecutionDefinition(before: RuleEntity, current: RuleEntity): Boolean =
    before.triggerLogic == current.triggerLogic &&
        before.triggers == current.triggers &&
        before.actions == current.actions &&
        before.exitActions == current.exitActions &&
        before.cooldownSeconds == current.cooldownSeconds &&
        before.repeatIntervalSeconds == current.repeatIntervalSeconds &&
        before.requirePark == current.requirePark &&
        before.confirmBeforeExecute == current.confirmBeforeExecute &&
        before.fireOncePerTrip == current.fireOncePerTrip

internal fun automationActionOwner(
    ruleId: Long,
    phase: String,
    index: Int,
    action: ActionDef,
): String? = if (action.kind in AUTOMATION_MEDIA_KINDS) {
    val ownerPhase = if (phase == "exit") "exit" else "enter"
    "automation:$ruleId:$ownerPhase:$index"
} else {
    null
}

private val AUTOMATION_MEDIA_KINDS =
    setOf("inside_sound", "outside_sound", "play_music", "play_video")
