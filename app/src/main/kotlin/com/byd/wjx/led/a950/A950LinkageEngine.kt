package com.byd.wjx.led.a950

import com.byd.wjx.led.LedLinkageMatcher
import com.byd.wjx.led.LedVehicleSignals

sealed interface A950LinkageDecision {
    data object None : A950LinkageDecision
    data class Play(val preset: A950Preset) : A950LinkageDecision
    data object RestoreGraffiti : A950LinkageDecision
}

/** Keeps the selected A950 preset active while its vehicle condition remains true. */
class A950LinkageEngine {
    private var boundRules: List<A950LinkageRule> = emptyList()
    private val previousMatches = linkedMapOf<String, Boolean>()
    private var activeRuleId: String? = null

    fun evaluate(
        rules: List<A950LinkageRule>,
        signals: LedVehicleSignals,
        now: Long,
    ): A950Preset? = when (val decision = evaluateDecision(rules, signals, now)) {
        is A950LinkageDecision.Play -> decision.preset
        A950LinkageDecision.None,
        A950LinkageDecision.RestoreGraffiti,
        -> null
    }

    fun evaluateDecision(
        rules: List<A950LinkageRule>,
        signals: LedVehicleSignals,
        now: Long,
    ): A950LinkageDecision {
        bindRules(rules)
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
            triggered != null -> activeRuleId = triggered.id
            !activeStillMatches -> activeRuleId = null
        }
        return when {
            triggered != null -> A950LinkageDecision.Play(
                triggered.steps.firstOrNull()?.preset ?: A950Preset.TEMPORARY_STOP,
            )
            hadActiveRule && !activeStillMatches -> A950LinkageDecision.RestoreGraffiti
            else -> A950LinkageDecision.None
        }
    }

    fun reset() {
        boundRules = emptyList()
        previousMatches.clear()
        activeRuleId = null
    }

    private fun bindRules(rules: List<A950LinkageRule>) {
        val normalized = rules.map(A950LinkageRule::normalized)
        if (normalized == boundRules) return
        boundRules = normalized
        previousMatches.clear()
        activeRuleId = null
    }

    private fun matchesByRule(signals: LedVehicleSignals): LinkedHashMap<String, Boolean> =
        boundRules.associateTo(linkedMapOf()) { rule -> rule.id to rule.matches(signals) }

    private fun captureMatches(matches: Map<String, Boolean>) {
        previousMatches.keys.retainAll(matches.keys)
        previousMatches.putAll(matches)
    }

    private fun A950LinkageRule.matches(signals: LedVehicleSignals): Boolean {
        if (!enabled) return false
        return LedLinkageMatcher.matches(triggerType, conditionValue, signals)
    }
}
