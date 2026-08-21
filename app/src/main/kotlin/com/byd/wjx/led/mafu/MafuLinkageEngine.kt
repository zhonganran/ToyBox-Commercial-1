package com.byd.wjx.led.mafu

import com.byd.wjx.led.LedLinkageMatcher
import com.byd.wjx.led.LedVehicleSignals

/** Keeps the selected Mafu remote-key program active while its condition remains true. */
class MafuLinkageEngine {
    private var boundRules: List<MafuLinkageRule> = emptyList()
    private val previousMatches = linkedMapOf<String, Boolean>()
    private var activeRuleId: String? = null
    private var displayedKey: MafuRemoteKey? = null
    private var resumeProgramListPending = false

    fun evaluate(
        rules: List<MafuLinkageRule>,
        model: MafuRemoteModel,
        signals: LedVehicleSignals,
        now: Long,
    ): MafuRemoteKey? {
        bindRules(rules, model)
        val currentMatches = matchesByRule(signals)
        val hadActiveRule = activeRuleId != null
        val activeRule = activeRuleId?.let { id -> boundRules.firstOrNull { it.id == id } }
        val activeStillMatches = activeRule?.let { currentMatches[it.id] == true } == true
        val activeIndex = activeRule?.let { boundRules.indexOf(it) } ?: -1
        val higherPriorityTrigger = if (activeStillMatches) {
            boundRules.take(activeIndex.coerceAtLeast(0)).firstOrNull { rule ->
                rule.enabled &&
                    currentMatches[rule.id] == true &&
                    previousMatches[rule.id] != true
            }
        } else {
            null
        }
        val triggered = higherPriorityTrigger ?: if (!activeStillMatches) {
            boundRules.firstOrNull { rule -> rule.enabled && currentMatches[rule.id] == true }
        } else {
            null
        }
        captureMatches(currentMatches)
        when {
            triggered != null -> {
                activeRuleId = triggered.id
                resumeProgramListPending = false
            }
            !activeStillMatches -> {
                activeRuleId = null
                if (hadActiveRule) resumeProgramListPending = true
            }
        }
        return triggered?.let { startPlayback(it) }
    }

    fun consumeProgramListResume(): Boolean {
        if (!resumeProgramListPending) return false
        resumeProgramListPending = false
        displayedKey = null
        return true
    }

    fun reset() {
        boundRules = emptyList()
        previousMatches.clear()
        activeRuleId = null
        displayedKey = null
        resumeProgramListPending = false
    }

    private fun bindRules(rules: List<MafuLinkageRule>, model: MafuRemoteModel) {
        val normalized = rules.map { it.normalized(model) }
        if (normalized == boundRules) return
        boundRules = normalized
        previousMatches.clear()
        activeRuleId = null
        resumeProgramListPending = false
    }

    private fun matchesByRule(signals: LedVehicleSignals): LinkedHashMap<String, Boolean> =
        boundRules.associateTo(linkedMapOf()) { rule -> rule.id to rule.matches(signals) }

    private fun captureMatches(matches: Map<String, Boolean>) {
        previousMatches.keys.retainAll(matches.keys)
        previousMatches.putAll(matches)
    }

    private fun startPlayback(rule: MafuLinkageRule): MafuRemoteKey? {
        return dispatch(rule.steps.first().remoteKey, force = true)
    }

    private fun dispatch(key: MafuRemoteKey, force: Boolean = false): MafuRemoteKey? {
        if (!force && displayedKey == key) return null
        displayedKey = key
        return key
    }

    private fun MafuLinkageRule.matches(signals: LedVehicleSignals): Boolean {
        if (!enabled) return false
        return LedLinkageMatcher.matches(triggerType, conditionValue, signals)
    }

}
