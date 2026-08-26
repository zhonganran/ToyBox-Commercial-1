package com.byd.wjx.led.ipixelfa

import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.led.LedSignalTier
import com.byd.wjx.led.LedVehicleSignalAccumulator
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
class IpixelFaRuntimeCoordinator @Inject constructor(
    private val preferences: IpixelFaPreferences,
    private val bleClient: IpixelFaBleClient,
    private val parsReader: ParsReader,
) {
    private val ruleEngine = IpixelFaLinkageEngine()
    private val signalAccumulator = LedVehicleSignalAccumulator()
    private val decisionMutex = Mutex()
    private val _runtime = MutableStateFlow(IpixelFaRuntimeState())
    val runtime: StateFlow<IpixelFaRuntimeState> = _runtime.asStateFlow()

    @Volatile private var runtimeActive = false
    @Volatile private var connectionInitialized = false
    private var pendingPreset: IpixelFaPreset? = null
    private var programListResumePending = false
    private var runtimeJob: Job? = null

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (runtimeJob?.isActive == true) return
        runtimeActive = true
        runtimeJob = scope.launch {
            coroutineScope {
                launch {
                    var previousBinding: ActiveFaBinding? = null
                    preferences.state
                        .map { ActiveFaBinding(it.enabled, it.deviceAddress) }
                        .distinctUntilChanged()
                        .collect { binding ->
                            val connection = bleClient.connection.value
                            val connectionIsActive = connection.phase in setOf(
                                IpixelFaConnectionPhase.CONNECTING,
                                IpixelFaConnectionPhase.DISCOVERING,
                                IpixelFaConnectionPhase.READY,
                                IpixelFaConnectionPhase.RETRYING,
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
                                    decisionMutex.withLock {
                                        resetRuntimeState()
                                    }
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
                                    _runtime.value = IpixelFaRuntimeState()
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
                                _runtime.value = IpixelFaRuntimeState()
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
                    runLedSignalLoop(
                        tag = TAG,
                        deviceLabel = "FA",
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
                        deviceLabel = "FA",
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
                        deviceLabel = "FA",
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
                        deviceLabel = "FA",
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
        WjxFileLogger.i(TAG, "FA runtime started")
    }

    @Synchronized
    fun stop() {
        runtimeActive = false
        runtimeJob?.cancel()
        runtimeJob = null
        ruleEngine.reset()
        clearPendingCommands()
        connectionInitialized = false
        signalAccumulator.reset()
        _runtime.value = IpixelFaRuntimeState()
        bleClient.disconnect()
        WjxFileLogger.i(TAG, "FA runtime stopped")
    }

    suspend fun onVehicleData(data: DiParsData) {
        if (!runtimeActive) return
        signalAccumulator.updateCore(data)
        evaluateAndDispatch(System.currentTimeMillis())
    }

    suspend fun applyBrightness(percent: Int): Boolean {
        if (!bleClient.connection.value.ready) return false
        return bleClient.setBrightness(percent)
    }

    private suspend fun evaluateAndDispatch(now: Long) = decisionMutex.withLock {
        if (!runtimeActive || !preferences.state.value.enabled || !connectionInitialized) {
            return@withLock
        }
        val signals = signalAccumulator.snapshot(now)
        signalAccumulator.logChanges(TAG, signals)
        val next = ruleEngine.evaluate(preferences.state.value.rules, signals, now)
        if (next != null) {
            pendingPreset = next
            programListResumePending = false
        } else if (ruleEngine.consumeProgramListRestore()) {
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
                _runtime.value.copy(lastError = "标准 FA 命令发送失败")
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

        if (programListResumePending) tryResumeProgramList(now, "condition-ended")
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
        val programListApplied = brightnessApplied && bleClient.playProgramList()
        val initialized = programListApplied && bleClient.connection.value.ready
        connectionInitialized = initialized
        programListResumePending = false
        _runtime.value = if (initialized) {
            IpixelFaRuntimeState(
                lastCommandAt = System.currentTimeMillis(),
                lastError = null,
            )
        } else {
            IpixelFaRuntimeState(
                lastError = if (brightnessApplied) {
                    "节目列表恢复发送失败"
                } else {
                    "亮度设置发送失败"
                }
            )
        }
        WjxFileLogger.i(
            TAG,
            "FA initialization brightness=$brightnessApplied programList=$programListApplied " +
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
        val resumed = bleClient.playProgramList()
        programListResumePending = !resumed && bleClient.connection.value.ready
        _runtime.value = if (resumed) {
            _runtime.value.copy(
                currentPreset = null,
                lastPresetChangedAt = now,
                lastCommandAt = System.currentTimeMillis(),
                lastError = null,
            )
        } else {
            _runtime.value.copy(lastError = "节目列表恢复发送失败")
        }
        WjxFileLogger.i(TAG, "resume FA program list reason=$reason from=$current sent=$resumed")
        return resumed
    }

    private fun resetRuntimeState() {
        ruleEngine.reset()
        clearPendingCommands()
        connectionInitialized = false
        signalAccumulator.reset()
        _runtime.value = IpixelFaRuntimeState()
    }

    private fun clearPendingCommands() {
        pendingPreset = null
        programListResumePending = false
    }

    private fun shouldPollSignals(): Boolean =
        preferences.state.value.enabled && bleClient.connection.value.ready && connectionInitialized

    private companion object {
        private const val TAG = "IpixelFaRuntime"
        private const val MOTION_INTERVAL_MS = 100L
        private const val BRAKE_INTERVAL_MS = 200L
        private const val BODY_INTERVAL_MS = 500L
        private const val ADAS_INTERVAL_MS = 1_000L
        private const val ENGINE_TICK_MS = 100L
        private const val INITIALIZATION_RETRY_MS = 2_000L
    }

    private data class ActiveFaBinding(
        val enabled: Boolean,
        val deviceAddress: String,
    )
}
