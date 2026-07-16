package com.byd.wjx.led

import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.DiParsData
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
class LedRuntimeCoordinator @Inject constructor(
    private val preferences: LedPreferences,
    private val bleClient: LedBleClient,
    private val parsReader: ParsReader,
) {
    private val arbiter = LedSceneArbiter()
    private val decisionMutex = Mutex()
    private val _runtime = MutableStateFlow(LedRuntimeState())
    val runtime: StateFlow<LedRuntimeState> = _runtime.asStateFlow()

    @Volatile private var connectionInitialized = false
    @Volatile private var latestVehicleSignals = LedVehicleSignals()
    @Volatile private var latestMotionSignals = LedVehicleSignals()
    @Volatile private var latestMotionSignalsAt = 0L
    @Volatile private var latestBrakeSignals = LedVehicleSignals()
    @Volatile private var latestBrakeSignalsAt = 0L
    @Volatile private var latestBodySignals = LedVehicleSignals()
    @Volatile private var latestBodySignalsAt = 0L
    @Volatile private var latestAdasSignals = LedVehicleSignals()
    @Volatile private var latestAdasSignalsAt = 0L
    private var lastSignalLogKey: SignalLogKey? = null
    private var runtimeJob: Job? = null
    private var testRestoreJob: Job? = null

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (runtimeJob?.isActive == true) return
        runtimeJob = scope.launch {
            coroutineScope {
                launch {
                    preferences.state
                        .map { Triple(it.enabled, it.deviceAddress, it.deviceName) }
                        .distinctUntilChanged()
                        .collect { (enabled, _, _) ->
                            if (enabled) {
                                bleClient.startAutoConnect()
                            } else {
                                if (bleClient.connection.value.ready) {
                                    bleClient.play(LedScene.DEFAULT)
                                }
                                bleClient.disconnect()
                                decisionMutex.withLock {
                                    arbiter.reset()
                                    connectionInitialized = false
                                    resetSignalCaches()
                                    _runtime.value = LedRuntimeState(currentScene = LedScene.DEFAULT)
                                }
                            }
                        }
                }
                launch {
                    var wasReady = false
                    bleClient.connection.collect { connection ->
                        if (connection.ready && !wasReady) {
                            connectionInitialized = false
                            val prefs = preferences.state.value
                            decisionMutex.withLock {
                                testRestoreJob?.cancel()
                                testRestoreJob = null
                                arbiter.reset()
                                resetSignalCaches()
                                _runtime.value = LedRuntimeState(currentScene = LedScene.DEFAULT)
                                bleClient.setBrightness(prefs.brightness)
                                val sent = bleClient.play(LedScene.DEFAULT)
                                _runtime.value = if (sent) {
                                    _runtime.value.copy(
                                        lastCommandAt = System.currentTimeMillis(),
                                        lastError = null,
                                    )
                                } else {
                                    _runtime.value.copy(lastError = "默认微笑画面发送失败")
                                }
                                connectionInitialized = sent && bleClient.connection.value.ready
                                WjxFileLogger.i(TAG, "LED connected; default smile sent=$sent")
                            }
                        }
                        if (!connection.ready) connectionInitialized = false
                        wasReady = connection.ready
                    }
                }
                launch {
                    runSignalLoop(
                        label = "motion",
                        intervalMs = MOTION_INTERVAL_MS,
                        fetch = parsReader::fetchLedMotionVehicleSnapshot,
                    ) { signals, at ->
                        latestMotionSignals = signals
                        latestMotionSignalsAt = at
                    }
                }
                launch {
                    runSignalLoop(
                        label = "brake",
                        intervalMs = BRAKE_INTERVAL_MS,
                        fetch = parsReader::fetchLedBrakeVehicleSnapshot,
                    ) { signals, at ->
                        latestBrakeSignals = signals
                        latestBrakeSignalsAt = at
                    }
                }
                launch {
                    runSignalLoop(
                        label = "body",
                        intervalMs = BODY_INTERVAL_MS,
                        fetch = parsReader::fetchLedBodyVehicleSnapshot,
                    ) { signals, at ->
                        latestBodySignals = signals
                        latestBodySignalsAt = at
                    }
                }
                launch {
                    runSignalLoop(
                        label = "ADAS",
                        intervalMs = ADAS_INTERVAL_MS,
                        fetch = parsReader::fetchLedAdasVehicleSnapshot,
                    ) { signals, at ->
                        latestAdasSignals = signals
                        latestAdasSignalsAt = at
                    }
                }
            }
        }
        WjxFileLogger.i(TAG, "LED runtime started")
    }

    @Synchronized
    fun stop() {
        runtimeJob?.cancel()
        runtimeJob = null
        testRestoreJob?.cancel()
        testRestoreJob = null
        arbiter.reset()
        connectionInitialized = false
        resetSignalCaches()
        _runtime.value = LedRuntimeState(currentScene = LedScene.DEFAULT)
        bleClient.restoreDefaultAndDisconnect()
        WjxFileLogger.i(TAG, "LED runtime stopped")
    }

    suspend fun onVehicleData(data: DiParsData) {
        latestVehicleSignals = data.toLedCoreSignals()
        evaluateAndDispatch(System.currentTimeMillis())
    }

    suspend fun testScene(scene: LedScene): Boolean {
        if (!bleClient.connection.value.ready || !connectionInitialized) return false
        testRestoreJob?.cancel()
        val sent = bleClient.play(scene)
        if (sent && scene != LedScene.DEFAULT) {
            val owner = runtimeJob ?: return sent
            testRestoreJob = CoroutineScope(owner + kotlinx.coroutines.Dispatchers.IO).launch {
                delay(TEST_SCENE_DURATION_MS)
                bleClient.play(_runtime.value.currentScene)
            }
        }
        return sent
    }

    suspend fun applyBrightness(percent: Int): Boolean = bleClient.setBrightness(percent)

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
            if (latestVehicleSignals.powerState?.let { !BydSignalSemantics.isPowered(it) } == true) {
                delay(POWERED_OFF_FAST_DELAY_MS)
                continue
            }
            val snapshot = try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WjxFileLogger.w(TAG, "$label LED signal read failed: ${e.message}", e)
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
        if (!preferences.state.value.enabled || !connectionInitialized) return@withLock
        val motion = freshSignals(now, latestMotionSignalsAt, MOTION_FRESH_MS, latestMotionSignals)
        val brake = freshSignals(now, latestBrakeSignalsAt, BRAKE_FRESH_MS, latestBrakeSignals)
        val body = freshSignals(now, latestBodySignalsAt, BODY_FRESH_MS, latestBodySignals)
        val adas = freshSignals(now, latestAdasSignalsAt, ADAS_FRESH_MS, latestAdasSignals)
        val signals = latestVehicleSignals
            .mergeMotion(motion)
            .mergeBrake(brake)
            .mergeBody(body)
            .mergeAdas(adas)
        logSignalChanges(signals)
        val next = arbiter.evaluate(signals, now)
        val current = _runtime.value.currentScene
        if (next == current) return@withLock

        _runtime.value = _runtime.value.copy(currentScene = next, lastSceneChangedAt = now)
        if (!bleClient.connection.value.ready) return@withLock
        val sent = bleClient.play(next)
        _runtime.value = if (sent) {
            _runtime.value.copy(lastCommandAt = System.currentTimeMillis(), lastError = null)
        } else {
            _runtime.value.copy(lastError = "LED命令发送失败")
        }
        WjxFileLogger.i(
            TAG,
            "scene $current -> $next sent=$sent turn=${signals.turnSignalFlash} " +
                "gear=${signals.gear} brake=${signals.brakePedalDepth} " +
                "brakeLight=${signals.lightBrake} " +
                "acc=${signals.adasAccMode}/${signals.accCruiseState} " +
                "lcc=${signals.adasLccNcaStatus} tja=${signals.adasTjaState} " +
                "lane=${signals.laneKeepState} mnoa=${signals.adasMnoaStatus} " +
                "exec=${signals.adasExecSpeed} apa=${signals.adasApaParkingStatus}",
        )
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
        adasAccMode = adasAccMode,
        adasLccNcaStatus = adasLccNcaStatus,
        adasTjaState = adasTjaState,
        laneKeepState = laneKeepState,
        accCruiseState = accCruiseState,
        adasMnoaStatus = adasMnoaStatus,
        adasExecSpeed = adasExecSpeed,
        adasApaParkingStatus = adasApaParkingStatus,
        brakePedalDepth = brakePedalDepth,
        lightBrake = lightBrake,
    )

    private fun LedVehicleSignals.mergeMotion(motion: LedVehicleSignals): LedVehicleSignals = copy(
        turnSignalFlash = motion.turnSignalFlash ?: turnSignalFlash,
        gear = motion.gear ?: gear,
    )

    private fun LedVehicleSignals.mergeBrake(brake: LedVehicleSignals): LedVehicleSignals = copy(
        brakePedalDepth = brake.brakePedalDepth ?: brakePedalDepth,
        lightBrake = brake.lightBrake ?: lightBrake,
    )

    private fun LedVehicleSignals.mergeBody(body: LedVehicleSignals): LedVehicleSignals = copy(
        doorFL = body.doorFL ?: doorFL,
        doorFR = body.doorFR ?: doorFR,
        doorRL = body.doorRL ?: doorRL,
        doorRR = body.doorRR ?: doorRR,
        adasApaParkingStatus = body.adasApaParkingStatus ?: adasApaParkingStatus,
    )

    private fun LedVehicleSignals.mergeAdas(adas: LedVehicleSignals): LedVehicleSignals = copy(
        adasAccMode = adas.adasAccMode ?: adasAccMode,
        adasLccNcaStatus = adas.adasLccNcaStatus ?: adasLccNcaStatus,
        adasTjaState = adas.adasTjaState ?: adasTjaState,
        laneKeepState = adas.laneKeepState ?: laneKeepState,
        accCruiseState = adas.accCruiseState ?: accCruiseState,
        adasMnoaStatus = adas.adasMnoaStatus ?: adasMnoaStatus,
    )

    private fun freshSignals(
        now: Long,
        sampledAt: Long,
        maxAgeMs: Long,
        signals: LedVehicleSignals,
    ): LedVehicleSignals =
        if (sampledAt > 0L && now >= sampledAt && now - sampledAt <= maxAgeMs) {
            signals
        } else {
            LedVehicleSignals()
        }

    private fun resetSignalCaches() {
        latestVehicleSignals = LedVehicleSignals()
        latestMotionSignals = LedVehicleSignals()
        latestMotionSignalsAt = 0L
        latestBrakeSignals = LedVehicleSignals()
        latestBrakeSignalsAt = 0L
        latestBodySignals = LedVehicleSignals()
        latestBodySignalsAt = 0L
        latestAdasSignals = LedVehicleSignals()
        latestAdasSignalsAt = 0L
        lastSignalLogKey = null
    }

    private fun logSignalChanges(signals: LedVehicleSignals) {
        val key = SignalLogKey(
            turn = signals.turnSignalFlash,
            gear = signals.gear,
            moving = (signals.speed ?: 0) > 0,
            brakeBucket = signals.brakePedalDepth?.let { (it / 5) * 5 },
            brakeLight = signals.lightBrake,
            acc = signals.adasAccMode,
            accCruise = signals.accCruiseState,
            lcc = signals.adasLccNcaStatus,
            tja = signals.adasTjaState,
            lane = signals.laneKeepState,
            mnoa = signals.adasMnoaStatus,
            execSpeed = signals.adasExecSpeed,
            apa = signals.adasApaParkingStatus,
        )
        if (key == lastSignalLogKey) return
        lastSignalLogKey = key
        WjxFileLogger.i(
            TAG,
            "signals turn=${signals.turnSignalFlash} gear=${signals.gear} speed=${signals.speed} " +
                "brake=${signals.brakePedalDepth} brakeLight=${signals.lightBrake} " +
                "acc=${signals.adasAccMode}/${signals.accCruiseState} " +
                "lcc=${signals.adasLccNcaStatus} tja=${signals.adasTjaState} " +
                "lane=${signals.laneKeepState} mnoa=${signals.adasMnoaStatus} " +
                "exec=${signals.adasExecSpeed} apa=${signals.adasApaParkingStatus}",
        )
    }

    private companion object {
        private const val TAG = "LedRuntimeCoordinator"
        private const val MOTION_INTERVAL_MS = 100L
        private const val BRAKE_INTERVAL_MS = 200L
        private const val BODY_INTERVAL_MS = 500L
        private const val ADAS_INTERVAL_MS = 1_000L
        private const val MOTION_FRESH_MS = 350L
        private const val BRAKE_FRESH_MS = 700L
        private const val BODY_FRESH_MS = 1_500L
        private const val ADAS_FRESH_MS = 2_500L
        private const val MIN_LOOP_DELAY_MS = 20L
        private const val INACTIVE_LOOP_DELAY_MS = 500L
        private const val POWERED_OFF_FAST_DELAY_MS = 500L
        private const val TEST_SCENE_DURATION_MS = 4_000L
    }

    private data class SignalLogKey(
        val turn: Int?,
        val gear: Int?,
        val moving: Boolean,
        val brakeBucket: Int?,
        val brakeLight: Int?,
        val acc: Int?,
        val accCruise: Int?,
        val lcc: Int?,
        val tja: Int?,
        val lane: Int?,
        val mnoa: Int?,
        val execSpeed: Int?,
        val apa: Int?,
    )
}
