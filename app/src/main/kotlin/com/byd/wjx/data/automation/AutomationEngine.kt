package com.byd.wjx.data.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.dao.RuleLogDao
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.R
import com.byd.wjx.data.repository.TrackingStateRepository
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationEngine @Inject constructor(
    private val ruleDao: RuleDao,
    private val ruleLogDao: RuleLogDao,
    private val actionDispatcher: ActionDispatcher,
    private val automationConfigRepository: AutomationConfigRepository,
    private val networkAvailableMonitor: NetworkAvailableMonitor,
    private val activationManager: ActivationManager,
    private val vehicleStateRepository: VehicleStateRepository,
    private val trackingStateRepository: TrackingStateRepository,
    private val confirmationPresenter: AutomationConfirmationPresenter,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AutomationEngine"
        private const val CONFIRM_CHANNEL_ID = "wjx_automation_confirm"
        private const val CONFIRM_TIMEOUT_MS = 30_000L
        private const val NOTIF_BASE_ID = 5000

        const val ACTION_CONFIRM = "com.byd.wjx.AUTOMATION_CONFIRM"
        const val ACTION_CANCEL = "com.byd.wjx.AUTOMATION_CANCEL"
        const val EXTRA_NOTIF_ID = "notif_id"

        // How long after the first evaluate() the service_start trigger stays
        // armed, giving cold-start params a few polls to warm up.
        const val SERVICE_START_WINDOW_MS = 30_000L
        internal const val STATE_STABLE_SAMPLE_COUNT = 2
        internal const val LOCATION_MAX_AGE_MS = 120_000L
        private const val STALE_LOCATION_LOG_INTERVAL_MS = 60_000L
        private const val AUTO_PARKING_ACTIVE_VALUE = 4.0
        private const val MAX_AUTOMATION_RULES = 50
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingConfirmations = ConcurrentHashMap<Int, PendingAction>()
    // Edge triggering: only fire when condition transitions from false→true.
    // triggersHash covers trigger and action definitions, so a saved edit reseeds instead of
    // comparing the new condition against state of the OLD one — which fired
    // rules right at save time (issue #51).
    private data class EvalState(
        val triggersHash: Int,
        val matched: Boolean,
        val candidateMatched: Boolean? = null,
        val candidateSamples: Int = 0,
        val candidateEligible: Boolean = true,
    )
    private data class StableTransition(
        val state: EvalState,
        val entered: Boolean = false,
        val exited: Boolean = false,
        val initialEnter: Boolean = false,
        val edgeEligible: Boolean = true,
    )
    private val lastEvalResults = ConcurrentHashMap<Long, EvalState>()
    // Once-per-trip gate: ruleId -> tripStartedAt captured when rule last fired
    private val lastFiredTripByRule = ConcurrentHashMap<Long, Long>()
    // Per-rule consumption marker for the network_available trigger. Tracks the
    // most recent NetworkAvailableMonitor.lastAvailableAt that this rule has
    // already responded to, so a single VALIDATED edge fires the rule at most
    // once even if the rule's other AND-conditions delay the actual fire.
    private val lastSeenNetworkAvailableAt = ConcurrentHashMap<Long, Long>()
    private val lastEnteredAtByRule = ConcurrentHashMap<Long, Long>()
    private val lastExitedAtByRule = ConcurrentHashMap<Long, Long>()
    private var lastStaleLocationLogAtMs = 0L
    private var lastStaleLocationTimeMs = Long.MIN_VALUE
    private val triggerEvaluator = AutomationTriggerEvaluator()
    private val boardVehicleDetector = BoardVehicleDetector()
    internal var nowProvider: () -> Long = System::currentTimeMillis
    // Service-start trigger: active during a short window after the first
    // evaluate() of the process, consumed per rule on fire. The previous
    // one-shot flag raced cold-start nulls: the very first tick often carries
    // incomplete data, so "SuperToyBox startup AND temp > 22" silently missed the
    // whole trip when ExtTemp was still null on tick one (issue #51).
    @Volatile private var serviceStartWindowEnd = 0L
    private val serviceStartConsumed = ConcurrentHashMap.newKeySet<Long>()

    private data class PendingAction(
        val rule: RuleEntity,
        val actions: List<ActionDef>,
        val snapshot: String,
        val createdAt: Long,
        val notifId: Int,
        val phase: String,
    )

    init {
        createConfirmChannel()
        registerConfirmReceiver()
    }

    // Called from the tracking service's fast action loop. tripStartedAt is passed
    // explicitly so the once-per-trip gate observes the same tick as the trigger data.
    suspend fun evaluate(data: DiParsData, tripStartedAt: Long?) {
        cleanupExpired()
        val boardVehicleEdge = boardVehicleDetector.onSample(data)
        evaluateRules(
            data = data,
            tripStartedAt = tripStartedAt,
            boardVehicleEdge = boardVehicleEdge,
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
        val config = automationConfigRepository.current()
        val placesById = config.placesById
        val enabledRules = config.enabledRules
        val rules = if (activationManager.canUse(PremiumFeature.AUTOMATION_UNLIMITED)) {
            enabledRules.take(MAX_AUTOMATION_RULES)
        } else {
            enabledRules.sortedByDescending { it.createdAt }
                .take(ActivationManager.FREE_AUTOMATION_RULE_LIMIT)
        }

        // Arm the service_start window on the very first evaluate() of the process.
        if (serviceStartWindowEnd == 0L) serviceStartWindowEnd = now + SERVICE_START_WINDOW_MS

        // Prune per-rule state for rules that have been deleted (or disabled
        // and removed from the active set). Without this, `lastEvalResults`,
        // `lastFiredTripByRule` and `lastSeenNetworkAvailableAt` would grow
        // monotonically over the app lifetime as the user creates and removes
        // rules. O(rules) per tick — negligible at typical N (≤ a few dozen).
        val activeIds = rules.mapTo(HashSet()) { it.id }
        actionDispatcher.stopMediaOwnedByInactiveRules(activeIds)
        lastEvalResults.keys.retainAll(activeIds)
        lastFiredTripByRule.keys.retainAll(activeIds)
        lastSeenNetworkAvailableAt.keys.retainAll(activeIds)
        lastEnteredAtByRule.keys.retainAll(activeIds)
        lastExitedAtByRule.keys.retainAll(activeIds)
        triggerEvaluator.retainRules(activeIds)
        serviceStartConsumed.retainAll(activeIds)

        var executedCount = 0
        for (rule in rules) {
            try {
                val triggers = TriggerDef.listFromJson(rule.triggers)
                if (triggers.isEmpty()) continue
                val triggersHash = (
                    rule.triggers + rule.triggerLogic + rule.actions + rule.exitActions
                ).hashCode()
                val previous = lastEvalResults[rule.id]
                if (previous != null && previous.triggersHash != triggersHash) {
                    actionDispatcher.stopMediaOwnedByRule(rule.id)
                }

                // network_available is an event trigger (like service_start) — fire when
                // VALIDATED internet edge happened that this rule has not consumed yet.
                // We CONSUME the edge eagerly (before cooldown / park-mode / matched
                // checks) so a stale edge can't fire later when the AND-condition or
                // cooldown finally permits. Edge semantics: network-validated is a
                // moment-in-time signal, not a persistent state.
                //
                // First observation of a rule (newly created, freshly enabled, or
                // first poll after a process restart) SEEDS the watermark with the
                // monitor's current value without firing. Without this guard the rule
                // would fire on any stale edge that happened before the rule was
                // active — including a probe-success captured at app startup before
                // the user toggled the rule on.
                val networkEdgeAt = networkAvailableMonitor.lastAvailableAt
                val seenAt = lastSeenNetworkAvailableAt[rule.id]
                val networkEdge: Boolean = when {
                    // Async-probe race guard. If we seed 0 here while a probe is
                    // about to publish a fresh edge, the next poll would treat
                    // that edge as "new" and fire — exactly the false-fire this
                    // logic is meant to prevent. Defer one tick.
                    seenAt == null && networkAvailableMonitor.probePending -> false
                    seenAt == null -> {
                        lastSeenNetworkAvailableAt[rule.id] = networkEdgeAt
                        false
                    }
                    else -> {
                        val isEdge = networkEdgeAt > 0L && networkEdgeAt > seenAt
                        if (isEdge) lastSeenNetworkAvailableAt[rule.id] = networkEdgeAt
                        isEdge
                    }
                }

                val serviceStartActive = now <= serviceStartWindowEnd && rule.id !in serviceStartConsumed
                val perTrigger = triggerEvaluator.evaluateEachTrigger(
                    ruleId = rule.id,
                    triggers = triggers,
                    data = data,
                    location = location,
                    places = placesById,
                    serviceStartActive = serviceStartActive,
                    networkAvailableEdge = networkEdge,
                    boardVehicleEdge = boardVehicleEdge,
                    now = now,
                    allowIntervalPulse = allowIntervalPulse,
                )
                val rawMatched = triggerEvaluator.combineByLogic(perTrigger, rule.triggerLogic)
                if (rawMatched == null) {
                    // Missing FIDs or a stale/unavailable location mean "unknown", not false.
                    // Preserve the last stable state so a telemetry gap cannot run exit actions
                    // and then re-run enter actions when data recovers.
                    continue
                }

                // Event-style triggers (service_start, network_available) bypass edge
                // detection ONLY when the matched=true was driven by the event itself.
                // Without this discriminator, a rule like `network_available OR speed > 50`
                // would fire every poll where speed > 50 (after cooldown), because the
                // bypass branch would see `hasEventTrigger=true` regardless of whether
                // the event actually contributed to the match.
                //
                // Edge bookkeeping happens BEFORE the cooldown / park gates so that a
                // front occurring while the rule is gated is consumed, not frozen and
                // fired minutes later when the gate finally opens. A front must happen while the rule is able
                // to act on it.
                val matchedViaEvent = triggerEvaluator.matchedViaEventTrigger(
                    triggers,
                    perTrigger,
                    rule.triggerLogic,
                )
                val transition = if (matchedViaEvent) {
                    val persistentMatched = triggerEvaluator.persistentMatch(
                        triggers,
                        perTrigger,
                        rule.triggerLogic,
                    )
                    StableTransition(
                        // Event-only pulses remain stateless. For OR rules, retain any
                        // simultaneously true non-event condition so it cannot produce a second
                        // enter immediately after the event has already executed the same rule.
                        state = when (persistentMatched) {
                            true -> EvalState(triggersHash, true)
                            false -> EvalState(triggersHash, false)
                            null -> previous?.takeIf { it.triggersHash == triggersHash }
                                ?: EvalState(triggersHash, false)
                        },
                        entered = rawMatched,
                    )
                } else {
                    stabilize(
                        previous = previous,
                        triggersHash = triggersHash,
                        rawMatched = rawMatched,
                        edgeEligibleNow = if (rawMatched) {
                            isEnterEdgeEligible(rule, data, tripStartedAt, now)
                        } else {
                            isExitEdgeEligible(rule, data, now)
                        },
                    )
                }
                lastEvalResults[rule.id] = transition.state

                if (transition.exited) {
                    stopPhaseMedia(
                        ruleId = rule.id,
                        actions = ActionDef.listFromJson(rule.actions),
                        ownerPhase = "enter",
                    )
                    val exitActions = ActionDef.listFromJson(rule.exitActions)
                    if (transition.edgeEligible && dispatchRulePhase(
                            rule = rule,
                            actions = exitActions,
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
                    now - effectiveLastEnterAt(rule) >= rule.repeatIntervalSeconds * 1000L
                if (transition.entered && previous != null) {
                    stopPhaseMedia(
                        ruleId = rule.id,
                        actions = ActionDef.listFromJson(rule.exitActions),
                        ownerPhase = "exit",
                    )
                }
                if ((transition.entered && transition.edgeEligible) || repeatDue) {
                    val phase = if (repeatDue) "repeat" else "enter"
                    val actions = ActionDef.listFromJson(rule.actions)
                    if (dispatchRulePhase(
                            rule = rule,
                            actions = actions,
                            triggers = triggers,
                            data = data,
                            tripStartedAt = tripStartedAt,
                            now = now,
                            phase = phase,
                        )) {
                        if (serviceStartActive && triggers.any { it.kind == "service_start" }) {
                            serviceStartConsumed.add(rule.id)
                        }
                        executedCount++
                    }
                }
            } catch (e: Exception) {
                WjxFileLogger.e(TAG, "Error evaluating rule '${rule.name}': ${e.message}", e)
            }
        }
        return executedCount
    }

    private fun stabilize(
        previous: EvalState?,
        triggersHash: Int,
        rawMatched: Boolean,
        edgeEligibleNow: Boolean,
    ): StableTransition {
        if (previous == null) {
            return StableTransition(
                state = EvalState(triggersHash, rawMatched),
                entered = rawMatched,
                initialEnter = rawMatched,
                edgeEligible = edgeEligibleNow,
            )
        }
        if (previous.triggersHash != triggersHash) {
            return StableTransition(state = EvalState(triggersHash, rawMatched))
        }
        if (rawMatched == previous.matched) {
            return StableTransition(
                state = previous.copy(candidateMatched = null, candidateSamples = 0, candidateEligible = true),
            )
        }
        val samples = if (previous.candidateMatched == rawMatched) previous.candidateSamples + 1 else 1
        val eligible = if (previous.candidateMatched == rawMatched) {
            previous.candidateEligible && edgeEligibleNow
        } else {
            edgeEligibleNow
        }
        if (samples < STATE_STABLE_SAMPLE_COUNT) {
            return StableTransition(
                state = previous.copy(
                    candidateMatched = rawMatched,
                    candidateSamples = samples,
                    candidateEligible = eligible,
                ),
            )
        }
        return StableTransition(
            state = EvalState(triggersHash, rawMatched),
            entered = rawMatched,
            exited = !rawMatched,
            edgeEligible = eligible,
        )
    }

    private fun isEnterEdgeEligible(rule: RuleEntity, data: DiParsData, tripStartedAt: Long?, now: Long): Boolean {
        if (rule.requirePark && data.gear != 1) return false
        if (now - effectiveLastEnterAt(rule) < rule.cooldownSeconds * 1000L) return false
        if (rule.fireOncePerTrip && tripStartedAt != null && lastFiredTripByRule[rule.id] == tripStartedAt) return false
        return true
    }

    private fun isExitEdgeEligible(rule: RuleEntity, data: DiParsData, now: Long): Boolean {
        if (rule.requirePark && data.gear != 1) return false
        return now - (lastExitedAtByRule[rule.id] ?: 0L) >= rule.cooldownSeconds * 1000L
    }

    private fun freshLocation(location: Location?, now: Long): Location? {
        location ?: return null
        val time = location.time
        if (time > 0L && now - time > LOCATION_MAX_AGE_MS) {
            val shouldLog = time != lastStaleLocationTimeMs ||
                lastStaleLocationLogAtMs == 0L ||
                now - lastStaleLocationLogAtMs >= STALE_LOCATION_LOG_INTERVAL_MS
            if (shouldLog) {
                lastStaleLocationTimeMs = time
                lastStaleLocationLogAtMs = now
                WjxFileLogger.i(TAG, "ignore stale automation location ageMs=${now - time}")
            }
            return null
        }
        lastStaleLocationTimeMs = Long.MIN_VALUE
        return location
    }

    private fun effectiveLastEnterAt(rule: RuleEntity): Long =
        maxOf(rule.lastTriggeredAt ?: 0L, lastEnteredAtByRule[rule.id] ?: 0L)

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
            WjxFileLogger.i(TAG, "rule phase blocked by park-only name='${rule.name}' phase=$phase gear=${data.gear}")
            return false
        }

        if (phase != "exit") {
            val lastFired = effectiveLastEnterAt(rule)
            val repeatMs = if (phase == "repeat") rule.repeatIntervalSeconds * 1000L else 0L
            val gateMs = maxOf(rule.cooldownSeconds * 1000L, repeatMs)
            if (now - lastFired < gateMs) {
                WjxFileLogger.i(TAG, "rule phase blocked by cooldown name='${rule.name}' phase=$phase remainingMs=${gateMs - (now - lastFired)}")
                return false
            }
            if (rule.fireOncePerTrip && tripStartedAt != null && lastFiredTripByRule[rule.id] == tripStartedAt) {
                WjxFileLogger.i(TAG, "rule phase blocked by once-per-trip name='${rule.name}' phase=$phase tripStartedAt=$tripStartedAt")
                return false
            }
            ruleDao.updateLastTriggered(rule.id, now)
            lastEnteredAtByRule[rule.id] = now
            if (rule.fireOncePerTrip && tripStartedAt != null) lastFiredTripByRule[rule.id] = tripStartedAt
        } else {
            val lastExit = lastExitedAtByRule[rule.id] ?: 0L
            val cooldownMs = rule.cooldownSeconds * 1000L
            if (now - lastExit < cooldownMs) {
                WjxFileLogger.i(TAG, "exit phase blocked by cooldown name='${rule.name}' remainingMs=${cooldownMs - (now - lastExit)}")
                return false
            }
            lastExitedAtByRule[rule.id] = now
        }

        val snapshot = JSONObject(triggerEvaluator.buildSnapshot(triggers, data))
            .apply { put("phase", phase) }
            .toString()
        WjxFileLogger.i(TAG, "rule phase ready name='${rule.name}' phase=$phase actions=${actions.size} confirm=${rule.confirmBeforeExecute} snapshot=$snapshot")

        if (rule.confirmBeforeExecute) {
            val shown = confirmationPresenter.show(
                ruleName = rule.name,
                actionsSummary = actions.joinToString(", ") { it.displayName },
                onConfirm = {
                    scope.launch {
                        executeConfirmed(rule, actions, snapshot, data, phase)
                    }
                },
                onCancel = {
                    scope.launch {
                        ruleLogDao.insert(
                            RuleLogEntity(
                                ruleId = rule.id,
                                ruleName = rule.name,
                                triggeredAt = now,
                                triggersSnapshot = snapshot,
                                actionsResult = """[{"result":"cancelled","phase":"$phase"}]""",
                                success = false,
                            )
                        )
                    }
                },
            )
            if (!shown) showConfirmNotification(rule, actions, snapshot, phase)
        } else {
            scope.launch { executeAndLog(rule, actions, snapshot, data, phase) }
        }
        return true
    }


    // --- Execution ---

    private suspend fun executeConfirmed(
        rule: RuleEntity,
        actions: List<ActionDef>,
        snapshot: String,
        data: DiParsData?,
        phase: String,
    ) {
        val currentRule = ruleDao.getById(rule.id)?.takeIf(RuleEntity::enabled)
        if (currentRule == null || !sameExecutionDefinition(rule, currentRule)) {
            ruleLogDao.insert(
                RuleLogEntity(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    triggeredAt = System.currentTimeMillis(),
                    triggersSnapshot = snapshot,
                    actionsResult = """[{"result":"stale_confirmation","phase":"$phase"}]""",
                    success = false,
                ),
            )
            WjxFileLogger.i(TAG, "ignore stale confirmation rule='${rule.name}' phase=$phase")
            return
        }
        executeAndLog(currentRule, actions, snapshot, data, phase)
    }

    private fun sameExecutionDefinition(before: RuleEntity, current: RuleEntity): Boolean =
        before.triggerLogic == current.triggerLogic &&
            before.triggers == current.triggers &&
            before.actions == current.actions &&
            before.exitActions == current.exitActions &&
            before.cooldownSeconds == current.cooldownSeconds &&
            before.repeatIntervalSeconds == current.repeatIntervalSeconds &&
            before.requirePark == current.requirePark &&
            before.confirmBeforeExecute == current.confirmBeforeExecute &&
            before.fireOncePerTrip == current.fireOncePerTrip

    private suspend fun executeAndLog(
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

        ruleLogDao.insert(
            RuleLogEntity(
                ruleId = rule.id,
                ruleName = rule.name,
                triggeredAt = System.currentTimeMillis(),
                triggersSnapshot = snapshot,
                actionsResult = results.toString(),
                success = allSuccess
            )
        )
        WjxFileLogger.i(TAG, "Rule '${rule.name}' executed: success=$allSuccess results=$results")
    }

    private fun stopPhaseMedia(
        ruleId: Long,
        actions: List<ActionDef>,
        ownerPhase: String,
    ) {
        actions.forEachIndexed { index, action ->
            val shouldStop = (action.kind == "outside_sound" && action.outsideSoundLoop()) ||
                action.kind == "play_video" ||
                (action.kind == "play_music" && action.automationMediaLoop())
            if (!shouldStop) return@forEachIndexed
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

    private fun automationActionOwner(
        ruleId: Long,
        phase: String,
        index: Int,
        action: ActionDef,
    ): String? =
        if (action.kind in setOf("outside_sound", "play_music", "play_video")) {
            val ownerPhase = if (phase == "exit") "exit" else "enter"
            "automation:$ruleId:$ownerPhase:$index"
        } else {
            null
        }

    private fun ActionDef.outsideSoundLoop(): Boolean = try {
        JSONObject(payload ?: "{}").optBoolean("loop", false)
    } catch (_: Exception) {
        false
    }

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

    // --- Confirmation notifications ---

    private fun showConfirmNotification(
        rule: RuleEntity,
        actions: List<ActionDef>,
        snapshot: String,
        phase: String,
    ) {
        val notifId = NOTIF_BASE_ID + rule.id.toInt()

        pendingConfirmations[notifId] = PendingAction(
            rule = rule, actions = actions, snapshot = snapshot,
            createdAt = System.currentTimeMillis(), notifId = notifId, phase = phase,
        )

        val summary = actions.joinToString(", ") { it.displayName }

        val confirmPI = PendingIntent.getBroadcast(
            context, notifId,
            Intent(ACTION_CONFIRM).putExtra(EXTRA_NOTIF_ID, notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelPI = PendingIntent.getBroadcast(
            context, notifId + 10000,
            Intent(ACTION_CANCEL).putExtra(EXTRA_NOTIF_ID, notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CONFIRM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(rule.name)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .addAction(android.R.drawable.ic_menu_send, context.getString(R.string.service_confirm_action_yes), confirmPI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.service_confirm_action_no), cancelPI)
            .setAutoCancel(true)
            .setTimeoutAfter(CONFIRM_TIMEOUT_MS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
        WjxFileLogger.i(TAG, "Confirm requested: '${rule.name}' -> $summary")
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = pendingConfirmations.entries
            .filter { now - it.value.createdAt > CONFIRM_TIMEOUT_MS }

        for ((notifId, pending) in expired) {
            pendingConfirmations.remove(notifId)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
            scope.launch {
                ruleLogDao.insert(
                    RuleLogEntity(
                        ruleId = pending.rule.id,
                        ruleName = pending.rule.name,
                        triggeredAt = pending.createdAt,
                        triggersSnapshot = pending.snapshot,
                        actionsResult = """[{"result":"timeout"}]""",
                        success = false
                    )
                )
            }
            WjxFileLogger.i(TAG, "Confirm timeout: '${pending.rule.name}'")
        }
    }

    private fun registerConfirmReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                val pending = pendingConfirmations.remove(notifId) ?: return

                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notifId)

                when (intent.action) {
                    ACTION_CONFIRM -> scope.launch {
                        val currentData = vehicleStateRepository.actionSnapshot.value?.data
                            ?: vehicleStateRepository.fullSnapshot.value?.data
                        executeConfirmed(
                            pending.rule,
                            pending.actions,
                            pending.snapshot,
                            currentData,
                            pending.phase,
                        )
                    }
                    ACTION_CANCEL -> {
                        scope.launch {
                            ruleLogDao.insert(
                                RuleLogEntity(
                                    ruleId = pending.rule.id,
                                    ruleName = pending.rule.name,
                                    triggeredAt = pending.createdAt,
                                    triggersSnapshot = pending.snapshot,
                                    actionsResult = """[{"result":"cancelled"}]""",
                                    success = false
                                )
                            )
                        }
                        WjxFileLogger.i(TAG, "Cancelled by user: '${pending.rule.name}'")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_CONFIRM)
            addAction(ACTION_CANCEL)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun createConfirmChannel() {
        val channel = NotificationChannel(
            CONFIRM_CHANNEL_ID,
            "Automation Confirmations",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Confirmation dialogs for automation rules"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
