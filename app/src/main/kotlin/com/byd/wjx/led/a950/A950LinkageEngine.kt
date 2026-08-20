package com.byd.wjx.led.a950

import com.byd.wjx.led.LedLinkageTriggerType
import com.byd.wjx.led.LedVehicleSignals

/** Evaluates only A950 rules and emits only A950 firmware presets. */
class A950LinkageEngine {
    private var boundRules: List<A950LinkageRule> = emptyList()
    private val previousMatches = linkedMapOf<String, Boolean>()
    private var playback: Playback? = null
    private var displayedPreset: A950Preset? = null

    fun evaluate(
        rules: List<A950LinkageRule>,
        signals: LedVehicleSignals,
        now: Long,
    ): A950Preset? {
        bindRules(rules)
        val currentMatches = matchesByRule(signals)
        playback?.let { active ->
            val activeIndex = boundRules.indexOfFirst { it.id == active.rule.id }
            val higherPriorityTrigger = boundRules
                .take(activeIndex.coerceAtLeast(0))
                .firstOrNull { rule ->
                    rule.enabled &&
                        currentMatches[rule.id] == true &&
                        previousMatches[rule.id] != true
                }
            captureMatches(currentMatches)
            if (higherPriorityTrigger != null) {
                return startPlayback(higherPriorityTrigger, now)
            }

            val elapsed = (now - active.startedAt).coerceAtLeast(0L)
            var stepStart = 0L
            active.rule.steps.forEachIndexed { index, step ->
                val stepEnd = stepStart + step.durationSeconds * 1_000L
                if (elapsed < stepEnd) {
                    if (index != active.stepIndex) {
                        playback = active.copy(stepIndex = index)
                        return dispatch(step.preset, force = true)
                    }
                    return null
                }
                stepStart = stepEnd
            }

            playback = null
            return null
        }

        val triggered = boundRules.firstOrNull { rule ->
            rule.enabled &&
                currentMatches[rule.id] == true &&
                previousMatches[rule.id] != true
        }
        captureMatches(currentMatches)
        return triggered?.let { startPlayback(it, now) }
    }

    fun reset() {
        boundRules = emptyList()
        previousMatches.clear()
        playback = null
        displayedPreset = null
    }

    private fun bindRules(rules: List<A950LinkageRule>) {
        val normalized = rules.map(A950LinkageRule::normalized)
        if (normalized == boundRules) return
        boundRules = normalized
        previousMatches.clear()
        playback = null
    }

    private fun matchesByRule(signals: LedVehicleSignals): LinkedHashMap<String, Boolean> =
        boundRules.associateTo(linkedMapOf()) { rule -> rule.id to rule.matches(signals) }

    private fun captureMatches(matches: Map<String, Boolean>) {
        previousMatches.keys.retainAll(matches.keys)
        previousMatches.putAll(matches)
    }

    private fun startPlayback(rule: A950LinkageRule, now: Long): A950Preset? {
        playback = Playback(rule, startedAt = now, stepIndex = 0)
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

    private data class Playback(
        val rule: A950LinkageRule,
        val startedAt: Long,
        val stepIndex: Int,
    )
}
