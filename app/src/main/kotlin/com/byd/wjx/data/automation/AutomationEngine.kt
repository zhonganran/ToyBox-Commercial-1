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
import java.util.Calendar
import kotlin.math.abs
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.dao.RuleLogDao
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.nativestack.BydSignalSemantics
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
        private const val MAX_AUTOMATION_RULES = 50
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingConfirmations = ConcurrentHashMap<Int, PendingAction>()
    // Edge triggering: only fire when condition transitions from false→true.
    // triggersHash detects rule edits, so a saved edit reseeds instead of
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
    private val lastIntervalPulseAt = ConcurrentHashMap<String, Long>()
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
        val notifId: Int
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
            buttonId = null,
            boardVehicleEdge = boardVehicleEdge,
            allowIntervalPulse = true,
        )
    }

    suspend fun onButtonPress(buttonId: Int): Int {
        val data = vehicleStateRepository.fullSnapshot.value?.data ?: return 0
        return evaluateRules(
            data = data,
            tripStartedAt = trackingStateRepository.sessionStartedAt.value,
            buttonId = buttonId,
            boardVehicleEdge = false,
            allowIntervalPulse = false,
        )
    }

    private suspend fun evaluateRules(
        data: DiParsData,
        tripStartedAt: Long?,
        buttonId: Int?,
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
        lastIntervalPulseAt.keys.removeAll { key -> key.substringBefore(':').toLongOrNull() !in activeIds }
        serviceStartConsumed.retainAll(activeIds)

        var executedCount = 0
        for (rule in rules) {
            try {
                val triggers = TriggerDef.listFromJson(rule.triggers)
                if (triggers.isEmpty()) continue
                if (buttonId != null && triggers.none { it.kind == "button_press" && it.value.toIntOrNull() == buttonId }) {
                    continue
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
                    buttonId != null -> false
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
                val perTrigger = evaluateEachTrigger(
                    ruleId = rule.id,
                    triggers = triggers,
                    data = data,
                    location = location,
                    places = placesById,
                    serviceStartActive = serviceStartActive,
                    networkAvailableEdge = networkEdge,
                    boardVehicleEdge = boardVehicleEdge,
                    buttonId = buttonId,
                    now = now,
                    allowIntervalPulse = allowIntervalPulse,
                )
                val rawMatched = combineByLogic(perTrigger, rule.triggerLogic)

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
                val matchedViaEvent = matchedViaEventTrigger(triggers, perTrigger, rule.triggerLogic)
                val triggersHash = (rule.triggers + rule.triggerLogic).hashCode()
                val previous = lastEvalResults[rule.id]
                val transition = if (matchedViaEvent) {
                    StableTransition(
                        // Event pulses execute an enter phase but must not become the persisted
                        // state of the rule; otherwise the next ordinary tick would look like an
                        // exit and incorrectly run the otherwise branch.
                        state = previous?.takeIf { it.triggersHash == triggersHash }
                            ?: EvalState(triggersHash, false),
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
                    stopLoopingOutsideSounds(rule)
                    stopTransientMedia(rule)
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
            WjxFileLogger.i(TAG, "ignore stale automation location ageMs=${now - time}")
            return null
        }
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

        val snapshot = JSONObject(buildSnapshot(triggers, data)).apply { put("phase", phase) }.toString()
        WjxFileLogger.i(TAG, "rule phase ready name='${rule.name}' phase=$phase actions=${actions.size} confirm=${rule.confirmBeforeExecute} snapshot=$snapshot")

        if (rule.confirmBeforeExecute) {
            val shown = ConfirmOverlayManager.show(
                context = context,
                ruleName = rule.name,
                actionsSummary = actions.joinToString(", ") { it.displayName },
                onConfirm = {
                    scope.launch {
                        executeAndLog(rule, actions, snapshot, vehicleStateRepository.fullSnapshot.value?.data)
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
            if (!shown) showConfirmNotification(rule, actions, snapshot)
        } else {
            scope.launch { executeAndLog(rule, actions, snapshot, data) }
        }
        return true
    }

    // --- Trigger evaluation ---

    private fun evaluateEachTrigger(
        ruleId: Long,
        triggers: List<TriggerDef>,
        data: DiParsData,
        location: Location?,
        places: Map<Long, PlaceEntity>,
        serviceStartActive: Boolean,
        networkAvailableEdge: Boolean,
        boardVehicleEdge: Boolean,
        buttonId: Int?,
        now: Long,
        allowIntervalPulse: Boolean,
    ): List<Boolean> = triggers.mapIndexed { index, trigger ->
        when (trigger.kind) {
            "place_enter" -> evaluatePlace(trigger, location, places, enterKind = true)
            "place_exit" -> evaluatePlace(trigger, location, places, enterKind = false)
            "time_of_day" -> evaluateTimeOfDay(trigger, location)
            "time_range" -> evaluateSchedule(trigger)
            "interval" -> evaluateInterval(ruleId, index, trigger, now, allowIntervalPulse)
            "service_start" -> serviceStartActive
            "network_available" -> networkAvailableEdge
            "board_vehicle" -> boardVehicleEdge
            "button_press" -> buttonId != null && trigger.value.toIntOrNull() == buttonId
            else -> { // "param" (default)
                val actual = getParamValue(data, trigger.param) ?: return@mapIndexed false
                val expected = trigger.value.toDoubleOrNull() ?: return@mapIndexed false
                compare(actual, trigger.operator, expected)
            }
        }
    }

    private fun evaluateInterval(
        ruleId: Long,
        triggerIndex: Int,
        trigger: TriggerDef,
        now: Long,
        allowPulse: Boolean,
    ): Boolean {
        if (!allowPulse) return false
        val seconds = trigger.value.toIntOrNull()?.takeIf { it in 15..300 } ?: return false
        val key = "$ruleId:$triggerIndex"
        val last = lastIntervalPulseAt.putIfAbsent(key, now) ?: return false
        if (now - last < seconds * 1000L) return false
        lastIntervalPulseAt[key] = now
        return true
    }

    private fun combineByLogic(results: List<Boolean>, logic: String): Boolean = when (logic) {
        "OR" -> results.any { it }
        else -> results.all { it }
    }

    /**
     * Determines whether the rule's matched=true was actually driven by an event
     * trigger (service_start / network_available), so the bypass-edge-detection
     * branch only fires for genuine events. Without this gate, a rule like
     * `network_available OR speed > 50` would re-fire on every poll where speed
     * exceeds 50 (after cooldown), because the bypass logic would see a present
     * event-trigger regardless of whether the event itself contributed.
     */
    private fun matchedViaEventTrigger(
        triggers: List<TriggerDef>,
        perTrigger: List<Boolean>,
        logic: String
    ): Boolean {
        val eventKinds = setOf("service_start", "network_available", "interval", "board_vehicle", "button_press")
        return when (logic) {
            // OR: at least one event-trigger must be true on its own.
            "OR" -> triggers.zip(perTrigger).any { (t, r) -> r && t.kind in eventKinds }
            // AND: every trigger must be true; if at least one of them is an
            // event, the whole composition fires only when that event was true,
            // so the rule is event-driven.
            else -> triggers.any { it.kind in eventKinds } && perTrigger.all { it }
        }
    }

    private fun evaluatePlace(
        trigger: TriggerDef,
        location: Location?,
        places: Map<Long, PlaceEntity>,
        enterKind: Boolean
    ): Boolean {
        if (location == null) return false
        val placeId = trigger.placeId ?: return false
        val place = places[placeId] ?: return false
        val inside = PlaceGeometry.isInside(
            location.latitude, location.longitude,
            place.lat, place.lon, place.radiusM
        )
        return if (enterKind) inside else !inside
    }

    private fun evaluateTimeOfDay(trigger: TriggerDef, location: Location?): Boolean {
        val loc = location ?: return false
        val phase = SunTimeCalculator.currentPhase(loc)
        val target = trigger.value.uppercase()
        return phase.name == target
    }

    private fun evaluateSchedule(trigger: TriggerDef): Boolean {
        val spec = ScheduleSpec.fromJson(trigger.value) ?: return false
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // Calendar: SUNDAY=1..SATURDAY=7 → ISO: MONDAY=1..SUNDAY=7
        val nowDow = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
            else cal.get(Calendar.DAY_OF_WEEK) - 1
        return isWithinSchedule(spec, nowMinute, nowDow)
    }

    private fun compare(actual: Double, op: String, expected: Double): Boolean = when (op) {
        ">" -> actual > expected
        "<" -> actual < expected
        ">=" -> actual >= expected
        "<=" -> actual <= expected
        "==" -> abs(actual - expected) < 0.01
        "!=" -> abs(actual - expected) >= 0.01
        else -> false
    }

    private fun getParamValue(data: DiParsData, param: String): Double? = when (param) {
        "Speed" -> data.speed?.toDouble()
        "SOC" -> data.soc?.toDouble()
        "ExtTemp" -> data.exteriorTemp?.toDouble()
        "InsideTemp" -> data.insideTemp?.toDouble()
        "ChargingStatus" -> data.chargingStatus?.toDouble()
        "ChargeGunState" -> data.chargeGunState?.toDouble()
        "PowerState" -> data.powerState?.toDouble()
        "Gear" -> data.gear?.toDouble()
        "ACStatus" -> data.acStatus?.toDouble()
        "ACTemp" -> data.acTemp?.toDouble()
        "FanLevel" -> data.fanLevel?.toDouble()
        "ACCirc" -> data.acCirc?.toDouble()
        "ACCtrlMode" -> data.acCtrlMode?.toDouble()
        "ACDefrostFront" -> data.acDefrostFront?.toDouble()
        "ACWindMode" -> data.acWindMode?.toDouble()
        "SeatHeatD" -> data.seatHeatD?.toDouble()
        "SeatVentD" -> data.seatVentD?.toDouble()
        "SeatHeatP" -> data.seatHeatP?.toDouble()
        "SeatVentP" -> data.seatVentP?.toDouble()
        "ACDefrostRear" -> data.acDefrostRear?.toDouble()
        "SteeringHeat" -> data.steeringHeat?.toDouble()
        "SunshadePos" -> data.sunshadePos?.toDouble()
        "MirrorFoldState" -> data.mirrorFoldState?.toDouble()
        "DoorFL" -> data.doorFL?.toDouble()
        "DoorFR" -> data.doorFR?.toDouble()
        "DoorRL" -> data.doorRL?.toDouble()
        "DoorRR" -> data.doorRR?.toDouble()
        "WindowFL" -> data.windowFL?.toDouble()
        "WindowFR" -> data.windowFR?.toDouble()
        "WindowRL" -> data.windowRL?.toDouble()
        "WindowRR" -> data.windowRR?.toDouble()
        "Sunroof" -> data.sunroof?.toDouble()
        "Trunk" -> data.trunk?.toDouble()
        "Hood" -> data.hood?.toDouble()
        "SeatbeltFL" -> data.seatbeltFL?.toDouble()
        "LockFL" -> data.lockFL?.toDouble()
        "TirePressFL" -> data.tirePressFL?.toDouble()
        "TirePressFR" -> data.tirePressFR?.toDouble()
        "TirePressRL" -> data.tirePressRL?.toDouble()
        "TirePressRR" -> data.tirePressRR?.toDouble()
        "TireTempFL" -> data.tireTempFL?.toDouble()
        "TireTempFR" -> data.tireTempFR?.toDouble()
        "TireTempRL" -> data.tireTempRL?.toDouble()
        "TireTempRR" -> data.tireTempRR?.toDouble()
        "TireStateFL" -> data.tireStateFL?.toDouble()
        "TireStateFR" -> data.tireStateFR?.toDouble()
        "TireStateRL" -> data.tireStateRL?.toDouble()
        "TireStateRR" -> data.tireStateRR?.toDouble()
        "DriveMode" -> data.driveMode?.toDouble()
        "WorkMode" -> data.workMode?.toDouble()
        "AutoPark" -> (data.autoPark ?: data.adasApaParkingStatus)?.toDouble()
        "AdasApaParkingStatus" -> data.adasApaParkingStatus?.toDouble()
        "ChargingCap" -> data.chargingCapCover?.toDouble()
        "FuelCap" -> data.fuelCap?.toDouble()
        "Rain" -> data.rain?.toDouble()
        "LightLow" -> data.lightLow?.toDouble()
        "LightSide" -> data.lightSide?.toDouble()
        "LightFrontFog" -> data.lightFrontFog?.toDouble()
        "LightRearFog" -> data.lightRearFog?.toDouble()
        "LightBrake" -> data.lightBrake?.toDouble()
        "LightReversing" -> data.lightReversing?.toDouble()
        "LightHigh" -> data.lightHigh?.toDouble()
        "DRL" -> data.drl?.toDouble()
        "TurnSignalFlash" -> data.turnSignalFlash?.toDouble()
        "AdasAccMode" -> data.adasAccMode?.toDouble()
        "AdasLccNcaStatus" -> data.adasLccNcaStatus?.toDouble()
        "AdasTjaState" -> data.adasTjaState?.toDouble()
        "LaneKeepState" -> data.laneKeepState?.toDouble()
        "AccCruiseState" -> data.accCruiseState?.toDouble()
        "AccCruisingSpeed" -> data.accCruisingSpeed?.toDouble()
        "AdasAebState" -> data.adasAebState?.toDouble()
        "AdasAvhState" -> data.adasAvhState?.toDouble()
        "AdasBsdState" -> data.adasBsdState?.toDouble()
        "AdasEspState" -> data.adasEspState?.toDouble()
        "AdasAccGrayState" -> data.adasAccGrayState?.toDouble()
        "AdasNoaGrayState" -> data.adasNoaGrayState?.toDouble()
        "AdasMnoaStatus" -> data.adasMnoaStatus?.toDouble()
        "AdasExecSpeed" -> data.adasExecSpeed?.toDouble()
        "BrakePedal" -> data.brakePedalDepth?.toDouble()
        "AccelPedal" -> data.accelPedalDepth?.toDouble()
        "EpbState" -> data.epbState?.toDouble()
        "BrakeState" -> data.brakeState?.toDouble()
        "EpedalMode" -> data.ePedalMode?.toDouble()
        "TurnSignalLeft" -> data.turnSignalLeft?.toDouble()
        "TurnSignalRight" -> data.turnSignalRight?.toDouble()
        "MaxBatTemp" -> data.maxBatTemp?.toDouble()
        "AvgBatTemp" -> data.avgBatTemp?.toDouble()
        "MinBatTemp" -> data.minBatTemp?.toDouble()
        "Power" -> data.power
        "Mileage" -> data.mileage
        "Voltage12V" -> data.voltage12v
        "MinCellVoltage" -> data.minCellVoltage
        "MaxCellVoltage" -> data.maxCellVoltage
        "FrontMotorSpeed" -> data.frontMotorSpeed?.toDouble()
        "RearMotorSpeed" -> data.rearMotorSpeed?.toDouble()
        "FrontMotorTemp" -> data.frontMotorTemp?.toDouble()
        "RearMotorTemp" -> data.rearMotorTemp?.toDouble()
        "FrontMotorCurrent" -> data.frontMotorCurrent
        "RearMotorCurrent" -> data.rearMotorCurrent
        "EngineRPM" -> data.engineRpm?.toDouble()
        "WaterTemp" -> data.waterTemp?.toDouble()
        else -> null
    }

    // --- Execution ---

    private suspend fun executeAndLog(
        rule: RuleEntity,
        actions: List<ActionDef>,
        snapshot: String,
        data: DiParsData?
    ) {
        val results = JSONArray()
        var allSuccess = true

        for ((index, action) in actions.withIndex()) {
            val result = actionDispatcher.dispatch(
                action = action,
                data = data,
                owner = automationActionOwner(rule.id, index, action),
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

    private fun stopLoopingOutsideSounds(rule: RuleEntity) {
        val actions = ActionDef.listFromJson(rule.actions)
        actions.forEachIndexed { index, action ->
            if (action.kind == "outside_sound" && action.outsideSoundLoop()) {
                val owner = automationActionOwner(rule.id, index, action) ?: return@forEachIndexed
                actionDispatcher.stopOutsideSound(owner)
                WjxFileLogger.i(TAG, "stop looping outside sound: rule='${rule.name}' owner=$owner")
                Log.i(TAG, "Stopped looping outside sound for rule='${rule.name}' owner=$owner")
            }
        }
    }

    private fun stopTransientMedia(rule: RuleEntity) {
        val actions = ActionDef.listFromJson(rule.actions)
        actions.forEachIndexed { index, action ->
            val shouldStop = action.kind == "play_video" ||
                (action.kind == "play_music" && action.automationMediaLoop())
            if (!shouldStop) return@forEachIndexed
            val owner = automationActionOwner(rule.id, index, action) ?: return@forEachIndexed
            actionDispatcher.stopTransientMedia(action.kind, owner)
            WjxFileLogger.i(TAG, "stop transient media: rule='${rule.name}' kind=${action.kind} owner=$owner")
        }
    }

    private fun automationActionOwner(ruleId: Long, index: Int, action: ActionDef): String? =
        if (action.kind in setOf("outside_sound", "play_music", "play_video")) {
            "automation:$ruleId:$index"
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

    private fun buildSnapshot(triggers: List<TriggerDef>, data: DiParsData): String {
        val json = JSONObject()
        triggers.forEach { t ->
            when (t.kind) {
                "place_enter" -> json.put("place_enter", t.placeName ?: "?")
                "place_exit" -> json.put("place_exit", t.placeName ?: "?")
                "time_of_day" -> json.put("time_of_day", t.value)
                "time_range" -> json.put("time_range", t.value)
                "interval" -> json.put("interval_sec", t.value)
                "service_start" -> json.put("service_start", true)
                "network_available" -> json.put("network_available", true)
                "board_vehicle" -> json.put("board_vehicle", true)
                "button_press" -> json.put("button_press", t.value)
                else -> json.put(t.param, getParamValue(data, t.param) ?: JSONObject.NULL)
            }
        }
        return json.toString()
    }

    // --- Confirmation notifications ---

    private fun showConfirmNotification(
        rule: RuleEntity,
        actions: List<ActionDef>,
        snapshot: String
    ) {
        val notifId = NOTIF_BASE_ID + rule.id.toInt()

        pendingConfirmations[notifId] = PendingAction(
            rule = rule, actions = actions, snapshot = snapshot,
            createdAt = System.currentTimeMillis(), notifId = notifId
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
                        val currentData = vehicleStateRepository.fullSnapshot.value?.data
                        executeAndLog(pending.rule, pending.actions, pending.snapshot, currentData)
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

/**
 * Detects a real boarding door-open edge without confusing the normal door-open used to exit
 * after a drive. Once driving or a fastened driver belt has been observed, the detector only
 * rearms after that exit door has opened and closed again while the vehicle is not driving.
 */
internal class BoardVehicleDetector {
    private var initialized = false
    private var previousDoorOpen = false
    private var armed = false
    private var droveSinceLastBoard = false
    private var exitDoorOpened = false

    fun onSample(data: DiParsData): Boolean {
        val doorOpen = data.doorFL == 1
        val driving = BydSignalSemantics.isDriveReady(data.powerState) ||
            data.gear == 4 || (data.speed ?: 0) > 0 || data.seatbeltFL == 1

        if (!initialized) {
            initialized = true
            previousDoorOpen = doorOpen
            armed = !driving && !doorOpen
            return !driving && doorOpen
        }

        if (driving) {
            droveSinceLastBoard = true
            armed = false
            exitDoorOpened = false
        } else if (droveSinceLastBoard) {
            if (doorOpen) exitDoorOpened = true
            if (exitDoorOpened && previousDoorOpen && !doorOpen) {
                droveSinceLastBoard = false
                exitDoorOpened = false
                armed = true
            }
        }

        val boarded = armed && !previousDoorOpen && doorOpen
        if (boarded) armed = false
        previousDoorOpen = doorOpen
        return boarded
    }
}
