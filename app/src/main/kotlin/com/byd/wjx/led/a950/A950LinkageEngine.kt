package com.byd.wjx.led.a950

import com.byd.wjx.led.LedLinkageTriggerType
import com.byd.wjx.led.LedVehicleSignals

/** Keeps the selected A950 preset active while its vehicle condition remains true. */
class A950LinkageEngine {
    private var boundRules: List<A950LinkageRule> = emptyList()
    private val previousMatches = linkedMapOf<String, Boolean>()
    private var activeRuleId: String? = null
    private var displayedPreset: A950Preset? = null
    private var resumeProgramListPending = false

    fun evaluate(
        rules: List<A950LinkageRule>,
        signals: LedVehicleSignals,
        now: Long,
    ): A950Preset? {
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
        displayedPreset = null
        return true
    }

    fun reset() {
        boundRules = emptyList()
        previousMatches.clear()
        activeRuleId = null
        displayedPreset = null
        resumeProgramListPending = false
    }

    private fun bindRules(rules: List<A950LinkageRule>) {
        val normalized = rules.map(A950LinkageRule::normalized)
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

    private fun startPlayback(rule: A950LinkageRule): A950Preset? {
        return dispatch(rule.steps.first().preset, force = true)
    }

    private fun dispatch(preset: A950Preset, force: Boolean = false): A950Preset? {
        if (!force && displayedPreset == preset) return null
        displayedPreset = preset
        return preset
    }

    private fun A950LinkageRule.matches(signals: LedVehicleSignals): Boolean {
        if (!enabled) return false
        return when (triggerType) {
            LedLinkageTriggerType.SPEED -> when (conditionValue) {
                "gt_30" -> signals.speed?.let { it > 30 } == true
                "gt_60" -> signals.speed?.let { it > 60 } == true
                "gt_80" -> signals.speed?.let { it > 80 } == true
                "gt_100" -> signals.speed?.let { it > 100 } == true
                "lt_20" -> signals.speed?.let { it < 20 } == true
                else -> false
            }
            LedLinkageTriggerType.GEAR -> signals.gear == when (conditionValue) {
                "P" -> 1
                "R" -> 2
                "N" -> 3
                "D" -> 4
                "S" -> 5
                "M" -> 6
                else -> null
            }
            LedLinkageTriggerType.ACCELERATOR ->
                signals.accelPedalDepth.exceedsEncodedThreshold(conditionValue)
            LedLinkageTriggerType.BRAKE ->
                signals.brakePedalDepth.exceedsEncodedThreshold(conditionValue)
            LedLinkageTriggerType.DOOR -> when (conditionValue) {
                "front_left" -> signals.doorFL == 1
                "front_right" -> signals.doorFR == 1
                "rear_left" -> signals.doorRL == 1
                "rear_right" -> signals.doorRR == 1
                "trunk" -> signals.trunk == 1
                else -> false
            }
            LedLinkageTriggerType.LIGHT -> when (conditionValue) {
                "left_turn" -> signals.turnSignalFlash == 2
                "right_turn" -> signals.turnSignalFlash == 4
                "hazard" -> signals.turnSignalFlash == 6
                "brake" -> signals.lightBrake == 1
                "reverse" -> signals.lightReversing == 1
                else -> false
            }
        }
    }

    private fun Int?.exceedsEncodedThreshold(condition: String): Boolean {
        val threshold = condition.removePrefix("gt_").toIntOrNull() ?: return false
        return this?.let { it > threshold } == true
    }

}
