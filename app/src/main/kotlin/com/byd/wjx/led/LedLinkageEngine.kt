package com.byd.wjx.led

/**
 * Stateful linkage evaluator matching the Modi behavior: rising-edge triggers, saved-order
 * precedence, uninterrupted scene sequences, and a delayed return to the default scene.
 */
class LedLinkageEngine(
    private val clearDelayMs: Long = 5_000L,
) {
    private var boundRules: List<LedLinkageRule> = emptyList()
    private val previousMatches = linkedMapOf<String, Boolean>()
    private var playback: Playback? = null
    private var noConditionSince: Long? = null
    private var displayedScene = LedScene.DEFAULT

    fun evaluate(
        rules: List<LedLinkageRule>,
        signals: LedVehicleSignals,
        now: Long,
    ): LedScene? {
        bindRules(rules)
        playback?.let { active ->
            val elapsed = (now - active.startedAt).coerceAtLeast(0L)
            var stepStart = 0L
            active.rule.steps.forEachIndexed { index, step ->
                val stepEnd = stepStart + step.durationSeconds * 1_000L
                if (elapsed < stepEnd) {
                    if (index != active.stepIndex) {
                        playback = active.copy(stepIndex = index)
                        return dispatch(step.scene, force = true)
                    }
                    return null
                }
                stepStart = stepEnd
            }

            playback = null
            val currentMatches = matchesByRule(signals)
            captureMatches(currentMatches)
            updateNoConditionTimer(currentMatches.values.any { it }, now)
            return maybeRestoreDefault(now)
        }

        val currentMatches = matchesByRule(signals)
        val triggered = boundRules.firstOrNull { rule ->
            rule.enabled &&
                currentMatches[rule.id] == true &&
                previousMatches[rule.id] != true
        }
        captureMatches(currentMatches)

        if (triggered != null) {
            noConditionSince = null
            playback = Playback(triggered, startedAt = now, stepIndex = 0)
            return dispatch(triggered.steps.first().scene, force = true)
        }

        updateNoConditionTimer(currentMatches.values.any { it }, now)
        return maybeRestoreDefault(now)
    }

    fun reset() {
        boundRules = emptyList()
        previousMatches.clear()
        playback = null
        noConditionSince = null
        displayedScene = LedScene.DEFAULT
    }

    private fun bindRules(rules: List<LedLinkageRule>) {
        val normalized = rules.map(LedLinkageRule::normalized)
        if (normalized == boundRules) return
        boundRules = normalized
        previousMatches.clear()
        playback = null
        noConditionSince = null
    }

    private fun matchesByRule(signals: LedVehicleSignals): LinkedHashMap<String, Boolean> =
        boundRules.associateTo(linkedMapOf()) { rule -> rule.id to rule.matches(signals) }

    private fun captureMatches(matches: Map<String, Boolean>) {
        previousMatches.keys.retainAll(matches.keys)
        previousMatches.putAll(matches)
    }

    private fun updateNoConditionTimer(anyConditionActive: Boolean, now: Long) {
        noConditionSince = if (anyConditionActive) null else noConditionSince ?: now
    }

    private fun maybeRestoreDefault(now: Long): LedScene? {
        val inactiveAt = noConditionSince ?: return null
        if (now - inactiveAt < clearDelayMs) return null
        return dispatch(LedScene.DEFAULT)
    }

    private fun dispatch(scene: LedScene, force: Boolean = false): LedScene? {
        if (!force && displayedScene == scene) return null
        displayedScene = scene
        return scene
    }

    private fun LedLinkageRule.matches(signals: LedVehicleSignals): Boolean {
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
        val rule: LedLinkageRule,
        val startedAt: Long,
        val stepIndex: Int,
    )
}
