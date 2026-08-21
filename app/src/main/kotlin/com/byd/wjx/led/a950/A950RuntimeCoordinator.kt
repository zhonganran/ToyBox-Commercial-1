package com.byd.wjx.led.a950

import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.led.LedVehicleSignals
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class A950RuntimeCoordinator @Inject constructor(
    private val preferences: A950Preferences,
    private val bleClient: A950BleClient,
    private val parsReader: ParsReader,
) {
    private val ruleEngine = A950LinkageEngine()
    private val decisionMutex = Mutex()
    private val _runtime = MutableStateFlow(A950RuntimeState())
    val runtime: StateFlow<A950RuntimeState> = _runtime.asStateFlow()

    @Volatile private var runtimeActive = false
    @Volatile private var connectionInitialized = false
    @Volatile private var latestVehicleSignals = LedVehicleSignals()
    @Volatile private var latestMotionSample = TimedSignals()
    @Volatile private var latestBrakeSample = TimedSignals()
    @Volatile private var latestBodySample = TimedSignals()
    private var lastSignalLogKey: SignalLogKey? = null
    private var pendingPreset: A950Preset? = null
    private var programListResumePending = false
    private var runtimeJob: Job? = null

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (runtimeJob?.isActive == true) return
        runtimeActive = true
        runtimeJob = scope.launch {
            coroutineScope {
                launch {
                    var previousBinding: ActiveA950Binding? = null
                    preferences.state
                        .map { ActiveA950Binding(it.enabled, it.deviceAddress) }
                        .distinctUntilChanged()
                        .collect { binding ->
                            val connection = bleClient.connection.value
                            val connectionIsActive = connection.phase in setOf(
                                A950ConnectionPhase.CONNECTING,
                                A950ConnectionPhase.DISCOVERING,
                                A950ConnectionPhase.INITIALIZING,
                                A950ConnectionPhase.READY,
                                A950ConnectionPhase.RETRYING,
                            )
                            val connectionMatches = connection.address.equals(
                                binding.deviceAddress,
                                ignoreCase = true,
                            )
                            val shouldDisconnect = connectionIsActive &&
                                (!binding.enabled || !connectionMatches)
                            if (shouldDisconnect) {
                                decisionMutex.withLock {
                                    restoreProgramListBeforeReset("binding-change")
                                    resetRuntimeState()
                                }
                                bleClient.disconnect()
                            } else {
                                val bindingChanged = previousBinding != null && previousBinding != binding
                                val readyForCurrentBinding = connection.ready && connectionMatches
                                if (!binding.enabled || (bindingChanged && !readyForCurrentBinding)) {
                                    decisionMutex.withLock { resetRuntimeState() }
                                }
                            }
                            previousBinding = binding
                            bleClient.startAutoConnect()
                        }
                }
                launch {
                    var initialEmission = true
                    preferences.state
                        .map { it.rules }
                        .distinctUntilChanged()
                        .collect {
                            if (initialEmission) {
                                initialEmission = false
                                return@collect
                            }
                            decisionMutex.withLock {
                                val hadActiveLinkage = _runtime.value.currentPreset != null
                                if (hadActiveLinkage && bleClient.connection.value.ready) {
                                    pendingPreset = null
                                    programListResumePending = true
                                    tryResumeProgramList(
                                        now = System.currentTimeMillis(),
                                        reason = "rules-changed",
                                    )
                                }
                                ruleEngine.reset()
                                pendingPreset = null
                                if (!programListResumePending) {
                                    _runtime.value = A950RuntimeState()
                                }
                            }
                        }
                }
                launch {
                    var wasReady = false
                    bleClient.connection.collect { connection ->
                        if (connection.ready && !wasReady) {
                            connectionInitialized = false
                            decisionMutex.withLock {
                                ruleEngine.reset()
                                clearPendingCommands()
                                resetSignalCaches()
                                _runtime.value = A950RuntimeState()
                                initializeReadyConnection()
                            }
                        }
                        if (!connection.ready) {
                            decisionMutex.withLock {
                                connectionInitialized = false
                                clearPendingCommands()
                            }
                        }
                        wasReady = connection.ready
                    }
                }
                launch {
                    runSignalLoop(
                        label = "motion",
                        intervalMs = MOTION_INTERVAL_MS,
                        fetch = parsReader::fetchLedMotionVehicleSnapshot,
                    ) { signals, at -> latestMotionSample = TimedSignals(signals, at) }
                }
                launch {
                    runSignalLoop(
                        label = "brake",
                        intervalMs = BRAKE_INTERVAL_MS,
                        fetch = parsReader::fetchLedBrakeVehicleSnapshot,
                    ) { signals, at -> latestBrakeSample = TimedSignals(signals, at) }
                }
                launch {
                    runSignalLoop(
                        label = "body",
                        intervalMs = BODY_INTERVAL_MS,
                        fetch = parsReader::fetchLedBodyVehicleSnapshot,
                    ) { signals, at -> latestBodySample = TimedSignals(signals, at) }
                }
                launch {
                    while (kotlin.coroutines.coroutineContext.isActive) {
                        if (
                            preferences.state.value.enabled &&
                            bleClient.connection.value.ready &&
                            !connectionInitialized
                        ) {
                            delay(INITIALIZATION_RETRY_MS)
                            decisionMutex.withLock {
                                if (
                                    runtimeActive &&
                                    preferences.state.value.enabled &&
                                    bleClient.connection.value.ready &&
                                    !connectionInitialized
                                ) {
                                    initializeReadyConnection()
                                }
                            }
                        } else {
                            delay(ENGINE_TICK_MS)
                        }
                    }
                }
                launch {
                    while (kotlin.coroutines.coroutineContext.isActive) {
                        delay(ENGINE_TICK_MS)
                        if (
                            preferences.state.value.enabled &&
                            bleClient.connection.value.ready &&
                            connectionInitialized
                        ) {
                            evaluateAndDispatch(System.currentTimeMillis())
                        }
                    }
                }
            }
        }
        WjxFileLogger.i(TAG, "A950 runtime started")
    }

    @Synchronized
    fun stop() {
        runtimeActive = false
        runtimeJob?.cancel()
        runtimeJob = null
        ruleEngine.reset()
        clearPendingCommands()
        connectionInitialized = false
        resetSignalCaches()
        _runtime.value = A950RuntimeState()
        bleClient.disconnect()
        WjxFileLogger.i(TAG, "A950 runtime stopped")
    }

    suspend fun onVehicleData(data: DiParsData) {
        if (!runtimeActive) return
        latestVehicleSignals = data.toLedCoreSignals()
        evaluateAndDispatch(System.currentTimeMillis())
    }

    suspend fun applyBrightness(percent: Int): Boolean {
        if (!bleClient.connection.value.ready) return false
        return bleClient.setBrightness(percent)
    }

    private suspend fun runSignalLoop(
        label: String,
        intervalMs: Long,
        fetch: suspend () -> com.byd.wjx.data.vehicle.VehicleSnapshot?,
        accept: (LedVehicleSignals, Long) -> Unit,
    ) {
        while (kotlin.coroutines.coroutineContext.isActive) {
            if (
                !preferences.state.value.enabled ||
                !bleClient.connection.value.ready ||
                !connectionInitialized
            ) {
                delay(INACTIVE_LOOP_DELAY_MS)
                continue
            }
            val snapshot = try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WjxFileLogger.w(TAG, "$label A950 signal read failed: ${e.message}", e)
                null
            }
            if (snapshot != null) {
                val readAt = System.currentTimeMillis()
                accept(snapshot.data.toLedSignals(), readAt)
                evaluateAndDispatch(readAt)
            }
            val spent = snapshot?.latencyMs ?: 0L
            delay((intervalMs - spent).coerceAtLeast(MIN_LOOP_DELAY_MS))
        }
    }

    private suspend fun evaluateAndDispatch(now: Long) = decisionMutex.withLock {
        if (!runtimeActive || !preferences.state.value.enabled || !connectionInitialized) {
            return@withLock
        }
        val motion = freshSignals(now, latestMotionSample, MOTION_FRESH_MS)
        val brake = freshSignals(now, latestBrakeSample, BRAKE_FRESH_MS)
        val body = freshSignals(now, latestBodySample, BODY_FRESH_MS)
        val signals = latestVehicleSignals
            .mergeMotion(motion)
            .mergeBrake(brake)
            .mergeBody(body)
        logSignalChanges(signals)
        val next = ruleEngine.evaluate(preferences.state.value.rules, signals, now)
        if (next != null) {
            pendingPreset = next
            programListResumePending = false
        } else if (ruleEngine.consumeProgramListResume()) {
            pendingPreset = null
            programListResumePending = true
        }
        val current = _runtime.value.currentPreset
        if (!bleClient.connection.value.ready) return@withLock

        val presetToSend = pendingPreset
        if (presetToSend != null) {
            val sent = bleClient.play(presetToSend)
            if (sent) pendingPreset = null
            _runtime.value = if (sent) {
                _runtime.value.copy(
                    currentPreset = presetToSend,
                    lastPresetChangedAt = now,
                    lastCommandAt = System.currentTimeMillis(),
                    lastError = null,
                )
            } else {
                _runtime.value.copy(lastError = "A950 命令发送失败")
            }
            WjxFileLogger.i(
                TAG,
                "preset $current -> $presetToSend sent=$sent turn=${signals.turnSignalFlash} " +
                    "gear=${signals.gear} brake=${signals.brakePedalDepth} " +
                    "accel=${signals.accelPedalDepth} brakeLight=${signals.lightBrake} " +
                    "reverseLight=${signals.lightReversing} trunk=${signals.trunk}",
            )
            return@withLock
        }

        if (!programListResumePending) return@withLock
        tryResumeProgramList(now, "condition-ended")
    }

    private suspend fun initializeReadyConnection(): Boolean {
        if (
            !runtimeActive ||
            !preferences.state.value.enabled ||
            !bleClient.connection.value.ready
        ) {
            connectionInitialized = false
            return false
        }
        val brightnessApplied = bleClient.setBrightness(preferences.state.value.brightness)
        val programListResumed = brightnessApplied && bleClient.resumeProgramList()
        val initialized = programListResumed && bleClient.connection.value.ready
        connectionInitialized = initialized
        programListResumePending = false
        _runtime.value = if (initialized) {
            A950RuntimeState(
                lastCommandAt = System.currentTimeMillis(),
                lastError = null,
            )
        } else {
            A950RuntimeState(
                lastError = if (brightnessApplied) {
                    "A950 节目列表恢复失败"
                } else {
                    "亮度设置发送失败"
                }
            )
        }
        WjxFileLogger.i(
            TAG,
            "A950 initialization brightness=$brightnessApplied programList=$programListResumed " +
                "initialized=$initialized",
        )
        return initialized
    }

    private suspend fun restoreProgramListBeforeReset(reason: String) {
        if (_runtime.value.currentPreset == null || !bleClient.connection.value.ready) return
        pendingPreset = null
        programListResumePending = true
        tryResumeProgramList(System.currentTimeMillis(), reason)
    }

    private suspend fun tryResumeProgramList(now: Long, reason: String): Boolean {
        val current = _runtime.value.currentPreset
        val resumed = bleClient.resumeProgramList()
        programListResumePending = !resumed && bleClient.connection.value.ready
        _runtime.value = if (resumed) {
            _runtime.value.copy(
                currentPreset = null,
                lastPresetChangedAt = now,
                lastCommandAt = System.currentTimeMillis(),
                lastError = null,
            )
        } else {
            _runtime.value.copy(lastError = "A950 节目列表恢复失败")
        }
        WjxFileLogger.i(TAG, "resume A950 program list reason=$reason from=$current sent=$resumed")
        return resumed
    }

    private fun resetRuntimeState() {
        ruleEngine.reset()
        clearPendingCommands()
        connectionInitialized = false
        resetSignalCaches()
        _runtime.value = A950RuntimeState()
    }

    private fun clearPendingCommands() {
        pendingPreset = null
        programListResumePending = false
    }

    private fun DiParsData.toLedCoreSignals(): LedVehicleSignals = LedVehicleSignals(
        speed = speed,
        gear = gear,
        powerState = powerState,
    )

    private fun DiParsData.toLedSignals(): LedVehicleSignals = LedVehicleSignals(
        speed = speed,
        turnSignalFlash = turnSignalFlash,
        gear = gear,
        powerState = powerState,
        doorFL = doorFL,
        doorFR = doorFR,
        doorRL = doorRL,
        doorRR = doorRR,
        trunk = trunk,
        brakePedalDepth = brakePedalDepth,
        lightBrake = lightBrake,
        lightReversing = lightReversing,
        accelPedalDepth = accelPedalDepth,
    )

    private fun LedVehicleSignals.mergeMotion(motion: LedVehicleSignals): LedVehicleSignals = copy(
        turnSignalFlash = motion.turnSignalFlash ?: turnSignalFlash,
        gear = motion.gear ?: gear,
    )

    private fun LedVehicleSignals.mergeBrake(brake: LedVehicleSignals): LedVehicleSignals = copy(
        brakePedalDepth = brake.brakePedalDepth ?: brakePedalDepth,
        lightBrake = brake.lightBrake ?: lightBrake,
        lightReversing = brake.lightReversing ?: lightReversing,
        accelPedalDepth = brake.accelPedalDepth ?: accelPedalDepth,
    )

    private fun LedVehicleSignals.mergeBody(body: LedVehicleSignals): LedVehicleSignals = copy(
        doorFL = body.doorFL ?: doorFL,
        doorFR = body.doorFR ?: doorFR,
        doorRL = body.doorRL ?: doorRL,
        doorRR = body.doorRR ?: doorRR,
        trunk = body.trunk ?: trunk,
    )

    private fun freshSignals(
        now: Long,
        sample: TimedSignals,
        maxAgeMs: Long,
    ): LedVehicleSignals =
        if (sample.sampledAt > 0L && now >= sample.sampledAt && now - sample.sampledAt <= maxAgeMs) {
            sample.signals
        } else {
            LedVehicleSignals()
        }

    private fun resetSignalCaches() {
        latestVehicleSignals = LedVehicleSignals()
        latestMotionSample = TimedSignals()
        latestBrakeSample = TimedSignals()
        latestBodySample = TimedSignals()
        lastSignalLogKey = null
    }

    private fun logSignalChanges(signals: LedVehicleSignals) {
        val key = SignalLogKey(
            turn = signals.turnSignalFlash,
            gear = signals.gear,
            moving = (signals.speed ?: 0) > 0,
            brakeBucket = signals.brakePedalDepth?.let { (it / 5) * 5 },
            accelBucket = signals.accelPedalDepth?.let { (it / 5) * 5 },
            brakeLight = signals.lightBrake,
            reverseLight = signals.lightReversing,
            doors = listOf(signals.doorFL, signals.doorFR, signals.doorRL, signals.doorRR),
            trunk = signals.trunk,
        )
        if (key == lastSignalLogKey) return
        lastSignalLogKey = key
        WjxFileLogger.i(
            TAG,
            "signals turn=${signals.turnSignalFlash} gear=${signals.gear} speed=${signals.speed} " +
                "brake=${signals.brakePedalDepth} accel=${signals.accelPedalDepth} " +
                "brakeLight=${signals.lightBrake} reverseLight=${signals.lightReversing} " +
                "doors=${signals.doorFL}/${signals.doorFR}/${signals.doorRL}/${signals.doorRR} " +
                "trunk=${signals.trunk}",
        )
    }

    private companion object {
        private const val TAG = "A950Runtime"
        private const val MOTION_INTERVAL_MS = 100L
        private const val BRAKE_INTERVAL_MS = 200L
        private const val BODY_INTERVAL_MS = 500L
        private const val ENGINE_TICK_MS = 100L
        private const val INITIALIZATION_RETRY_MS = 2_000L
        private const val MOTION_FRESH_MS = 350L
        private const val BRAKE_FRESH_MS = 700L
        private const val BODY_FRESH_MS = 1_500L
        private const val MIN_LOOP_DELAY_MS = 20L
        private const val INACTIVE_LOOP_DELAY_MS = 500L
    }

    private data class SignalLogKey(
        val turn: Int?,
        val gear: Int?,
        val moving: Boolean,
        val brakeBucket: Int?,
        val accelBucket: Int?,
        val brakeLight: Int?,
        val reverseLight: Int?,
        val doors: List<Int?>,
        val trunk: Int?,
    )

    private data class ActiveA950Binding(
        val enabled: Boolean,
        val deviceAddress: String,
    )

    private data class TimedSignals(
        val signals: LedVehicleSignals = LedVehicleSignals(),
        val sampledAt: Long = 0L,
    )
}
