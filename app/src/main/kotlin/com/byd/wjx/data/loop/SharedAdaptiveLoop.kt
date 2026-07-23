package com.byd.wjx.data.loop

import com.byd.wjx.data.local.EnergyDataReader
import com.byd.wjx.data.local.dao.LastStateDao
import com.byd.wjx.data.local.entity.LastStateEntity
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polling owner of NativeParsReader. Live consumers subscribe to [flow].
 * [actionFlow] is the fast action trigger stream; [flow] is the slower full
 * business stream for dashboard/trips/energy. Backoff applies when fetch()
 * returns null.
 */
@Singleton
class SharedAdaptiveLoop constructor(
    private val parsReader: ParsReader,
    private val lastStateDao: LastStateDao,
    private val energyDataReader: EnergyDataReader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cadence: CadenceConfig = CadenceConfig.default(),
    private val recoverVehicleReads: suspend () -> Boolean = { false },
) {
    private val _flow = MutableSharedFlow<DiParsData>(
        replay = 1, extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val flow: SharedFlow<DiParsData> = _flow.asSharedFlow()

    private val _actionFlow = MutableSharedFlow<DiParsData>(
        replay = 1, extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actionFlow: SharedFlow<DiParsData> = _actionFlow.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _snapshot = MutableStateFlow<VehicleSnapshot?>(null)
    val snapshot: StateFlow<VehicleSnapshot?> = _snapshot.asStateFlow()

    private val _actionSnapshot = MutableStateFlow<VehicleSnapshot?>(null)
    val actionSnapshot: StateFlow<VehicleSnapshot?> = _actionSnapshot.asStateFlow()

    private var job: Job? = null
    private val loopOwnership = Mutex()
    private val persistenceGate = SnapshotPersistenceGate()

    @Synchronized
    fun start(scope: CoroutineScope): Job {
        job?.takeIf { it.isActive }?.let { return it }
        job = scope.launch(dispatcher) {
            try {
                // A cancelled Binder transaction can take a short time to unwind. Serializing
                // ownership here prevents a restarted service from briefly running two pollers.
                loopOwnership.withLock { runLoop() }
            } finally {
                _connected.value = false
            }
        }.also { runningJob ->
            runningJob.invokeOnCompletion { error ->
                if (error != null && error !is CancellationException) {
                    WjxFileLogger.e(TAG, "vehicle polling stopped unexpectedly: ${error.message}", error)
                }
            }
        }
        return job!!
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _connected.value = false
    }

    private suspend fun runLoop() {
        var consecutiveNull = 0
        var regularDelayRemainingMs = 0L
        var lastCadenceState: LoopState? = null
        var actionRequestedFieldCount = 0
        var lastLoggedActionBudget: Pair<Int, Long>? = null
        while (true) {
            val regularDue = regularDelayRemainingMs <= 0L
            val fetchStartedAt = System.currentTimeMillis()
            val snapshot = runCatching {
                if (regularDue) {
                    parsReader.fetchVehicleSnapshot()
                } else {
                    parsReader.fetchActionVehicleSnapshot()
                }
            }.getOrNull()
            val fetchElapsedMs = System.currentTimeMillis() - fetchStartedAt
            if (fetchElapsedMs >= SLOW_FETCH_LOG_MS) {
                WjxFileLogger.w(
                    TAG,
                    "vehicle data ${if (regularDue) "full" else "action"} fetch slow ${fetchElapsedMs}ms"
                )
            }
            if (snapshot == null) {
                consecutiveNull++
                _connected.value = false
                regularDelayRemainingMs = 0L
                if (shouldAttemptRecovery(consecutiveNull)) {
                    val recovered = runCatching { recoverVehicleReads() }
                        .onFailure {
                            WjxFileLogger.w(TAG, "vehicle read recovery failed: ${it.message}", it)
                        }
                        .getOrDefault(false)
                    WjxFileLogger.i(
                        TAG,
                        "vehicle read recovery attempt consecutive=$consecutiveNull recovered=$recovered",
                    )
                    if (recovered) continue
                }
                val backoff = (NULL_RETRY_BASE_MS * pow15(consecutiveNull - 1))
                    .coerceAtMost(CadenceConfig.MAX_POLL_INTERVAL_MS)
                WjxFileLogger.w(TAG, "vehicle data unavailable consecutive=$consecutiveNull retryIn=${backoff}ms")
                delay(backoff)
                continue
            }
            val data = snapshot.data
            if (consecutiveNull > 0) {
                WjxFileLogger.i(TAG, "vehicle data recovered afterNull=$consecutiveNull")
            }
            consecutiveNull = 0
            _connected.value = true
            _actionSnapshot.value = snapshot
            _actionFlow.emit(data)
            if (regularDue) {
                _snapshot.value = snapshot
                runCatching { persistSnapshot(data) }
                    .onFailure {
                        WjxFileLogger.w(TAG, "snapshot persistence failed; live polling continues: ${it.message}", it)
                    }
                _flow.emit(data)
                val cadenceState = LoopFsm.classify(data)
                lastCadenceState = cadenceState
                regularDelayRemainingMs = (
                    cadence.intervalFor(cadenceState) - fetchElapsedMs
                ).coerceAtLeast(0L)
            } else {
                actionRequestedFieldCount = snapshot.requestedFieldCount
                regularDelayRemainingMs = (
                    regularDelayRemainingMs - fetchElapsedMs
                ).coerceAtLeast(0L)
                val observedState = LoopFsm.classify(data)
                if (lastCadenceState != null && observedState != lastCadenceState) {
                    WjxFileLogger.i(
                        TAG,
                        "vehicle state transition $lastCadenceState -> $observedState; forcing full snapshot",
                    )
                    regularDelayRemainingMs = 0L
                    continue
                }
            }
            val baseActionIntervalMs = cadence
                .actionIntervalFor(lastCadenceState ?: LoopState.DRIVE)
                .coerceAtLeast(MIN_ACTION_INTERVAL_MS)
            val actionIntervalMs = ActionPollingBudget.effectiveIntervalMs(
                baseIntervalMs = baseActionIntervalMs,
                requestedFieldCount = actionRequestedFieldCount,
            )
            val currentBudget = actionRequestedFieldCount to actionIntervalMs
            if (currentBudget != lastLoggedActionBudget) {
                if (actionIntervalMs > baseActionIntervalMs) {
                    WjxFileLogger.i(
                        TAG,
                        "action polling budget fields=$actionRequestedFieldCount " +
                            "base=${baseActionIntervalMs}ms effective=${actionIntervalMs}ms",
                    )
                }
                lastLoggedActionBudget = currentBudget
            }
            val actionDelayMs = ActionPollingBudget.delayAfterFetchMs(
                baseIntervalMs = baseActionIntervalMs,
                requestedFieldCount = actionRequestedFieldCount,
                fetchElapsedMs = fetchElapsedMs,
            )
            val delayMs = minOf(actionDelayMs, regularDelayRemainingMs)
            if (delayMs > 0L) delay(delayMs)
            regularDelayRemainingMs = (regularDelayRemainingMs - delayMs).coerceAtLeast(0L)
        }
    }

    private suspend fun persistSnapshot(data: DiParsData) {
        val now = System.currentTimeMillis()
        val energyDataAvailable = energyDataReader.isAvailable()
        val sample = SnapshotPersistenceSample(
            soc = data.soc,
            mileageKm = data.mileage,
            totalEnergyKwh = data.totalElecConsumption,
            ignition = data.powerState,
            energyDataAvailable = energyDataAvailable,
        )
        if (!persistenceGate.shouldPersist(sample, now)) return

        val energydataAvailable = if (energyDataAvailable) 1 else 0
        val updated = lastStateDao.updateSnapshot(
            ts = now,
            soc = data.soc,
            mileage = data.mileage,
            totalElec = data.totalElecConsumption,
            ignition = data.powerState,
            energydataAvailable = energydataAvailable,
        )
        if (updated == 0) {
            lastStateDao.upsert(
                LastStateEntity(
                    id = 1,
                    ts = now,
                    soc = data.soc,
                    mileage = data.mileage,
                    totalElec = data.totalElecConsumption,
                    ignition = data.powerState,
                    energydataAvailable = energydataAvailable,
                )
            )
        }
        persistenceGate.markPersisted(sample, now)
    }

    private fun pow15(n: Int): Long {
        var v = 1.0
        repeat(n) { v *= 1.5 }
        return v.toLong().coerceAtLeast(1L)
    }

    private fun shouldAttemptRecovery(consecutiveNull: Int): Boolean =
        consecutiveNull >= RECOVERY_AFTER_FAILURES &&
            (consecutiveNull - RECOVERY_AFTER_FAILURES) % RECOVERY_EVERY_FAILURES == 0

    private companion object {
        private const val TAG = "SharedAdaptiveLoop"
        const val MIN_ACTION_INTERVAL_MS = 300L
        const val NULL_RETRY_BASE_MS = 1_000L
        const val SLOW_FETCH_LOG_MS = 1_000L
        const val RECOVERY_AFTER_FAILURES = 3
        const val RECOVERY_EVERY_FAILURES = 3
    }
}

internal object ActionPollingBudget {
    const val MAX_ACTION_FIELD_READS_PER_SECOND = 120L
    const val MIN_IDLE_AFTER_FETCH_MS = 40L

    fun effectiveIntervalMs(baseIntervalMs: Long, requestedFieldCount: Int): Long {
        if (requestedFieldCount <= 0) return baseIntervalMs
        val readsPerCycle = requestedFieldCount.toLong()
        val budgetIntervalMs = (
            readsPerCycle * 1_000L + MAX_ACTION_FIELD_READS_PER_SECOND - 1L
        ) / MAX_ACTION_FIELD_READS_PER_SECOND
        return maxOf(baseIntervalMs, budgetIntervalMs)
    }

    fun delayAfterFetchMs(
        baseIntervalMs: Long,
        requestedFieldCount: Int,
        fetchElapsedMs: Long,
    ): Long = maxOf(
        effectiveIntervalMs(baseIntervalMs, requestedFieldCount) - fetchElapsedMs,
        MIN_IDLE_AFTER_FETCH_MS,
    )
}
