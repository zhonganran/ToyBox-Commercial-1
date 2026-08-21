package com.byd.wjx.led.ipixelfa

import com.byd.wjx.led.LedLinkageMatcher
import com.byd.wjx.led.LedVehicleSignals

/** Keeps the selected firmware preset active while its vehicle condition remains true. */
class IpixelFaLinkageEngine {
    private var boundRules: List<IpixelFaLinkageRule> = emptyList()
    private val previousMatches = linkedMapOf<String, Boolean>()
    private var activeRuleId: String? = null
    private var displayedPreset: IpixelFaPreset? = null
    private var lastPresetDispatchedAt: Long? = null
    private var programListRestorePending = false

    fun evaluate(
        rules: List<IpixelFaLinkageRule>,
        signals: LedVehicleSignals,
        now: Long,
    ): IpixelFaPreset? {
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
                programListRestorePending = false
            }
            !activeStillMatches -> {
                if (hadActiveRule) programListRestorePending = true
                activeRuleId = null
            }
        }
        return when {
            triggered != null -> startPlayback(triggered, now)
            activeStillMatches -> refreshPlayback(now)
            else -> null
        }
    }

    /** True after a linkage condition ends and the stored device program list must be resumed. */
    fun isProgramListRestorePending(): Boolean = programListRestorePending

    fun consumeProgramListRestore(): Boolean {
        if (!programListRestorePending) return false
        programListRestorePending = false
        displayedPreset = null
        lastPresetDispatchedAt = null
        return true
    }

    fun reset() {
        boundRules = emptyList()
        previousMatches.clear()
        activeRuleId = null
        displayedPreset = null
        lastPresetDispatchedAt = null
        programListRestorePending = false
    }

    private fun bindRules(rules: List<IpixelFaLinkageRule>) {
        val normalized = rules.map(IpixelFaLinkageRule::normalized)
        if (normalized == boundRules) return
        boundRules = normalized
        previousMatches.clear()
        activeRuleId = null
        displayedPreset = null
        lastPresetDispatchedAt = null
        programListRestorePending = false
    }

    private fun matchesByRule(signals: LedVehicleSignals): LinkedHashMap<String, Boolean> =
        boundRules.associateTo(linkedMapOf()) { rule -> rule.id to rule.matches(signals) }

    private fun captureMatches(matches: Map<String, Boolean>) {
        previousMatches.keys.retainAll(matches.keys)
        previousMatches.putAll(matches)
    }

    private fun startPlayback(rule: IpixelFaLinkageRule, now: Long): IpixelFaPreset? {
        return dispatch(rule.steps.first().preset, now, force = true)
    }

    private fun refreshPlayback(now: Long): IpixelFaPreset? {
        val preset = displayedPreset ?: return null
        val lastDispatchedAt = lastPresetDispatchedAt ?: return dispatch(preset, now, force = true)
        if (
            now < lastDispatchedAt ||
            now - lastDispatchedAt < IpixelFaProtocol.REMOTE_PRESET_REFRESH_MS
        ) {
            return null
        }
        return dispatch(preset, now, force = true)
    }

    private fun dispatch(
        preset: IpixelFaPreset,
        now: Long,
        force: Boolean = false,
    ): IpixelFaPreset? {
        if (!force && displayedPreset == preset) return null
        displayedPreset = preset
        lastPresetDispatchedAt = now
        return preset
    }

    private fun IpixelFaLinkageRule.matches(signals: LedVehicleSignals): Boolean {
        if (!enabled) return false
        return LedLinkageMatcher.matches(triggerType, conditionValue, signals)
    }

}
