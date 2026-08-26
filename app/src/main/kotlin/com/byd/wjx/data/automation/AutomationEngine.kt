package com.byd.wjx.data.automation

import android.content.Context
import android.location.Location
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationEngine @Inject constructor(
    private val ruleRepository: AutomationRuleRepository,
    journalRepository: AutomationJournalRepository,
    private val actionDispatcher: ActionDispatcher,
    private val automationConfigRepository: AutomationConfigRepository,
    private val networkAvailableMonitor: NetworkAvailableMonitor,
    private val activationManager: ActivationManager,
    vehicleStateRepository: VehicleStateRepository,
    private val trackingStateRepository: TrackingStateRepository,
    private val confirmationPresenter: AutomationConfirmationPresenter,
    @ApplicationContext context: Context,
) {
    companion object {
        private const val TAG = "AutomationEngine"
        const val ACTION_CONFIRM = AUTOMATION_CONFIRM_ACTION
        const val ACTION_CANCEL = AUTOMATION_CANCEL_ACTION
        const val EXTRA_NOTIF_ID = AUTOMATION_NOTIFICATION_ID_EXTRA
        const val SERVICE_START_WINDOW_MS = AUTOMATION_SERVICE_START_WINDOW_MS
        internal const val STATE_STABLE_SAMPLE_COUNT = AUTOMATION_STATE_STABLE_SAMPLE_COUNT
        internal const val LOCATION_MAX_AGE_MS = 120_000L
        private const val STALE_LOCATION_LOG_INTERVAL_MS = 60_000L
        private const val MAX_AUTOMATION_RULES = 50
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val triggerEvaluator = AutomationTriggerEvaluator()
    private val boardVehicleDetector = BoardVehicleDetector()
    private val runtimeState = AutomationRuleRuntimeState()
    private val executionCoordinator = AutomationActionExecutionCoordinator(
        ruleRepository = ruleRepository,
        journalRepository = journalRepository,
        actionDispatcher = actionDispatcher,
        vehicleStateRepository = vehicleStateRepository,
    )
    private val confirmationNotifications = AutomationConfirmationNotificationGateway(
        context = context,
        vehicleStateRepository = vehicleStateRepository,
        executionCoordinator = executionCoordinator,
        scope = scope,
    )

    private var lastStaleLocationLogAtMs = 0L
    private var lastStaleLocationTimeMs = Long.MIN_VALUE
    internal var nowProvider: () -> Long = System::currentTimeMillis

    suspend fun evaluate(data: DiParsData, tripStartedAt: Long?) {
        confirmationNotifications.cleanupExpired()
        evaluateRules(
            data = data,
            tripStartedAt = tripStartedAt,
            boardVehicleEdge = boardVehicleDetector.onSample(data),
            allowIntervalPulse = true,
        )
    }

    private suspend fun evaluateRules(
        data: DiParsData,
        tripStartedAt: Long?,
        boardVehicleEdge: Boolean,
        allowIntervalPulse: Boolean,
    ): Int {
        val now = nowProvider()
        val location = freshLocation(trackingStateRepository.lastLocation.value, now)
        val rules = selectedRules(automationConfigRepository.current().enabledRules)
        runtimeState.armServiceStartWindow(now)

        val activeRuleIds = rules.mapTo(HashSet()) { it.id }
        actionDispatcher.stopMediaOwnedByInactiveRules(activeRuleIds)
        runtimeState.retainRules(activeRuleIds)
        triggerEvaluator.retainRules(activeRuleIds)

        var executedCount = 0
        rules.forEach { rule ->
            try {
                executedCount += evaluateRule(
                    rule = rule,
                    data = data,
                    location = location,
                    tripStartedAt = tripStartedAt,
                    boardVehicleEdge = boardVehicleEdge,
                    allowIntervalPulse = allowIntervalPulse,
                    now = now,
                )
            } catch (error: Exception) {
                WjxFileLogger.e(
                    TAG,
                    "Error evaluating rule '${rule.name}': ${error.message}",
                    error,
                )
            }
        }
        return executedCount
    }

    private fun selectedRules(enabledRules: List<RuleEntity>): List<RuleEntity> =
        if (activationManager.canUse(PremiumFeature.AUTOMATION_UNLIMITED)) {
            enabledRules.take(MAX_AUTOMATION_RULES)
        } else {
            enabledRules.sortedByDescending { it.createdAt }
                .take(ActivationManager.FREE_AUTOMATION_RULE_LIMIT)
        }

    private suspend fun evaluateRule(
        rule: RuleEntity,
        data: DiParsData,
        location: Location?,
        tripStartedAt: Long?,
        boardVehicleEdge: Boolean,
        allowIntervalPulse: Boolean,
        now: Long,
    ): Int {
        val triggers = TriggerDef.listFromJson(rule.triggers)
        if (triggers.isEmpty()) return 0

        val definitionHash = (
            rule.triggers + rule.triggerLogic + rule.actions + rule.exitActions
        ).hashCode()
        val previous = runtimeState.previous(rule.id)
        if (previous != null && previous.definitionHash != definitionHash) {
            actionDispatcher.stopMediaOwnedByRule(rule.id)
        }

        val networkEdge = runtimeState.consumeNetworkEdge(
            ruleId = rule.id,
            edgeAt = networkAvailableMonitor.lastAvailableAt,
            probePending = networkAvailableMonitor.probePending,
        )
        val serviceStartActive = runtimeState.serviceStartActive(rule.id, now)
        val perTrigger = triggerEvaluator.evaluateEachTrigger(
            ruleId = rule.id,
            triggers = triggers,
            data = data,
            location = location,
            serviceStartActive = serviceStartActive,
            networkAvailableEdge = networkEdge,
            boardVehicleEdge = boardVehicleEdge,
            now = now,
            allowIntervalPulse = allowIntervalPulse,
        )
        val rawMatched = triggerEvaluator.combineByLogic(perTrigger, rule.triggerLogic)
            ?: return 0
        val matchedViaEvent = triggerEvaluator.matchedViaEventTrigger(
            triggers = triggers,
            perTrigger = perTrigger,
            logic = rule.triggerLogic,
        )
        val transition = if (matchedViaEvent) {
            eventTransition(
                previous = previous,
                definitionHash = definitionHash,
                rawMatched = rawMatched,
                persistentMatched = triggerEvaluator.persistentMatch(
                    triggers = triggers,
                    perTrigger = perTrigger,
                    logic = rule.triggerLogic,
                ),
            )
        } else {
            runtimeState.stabilize(
                previous = previous,
                definitionHash = definitionHash,
                rawMatched = rawMatched,
                edgeEligibleNow = if (rawMatched) {
                    runtimeState.isEnterEdgeEligible(rule, data, tripStartedAt, now)
                } else {
                    runtimeState.isExitEdgeEligible(rule, data, now)
                },
            )
        }
        runtimeState.save(rule.id, transition.state)

        var executedCount = 0
        if (transition.exited) {
            executionCoordinator.stopPhaseMedia(
                ruleId = rule.id,
                actions = ActionDef.listFromJson(rule.actions),
                ownerPhase = "enter",
            )
            if (transition.edgeEligible && dispatchRulePhase(
                    rule = rule,
                    actions = ActionDef.listFromJson(rule.exitActions),
                    triggers = triggers,
                    data = data,
                    tripStartedAt = tripStartedAt,
                    now = now,
                    phase = "exit",
                )) {
                executedCount++
            }
        }

        val repeatDue = !matchedViaEvent && transition.state.matched &&
            !transition.entered && rule.repeatIntervalSeconds >= 2 &&
            now - runtimeState.effectiveLastEnterAt(rule) >=
            rule.repeatIntervalSeconds * 1000L
        if (transition.entered && previous != null) {
            executionCoordinator.stopPhaseMedia(
                ruleId = rule.id,
                actions = ActionDef.listFromJson(rule.exitActions),
                ownerPhase = "exit",
            )
        }
        if ((transition.entered && transition.edgeEligible) || repeatDue) {
            val phase = if (repeatDue) "repeat" else "enter"
            if (dispatchRulePhase(
                    rule = rule,
                    actions = ActionDef.listFromJson(rule.actions),
                    triggers = triggers,
                    data = data,
                    tripStartedAt = tripStartedAt,
                    now = now,
                    phase = phase,
                )) {
                if (serviceStartActive && triggers.any { it.kind == "service_start" }) {
                    runtimeState.consumeServiceStart(rule.id)
                }
                executedCount++
            }
        }
        return executedCount
    }

    private fun eventTransition(
        previous: AutomationEvalState?,
        definitionHash: Int,
        rawMatched: Boolean,
        persistentMatched: Boolean?,
    ): AutomationStableTransition = AutomationStableTransition(
        state = when (persistentMatched) {
            true -> AutomationEvalState(definitionHash, true)
            false -> AutomationEvalState(definitionHash, false)
            null -> previous?.takeIf { it.definitionHash == definitionHash }
                ?: AutomationEvalState(definitionHash, false)
        },
        entered = rawMatched,
    )

    private suspend fun dispatchRulePhase(
        rule: RuleEntity,
        actions: List<ActionDef>,
        triggers: List<TriggerDef>,
        data: DiParsData,
        tripStartedAt: Long?,
        now: Long,
        phase: String,
    ): Boolean {
        if (actions.isEmpty()) return false
        if (rule.requirePark && data.gear != 1) {
            WjxFileLogger.i(
                TAG,
                "rule phase blocked by park-only name='${rule.name}' phase=$phase gear=${data.gear}",
            )
            return false
        }
        if (phase == "exit") {
            val cooldownMs = rule.cooldownSeconds * 1000L
            val elapsed = now - runtimeState.lastExitAt(rule.id)
            if (elapsed < cooldownMs) {
                WjxFileLogger.i(
                    TAG,
                    "exit phase blocked by cooldown name='${rule.name}' remainingMs=${cooldownMs - elapsed}",
                )
                return false
            }
            runtimeState.recordExit(rule.id, now)
        } else {
            val lastFired = runtimeState.effectiveLastEnterAt(rule)
            val repeatMs = if (phase == "repeat") rule.repeatIntervalSeconds * 1000L else 0L
            val gateMs = maxOf(rule.cooldownSeconds * 1000L, repeatMs)
            val elapsed = now - lastFired
            if (elapsed < gateMs) {
                WjxFileLogger.i(
                    TAG,
                    "rule phase blocked by cooldown name='${rule.name}' phase=$phase remainingMs=${gateMs - elapsed}",
                )
                return false
            }
            if (rule.fireOncePerTrip && runtimeState.hasFiredTrip(rule.id, tripStartedAt)) {
                WjxFileLogger.i(
                    TAG,
                    "rule phase blocked by once-per-trip name='${rule.name}' phase=$phase tripStartedAt=$tripStartedAt",
                )
                return false
            }
            ruleRepository.markTriggered(rule.id, now)
            runtimeState.recordEnter(rule, tripStartedAt, now)
        }

        val snapshot = JSONObject(triggerEvaluator.buildSnapshot(triggers, data))
            .apply { put("phase", phase) }
            .toString()
        WjxFileLogger.i(
            TAG,
            "rule phase ready name='${rule.name}' phase=$phase actions=${actions.size} " +
                "confirm=${rule.confirmBeforeExecute} snapshot=$snapshot",
        )
        dispatchActions(rule, actions, snapshot, data, now, phase)
        return true
    }

    private fun dispatchActions(
        rule: RuleEntity,
        actions: List<ActionDef>,
        snapshot: String,
        data: DiParsData,
        now: Long,
        phase: String,
    ) {
        if (!rule.confirmBeforeExecute) {
            scope.launch {
                executionCoordinator.executeAndLog(rule, actions, snapshot, data, phase)
            }
            return
        }

        val shown = confirmationPresenter.show(
            ruleName = rule.name,
            actionsSummary = actions.joinToString(", ") { it.displayName },
            onConfirm = {
                scope.launch {
                    executionCoordinator.executeConfirmed(rule, actions, snapshot, data, phase)
                }
            },
            onCancel = {
                scope.launch {
                    executionCoordinator.recordConfirmationOutcome(
                        rule = rule,
                        snapshot = snapshot,
                        triggeredAt = now,
                        result = "cancelled",
                        phase = phase,
                    )
                }
            },
        )
        if (!shown) {
            confirmationNotifications.show(rule, actions, snapshot, phase)
        }
    }

    private fun freshLocation(location: Location?, now: Long): Location? {
        location ?: return null
        val locationTime = location.time
        if (locationTime > 0L && now - locationTime > LOCATION_MAX_AGE_MS) {
            val shouldLog = locationTime != lastStaleLocationTimeMs ||
                lastStaleLocationLogAtMs == 0L ||
                now - lastStaleLocationLogAtMs >= STALE_LOCATION_LOG_INTERVAL_MS
            if (shouldLog) {
                lastStaleLocationTimeMs = locationTime
                lastStaleLocationLogAtMs = now
                WjxFileLogger.i(TAG, "ignore stale automation location ageMs=${now - locationTime}")
            }
            return null
        }
        lastStaleLocationTimeMs = Long.MIN_VALUE
        return location
    }
}
