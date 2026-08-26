package com.byd.wjx.data.automation

import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.remote.DiParsData
import java.util.concurrent.ConcurrentHashMap

internal const val AUTOMATION_SERVICE_START_WINDOW_MS = 30_000L
internal const val AUTOMATION_STATE_STABLE_SAMPLE_COUNT = 2

internal data class AutomationEvalState(
    val definitionHash: Int,
    val matched: Boolean,
    val candidateMatched: Boolean? = null,
    val candidateSamples: Int = 0,
    val candidateEligible: Boolean = true,
)

internal data class AutomationStableTransition(
    val state: AutomationEvalState,
    val entered: Boolean = false,
    val exited: Boolean = false,
    val initialEnter: Boolean = false,
    val edgeEligible: Boolean = true,
)

internal class AutomationRuleRuntimeState(
    private val stableSampleCount: Int = AUTOMATION_STATE_STABLE_SAMPLE_COUNT,
    private val serviceStartWindowMs: Long = AUTOMATION_SERVICE_START_WINDOW_MS,
) {
    private val evaluations = ConcurrentHashMap<Long, AutomationEvalState>()
    private val lastFiredTripByRule = ConcurrentHashMap<Long, Long>()
    private val lastSeenNetworkAvailableAt = ConcurrentHashMap<Long, Long>()
    private val lastEnteredAtByRule = ConcurrentHashMap<Long, Long>()
    private val lastExitedAtByRule = ConcurrentHashMap<Long, Long>()
    private val serviceStartConsumed = ConcurrentHashMap.newKeySet<Long>()
    private var serviceStartWindowEnd = 0L

    fun armServiceStartWindow(now: Long) {
        if (serviceStartWindowEnd == 0L) serviceStartWindowEnd = now + serviceStartWindowMs
    }

    fun serviceStartActive(ruleId: Long, now: Long): Boolean =
        now <= serviceStartWindowEnd && ruleId !in serviceStartConsumed

    fun consumeServiceStart(ruleId: Long) {
        serviceStartConsumed.add(ruleId)
    }

    fun retainRules(activeRuleIds: Set<Long>) {
        evaluations.keys.retainAll(activeRuleIds)
        lastFiredTripByRule.keys.retainAll(activeRuleIds)
        lastSeenNetworkAvailableAt.keys.retainAll(activeRuleIds)
        lastEnteredAtByRule.keys.retainAll(activeRuleIds)
        lastExitedAtByRule.keys.retainAll(activeRuleIds)
        serviceStartConsumed.retainAll(activeRuleIds)
    }

    fun previous(ruleId: Long): AutomationEvalState? = evaluations[ruleId]

    fun save(ruleId: Long, state: AutomationEvalState) {
        evaluations[ruleId] = state
    }

    fun consumeNetworkEdge(ruleId: Long, edgeAt: Long, probePending: Boolean): Boolean {
        val seenAt = lastSeenNetworkAvailableAt[ruleId]
        return when {
            seenAt == null && probePending -> false
            seenAt == null -> {
                lastSeenNetworkAvailableAt[ruleId] = edgeAt
                false
            }
            else -> {
                val isEdge = edgeAt > 0L && edgeAt > seenAt
                if (isEdge) lastSeenNetworkAvailableAt[ruleId] = edgeAt
                isEdge
            }
        }
    }

    fun stabilize(
        previous: AutomationEvalState?,
        definitionHash: Int,
        rawMatched: Boolean,
        edgeEligibleNow: Boolean,
    ): AutomationStableTransition {
        if (previous == null) {
            return AutomationStableTransition(
                state = AutomationEvalState(definitionHash, rawMatched),
                entered = rawMatched,
                initialEnter = rawMatched,
                edgeEligible = edgeEligibleNow,
            )
        }
        if (previous.definitionHash != definitionHash) {
            return AutomationStableTransition(AutomationEvalState(definitionHash, rawMatched))
        }
        if (rawMatched == previous.matched) {
            return AutomationStableTransition(
                previous.copy(
                    candidateMatched = null,
                    candidateSamples = 0,
                    candidateEligible = true,
                ),
            )
        }
        val samples = if (previous.candidateMatched == rawMatched) {
            previous.candidateSamples + 1
        } else {
            1
        }
        val eligible = if (previous.candidateMatched == rawMatched) {
            previous.candidateEligible && edgeEligibleNow
        } else {
            edgeEligibleNow
        }
        if (samples < stableSampleCount) {
            return AutomationStableTransition(
                previous.copy(
                    candidateMatched = rawMatched,
                    candidateSamples = samples,
                    candidateEligible = eligible,
                ),
            )
        }
        return AutomationStableTransition(
            state = AutomationEvalState(definitionHash, rawMatched),
            entered = rawMatched,
            exited = !rawMatched,
            edgeEligible = eligible,
        )
    }

    fun effectiveLastEnterAt(rule: RuleEntity): Long =
        maxOf(rule.lastTriggeredAt ?: 0L, lastEnteredAtByRule[rule.id] ?: 0L)

    fun isEnterEdgeEligible(
        rule: RuleEntity,
        data: DiParsData,
        tripStartedAt: Long?,
        now: Long,
    ): Boolean {
        if (rule.requirePark && data.gear != 1) return false
        if (now - effectiveLastEnterAt(rule) < rule.cooldownSeconds * 1000L) return false
        return !rule.fireOncePerTrip || !hasFiredTrip(rule.id, tripStartedAt)
    }

    fun isExitEdgeEligible(rule: RuleEntity, data: DiParsData, now: Long): Boolean {
        if (rule.requirePark && data.gear != 1) return false
        return now - lastExitAt(rule.id) >= rule.cooldownSeconds * 1000L
    }

    fun hasFiredTrip(ruleId: Long, tripStartedAt: Long?): Boolean =
        tripStartedAt != null && lastFiredTripByRule[ruleId] == tripStartedAt

    fun recordEnter(rule: RuleEntity, tripStartedAt: Long?, now: Long) {
        lastEnteredAtByRule[rule.id] = now
        if (rule.fireOncePerTrip && tripStartedAt != null) {
            lastFiredTripByRule[rule.id] = tripStartedAt
        }
    }

    fun lastExitAt(ruleId: Long): Long = lastExitedAtByRule[ruleId] ?: 0L

    fun recordExit(ruleId: Long, now: Long) {
        lastExitedAtByRule[ruleId] = now
    }
}
