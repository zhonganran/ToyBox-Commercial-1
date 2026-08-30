package com.byd.wjx.led.mafu

import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.led.LedSignalTier
import com.byd.wjx.led.LedVehicleSignalAccumulator
import com.byd.wjx.led.brightnessAt
import com.byd.wjx.led.runLedSignalLoop
import com.byd.wjx.log.WjxFileLogger
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
class MafuRuntimeCoordinator @Inject constructor(
    private val preferences: MafuPreferences,
    private val bleClient: MafuBleClient,
    private val parsReader: ParsReader,
) {
    private val ruleEngine = MafuLinkageEngine()
    private val signalAccumulator = LedVehicleSignalAccumulator()
    private val decisionMutex = Mutex()
    private val _runtime = MutableStateFlow(MafuRuntimeState())
    val runtime: StateFlow<MafuRuntimeState> = _runtime.asStateFlow()

    @Volatile private var runtimeActive = false
    @Volatile private var connectionInitialized = false
    private var pendingRemoteKey: MafuRemoteKey? = null
    private var programListResumePending = false
    private var lastAppliedBrightness: Int? = null
    private var lastBrightnessEvaluationAtMs = Long.MIN_VALUE
    private var nextBrightnessRetryAtMs = 0L
    private var runtimeJob: Job? = null

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (runtimeJob?.isActive == true) return
        runtimeActive = true
        runtimeJob = scope.launch {
            coroutineScope {
                launch {
                    var previousBinding: ActiveMafuBinding? = null
                    preferences.state
                        .map { ActiveMafuBinding(it.enabled, it.deviceAddress) }
                        .distinctUntilChanged()
                        .collect { binding ->
                            val connection = bleClient.connection.value
                            val connectionIsActive = connection.phase in ACTIVE_CONNECTION_PHASES
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
                        .map { it.remoteModel to it.rules }
                        .distinctUntilChanged()
                        .collect {
                            if (initialEmission) {
                                initialEmission = false
                                return@collect
                            }
                            decisionMutex.withLock {
                                val hadActiveLinkage = _runtime.value.currentRemoteKey != null
                                if (hadActiveLinkage && bleClient.connection.value.ready) {
                                    pendingRemoteKey = null
                                    programListResumePending = true
                                    tryResumeProgramList(
                                        now = System.currentTimeMillis(),
                                        reason = "rules-or-model-changed",
                                    )
                                }
                                ruleEngine.reset()
                                pendingRemoteKey = null
                                if (!programListResumePending) {
                                    _runtime.value = MafuRuntimeState()
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
                                signalAccumulator.reset()
                                _runtime.value = MafuRuntimeState()
                                initializeReadyConnection()
                            }
                        }
                        if (!connection.ready) {
                            decisionMutex.withLock {
                                connectionInitialized = false
                                clearPendingCommands()
                                resetBrightnessState()
                            }
                        }
                        wasReady = connection.ready
                    }
                }
                launch {
                    runLedSignalLoop(
                        tag = TAG,
                        deviceLabel = "Mafu",
                        signalLabel = "motion",
                        tier = LedSignalTier.MOTION,
                        intervalMs = MOTION_INTERVAL_MS,
                        shouldPoll = ::shouldPollSignals,
                        fetch = parsReader::fetchLedMotionVehicleSnapshot,
                        accumulator = signalAccumulator,
                        onSample = ::evaluateAndDispatch,
                    )
                }
                launch {
                    runLedSignalLoop(
                        tag = TAG,
                        deviceLabel = "Mafu",
                        signalLabel = "brake",
                        tier = LedSignalTier.BRAKE,
                        intervalMs = BRAKE_INTERVAL_MS,
                        shouldPoll = ::shouldPollSignals,
                        fetch = parsReader::fetchLedBrakeVehicleSnapshot,
                        accumulator = signalAccumulator,
                        onSample = ::evaluateAndDispatch,
                    )
                }
                launch {
                    runLedSignalLoop(
                        tag = TAG,
                        deviceLabel = "Mafu",
                        signalLabel = "body",
                        tier = LedSignalTier.BODY,
                        intervalMs = BODY_INTERVAL_MS,
                        shouldPoll = ::shouldPollSignals,
                        fetch = parsReader::fetchLedBodyVehicleSnapshot,
                        accumulator = signalAccumulator,
                        onSample = ::evaluateAndDispatch,
                    )
                }
                launch {
                    runLedSignalLoop(
                        tag = TAG,
                        deviceLabel = "Mafu",
                        signalLabel = "ADAS",
                        tier = LedSignalTier.ADAS,
                        intervalMs = ADAS_INTERVAL_MS,
                        shouldPoll = ::shouldPollSignals,
                        fetch = parsReader::fetchLedAdasVehicleSnapshot,
                        accumulator = signalAccumulator,
                        onSample = ::evaluateAndDispatch,
                    )
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
        WjxFileLogger.i(TAG, "Mafu runtime started")
    }

    @Synchronized
    fun requestReconnect(reason: String) {
        val binding = preferences.state.value
        if (!runtimeActive || !binding.enabled || binding.deviceAddress.isBlank()) return
        val connection = bleClient.connection.value
        val connectionMatches = connection.address.equals(binding.deviceAddress, ignoreCase = true)
        if (connectionMatches && connection.phase in ACTIVE_CONNECTION_PHASES) return
        WjxFileLogger.i(TAG, "Mafu reconnect requested reason=$reason")
        bleClient.startAutoConnect()
    }

    @Synchronized
    fun stop() {
        runtimeActive = false
        runtimeJob?.cancel()
        runtimeJob = null
        ruleEngine.reset()
        clearPendingCommands()
        connectionInitialized = false
        resetBrightnessState()
        signalAccumulator.reset()
        _runtime.value = MafuRuntimeState()
        bleClient.disconnect()
        WjxFileLogger.i(TAG, "Mafu runtime stopped")
    }

    suspend fun onVehicleData(data: DiParsData) {
        if (!runtimeActive) return
        signalAccumulator.updateCore(data)
        evaluateAndDispatch(System.currentTimeMillis())
    }

    suspend fun applyBrightness(level: Int): Boolean = decisionMutex.withLock {
        if (!bleClient.connection.value.ready) return false
        val normalized = level.coerceIn(1, 15)
        val sent = bleClient.setBrightness(normalized)
        if (sent) {
            lastAppliedBrightness = normalized
            nextBrightnessRetryAtMs = 0L
        }
        sent
    }

    suspend fun syncBrightness(): Boolean = decisionMutex.withLock {
        if (!bleClient.connection.value.ready) return false
        applyDesiredBrightness(System.currentTimeMillis(), force = true)
    }

    suspend fun setScreenPower(enabled: Boolean): Boolean {
        if (!bleClient.connection.value.ready) return false
        val sent = bleClient.setScreenPower(enabled)
        if (sent) {
            _runtime.value = _runtime.value.copy(
                screenPowerEnabled = enabled,
                lastCommandAt = System.currentTimeMillis(),
                lastError = null,
            )
        }
        return sent
    }

    private suspend fun evaluateAndDispatch(now: Long) = decisionMutex.withLock {
        val prefs = preferences.state.value
        if (!runtimeActive || !prefs.enabled || !connectionInitialized) return@withLock
        val signals = signalAccumulator.snapshot(now)
        signalAccumulator.logChanges(TAG, signals)
        val next = ruleEngine.evaluate(prefs.rules, prefs.remoteModel, signals, now)
        if (next != null) {
            pendingRemoteKey = next
            programListResumePending = false
        } else if (ruleEngine.consumeProgramListResume()) {
            pendingRemoteKey = null
            programListResumePending = true
        }
        val current = _runtime.value.currentRemoteKey
        if (!bleClient.connection.value.ready) return@withLock
        ensureScheduledBrightness(now)

        val keyToSend = pendingRemoteKey
        if (keyToSend != null) {
            val sent = bleClient.play(keyToSend, prefs.remoteModel)
            if (sent) pendingRemoteKey = null
            _runtime.value = if (sent) {
                _runtime.value.copy(
                    currentRemoteKey = keyToSend,
                    lastProgramId = prefs.remoteModel.programId(keyToSend),
                    lastPresetChangedAt = now,
                    lastCommandAt = System.currentTimeMillis(),
                    lastError = null,
                )
            } else {
                _runtime.value.copy(lastError = "马夫 LED 命令发送失败")
            }
            WjxFileLogger.i(
                TAG,
                "remoteKey $current -> $keyToSend program=${prefs.remoteModel.programId(keyToSend)} " +
                    "sent=$sent turn=${signals.turnSignalFlash} gear=${signals.gear} " +
                    "brake=${signals.brakePedalDepth} accel=${signals.accelPedalDepth} " +
                    "brakeLight=${signals.lightBrake} reverseLight=${signals.lightReversing} " +
                    "trunk=${signals.trunk}",
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
        val screenPowerApplied = bleClient.setScreenPower(true)
        val desiredBrightness = desiredBrightness(System.currentTimeMillis())
        val brightnessApplied = screenPowerApplied && bleClient.setBrightness(desiredBrightness)
        if (brightnessApplied) {
            lastAppliedBrightness = desiredBrightness
            nextBrightnessRetryAtMs = 0L
        }
        val programListApplied = brightnessApplied && bleClient.playProgramList()
        val initialized = programListApplied && bleClient.connection.value.ready
        connectionInitialized = initialized
        programListResumePending = false
        _runtime.value = if (initialized) {
            MafuRuntimeState(
                lastCommandAt = System.currentTimeMillis(),
                lastError = null,
                screenPowerEnabled = true,
            )
        } else {
            MafuRuntimeState(
                screenPowerEnabled = screenPowerApplied,
                lastError = when {
                    !screenPowerApplied -> "马夫开屏命令发送失败"
                    !brightnessApplied -> "亮度设置发送失败"
                    else -> "马夫节目列表恢复失败"
                }
            )
        }
        WjxFileLogger.i(
            TAG,
            "Mafu initialization screenPower=$screenPowerApplied brightness=$brightnessApplied " +
                "programList=$programListApplied initialized=$initialized",
        )
        return initialized
    }

    private suspend fun restoreProgramListBeforeReset(reason: String) {
        if (_runtime.value.currentRemoteKey == null || !bleClient.connection.value.ready) return
        pendingRemoteKey = null
        programListResumePending = true
        tryResumeProgramList(System.currentTimeMillis(), reason)
    }

    private suspend fun tryResumeProgramList(now: Long, reason: String): Boolean {
        val current = _runtime.value.currentRemoteKey
        val resumed = bleClient.playProgramList()
        programListResumePending = !resumed && bleClient.connection.value.ready
        _runtime.value = if (resumed) {
            _runtime.value.copy(
                currentRemoteKey = null,
                lastProgramId = null,
                lastPresetChangedAt = now,
                lastCommandAt = System.currentTimeMillis(),
                lastError = null,
            )
        } else {
            _runtime.value.copy(lastError = "马夫节目列表恢复失败")
        }
        WjxFileLogger.i(TAG, "resume Mafu program list reason=$reason from=$current sent=$resumed")
        return resumed
    }

    private fun resetRuntimeState() {
        ruleEngine.reset()
        clearPendingCommands()
        connectionInitialized = false
        resetBrightnessState()
        signalAccumulator.reset()
        _runtime.value = MafuRuntimeState()
    }

    private suspend fun ensureScheduledBrightness(now: Long) {
        if (!preferences.state.value.brightnessSchedule.enabled) return
        if (
            lastBrightnessEvaluationAtMs != Long.MIN_VALUE &&
            now >= lastBrightnessEvaluationAtMs &&
            now - lastBrightnessEvaluationAtMs < BRIGHTNESS_EVALUATION_INTERVAL_MS
        ) {
            return
        }
        lastBrightnessEvaluationAtMs = now
        applyDesiredBrightness(now, force = false)
    }

    private suspend fun applyDesiredBrightness(now: Long, force: Boolean): Boolean {
        val desired = desiredBrightness(now)
        if (!force && desired == lastAppliedBrightness) return true
        if (!force && now < nextBrightnessRetryAtMs) return false
        val sent = bleClient.setBrightness(desired)
        if (sent) {
            lastAppliedBrightness = desired
            nextBrightnessRetryAtMs = 0L
            WjxFileLogger.i(TAG, "scheduled brightness applied=$desired")
        } else {
            nextBrightnessRetryAtMs = now + BRIGHTNESS_RETRY_MS
            WjxFileLogger.w(TAG, "scheduled brightness failed desired=$desired")
        }
        return sent
    }

    private fun desiredBrightness(now: Long): Int {
        val prefs = preferences.state.value
        return prefs.brightnessSchedule.brightnessAt(now, prefs.brightness).coerceIn(1, 15)
    }

    private fun resetBrightnessState() {
        lastAppliedBrightness = null
        lastBrightnessEvaluationAtMs = Long.MIN_VALUE
        nextBrightnessRetryAtMs = 0L
    }

    private fun clearPendingCommands() {
        pendingRemoteKey = null
        programListResumePending = false
    }

    private fun shouldPollSignals(): Boolean =
        preferences.state.value.enabled && bleClient.connection.value.ready && connectionInitialized

    private companion object {
        private const val TAG = "MafuRuntime"
        private val ACTIVE_CONNECTION_PHASES = setOf(
            MafuConnectionPhase.CONNECTING,
            MafuConnectionPhase.DISCOVERING,
            MafuConnectionPhase.SUBSCRIBING,
            MafuConnectionPhase.INITIALIZING,
            MafuConnectionPhase.READY,
            MafuConnectionPhase.RETRYING,
        )
        private const val MOTION_INTERVAL_MS = 100L
        private const val BRAKE_INTERVAL_MS = 200L
        private const val BODY_INTERVAL_MS = 500L
        private const val ADAS_INTERVAL_MS = 1_000L
        private const val ENGINE_TICK_MS = 100L
        private const val INITIALIZATION_RETRY_MS = 2_000L
        private const val BRIGHTNESS_RETRY_MS = 2_000L
        private const val BRIGHTNESS_EVALUATION_INTERVAL_MS = 1_000L
    }

    private data class ActiveMafuBinding(
        val enabled: Boolean,
        val deviceAddress: String,
    )
}
