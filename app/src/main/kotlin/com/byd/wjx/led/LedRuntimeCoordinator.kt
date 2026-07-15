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

    @Volatile private var latestRegularSignals = LedVehicleSignals()
    @Volatile private var latestFastSignals = LedVehicleSignals()
    @Volatile private var latestFastSignalsAt = 0L
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
                                    latestRegularSignals = LedVehicleSignals()
                                    latestFastSignals = LedVehicleSignals()
                                    latestFastSignalsAt = 0L
                                    lastSignalLogKey = null
                                    _runtime.value = LedRuntimeState(currentScene = LedScene.DEFAULT)
                                }
                            }
                        }
                }
                launch {
                    var wasReady = false
                    bleClient.connection.collect { connection ->
                        if (connection.ready && !wasReady) {
                            val prefs = preferences.state.value
                            bleClient.setBrightness(prefs.brightness)
                            bleClient.play(_runtime.value.currentScene)
                        }
                        wasReady = connection.ready
                    }
                }
                launch { runFastSignalLoop() }
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
        latestRegularSignals = LedVehicleSignals()
        latestFastSignals = LedVehicleSignals()
        latestFastSignalsAt = 0L
        lastSignalLogKey = null
        _runtime.value = LedRuntimeState(currentScene = LedScene.DEFAULT)
        bleClient.restoreDefaultAndDisconnect()
        WjxFileLogger.i(TAG, "LED runtime stopped")
    }

    suspend fun onVehicleData(data: DiParsData) {
        latestRegularSignals = data.toLedSignals()
        evaluateAndDispatch(System.currentTimeMillis())
    }

    suspend fun testScene(scene: LedScene): Boolean {
        if (!bleClient.connection.value.ready) return false
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

    private suspend fun runFastSignalLoop() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            if (!preferences.state.value.enabled || !bleClient.connection.value.ready) {
                delay(FAST_IDLE_DELAY_MS)
                continue
            }
            if (latestRegularSignals.powerState?.let { !BydSignalSemantics.isPowered(it) } == true) {
                delay(POWERED_OFF_FAST_DELAY_MS)
                continue
            }
            val snapshot = try {
                parsReader.fetchLedVehicleSnapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WjxFileLogger.w(TAG, "fast LED signal read failed: ${e.message}", e)
                null
            }
            if (snapshot != null) {
                latestFastSignals = snapshot.data.toLedSignals()
                latestFastSignalsAt = System.currentTimeMillis()
                evaluateAndDispatch(latestFastSignalsAt)
            }
            val spent = snapshot?.latencyMs ?: 0L
            delay((FAST_INTERVAL_MS - spent).coerceAtLeast(MIN_LOOP_DELAY_MS))
        }
    }

    private suspend fun evaluateAndDispatch(now: Long) = decisionMutex.withLock {
        if (!preferences.state.value.enabled) return@withLock
        val fastSignals = if (now - latestFastSignalsAt <= FAST_SIGNAL_FRESH_MS) {
            latestFastSignals
        } else {
            LedVehicleSignals()
        }
        val signals = latestRegularSignals.mergeFast(fastSignals)
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
            "scene $current -> $next sent=$sent left=${signals.turnSignalLeft} right=${signals.turnSignalRight} " +
                "hazard=${signals.hazardLights} gear=${signals.gear} brake=${signals.brakePedalDepth} " +
                "brakeLight=${signals.lightBrake} reverseLight=${signals.lightReversing} " +
                "lcc=${signals.adasLccNcaStatus} tja=${signals.adasTjaState}",
        )
    }

    private fun DiParsData.toLedSignals(): LedVehicleSignals = LedVehicleSignals(
        speed = speed,
        turnSignalLeft = turnSignalLeft,
        turnSignalRight = turnSignalRight,
        hazardLights = hazardLights,
        gear = gear,
        powerState = powerState,
        driverDoor = doorFL,
        adasLccNcaStatus = adasLccNcaStatus,
        adasTjaState = adasTjaState,
        laneKeepState = laneKeepState,
        brakePedalDepth = brakePedalDepth,
        lightBrake = lightBrake,
        lightReversing = lightReversing,
    )

    private fun LedVehicleSignals.mergeFast(fast: LedVehicleSignals): LedVehicleSignals = copy(
        turnSignalLeft = fast.turnSignalLeft ?: turnSignalLeft,
        turnSignalRight = fast.turnSignalRight ?: turnSignalRight,
        hazardLights = fast.hazardLights ?: hazardLights,
        gear = fast.gear ?: gear,
        brakePedalDepth = fast.brakePedalDepth ?: brakePedalDepth,
        lightBrake = fast.lightBrake ?: lightBrake,
        lightReversing = fast.lightReversing ?: lightReversing,
    )

    private fun logSignalChanges(signals: LedVehicleSignals) {
        val key = SignalLogKey(
            left = signals.turnSignalLeft,
            right = signals.turnSignalRight,
            hazard = signals.hazardLights,
            gear = signals.gear,
            moving = (signals.speed ?: 0) > 0,
            brakeBucket = signals.brakePedalDepth?.let { (it / 5) * 5 },
            brakeLight = signals.lightBrake,
            reverseLight = signals.lightReversing,
            lcc = signals.adasLccNcaStatus,
            tja = signals.adasTjaState,
            laneKeep = signals.laneKeepState,
        )
        if (key == lastSignalLogKey) return
        lastSignalLogKey = key
        WjxFileLogger.i(
            TAG,
            "signals left=${signals.turnSignalLeft} right=${signals.turnSignalRight} " +
                "hazard=${signals.hazardLights} gear=${signals.gear} speed=${signals.speed} " +
                "brake=${signals.brakePedalDepth} brakeLight=${signals.lightBrake} " +
                "reverseLight=${signals.lightReversing} lcc=${signals.adasLccNcaStatus} " +
                "tja=${signals.adasTjaState} lane=${signals.laneKeepState}",
        )
    }

    private companion object {
        private const val TAG = "LedRuntimeCoordinator"
        private const val FAST_INTERVAL_MS = 100L
        private const val FAST_SIGNAL_FRESH_MS = 350L
        private const val MIN_LOOP_DELAY_MS = 20L
        private const val FAST_IDLE_DELAY_MS = 500L
        private const val POWERED_OFF_FAST_DELAY_MS = 500L
        private const val TEST_SCENE_DURATION_MS = 4_000L
    }

    private data class SignalLogKey(
        val left: Int?,
        val right: Int?,
        val hazard: Int?,
        val gear: Int?,
        val moving: Boolean,
        val brakeBucket: Int?,
        val brakeLight: Int?,
        val reverseLight: Int?,
        val lcc: Int?,
        val tja: Int?,
        val laneKeep: Int?,
    )
}
