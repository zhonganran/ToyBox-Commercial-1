package com.byd.wjx.data.charging

import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.BatteryHealthRepository
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.vehicle.VehicleGateway
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reconstructs a completed charge from the persisted parked anchor and current vehicle readings.
 *
 * The detector owns serialization and the gun-glitch state machine. Pure charge gates and energy
 * selection live in ChargingCatchUpPolicies; database writes live in [CatchUpSessionRecorder].
 */
@Singleton
class AutoserviceChargingDetector @Inject constructor(
    private val gateway: VehicleGateway,
    chargeRepo: ChargeRepository,
    batteryHealthRepo: BatteryHealthRepository,
    private val stateStore: ChargingStateStore,
    private val classifier: ChargingTypeClassifier,
    private val settings: SettingsRepository,
    parsReader: ParsReader,
    private val journal: CatchUpJournal,
) {
    private val mutex = Mutex()
    private val recorder = CatchUpSessionRecorder(
        chargeRepo,
        batteryHealthRepo,
        stateStore,
        parsReader,
    )
    private var consecutiveGunGlitches = 0
    @Volatile private var lastSample: DiParsData? = null
    private val _state = MutableStateFlow(DetectorState.IDLE)
    val state: StateFlow<DetectorState> = _state

    fun onSample(data: DiParsData) {
        lastSample = data
    }

    /**
     * Advances the parked anchor while SOC is flat or falling. A rise leaves the old anchor intact
     * and asks the caller to re-arm catch-up unless power still shows energy flowing into the pack.
     */
    suspend fun recordParkedAnchor(
        data: DiParsData,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (ChargeGunState.isConnected(data.chargeGunState)) return false
        val soc = data.soc?.takeIf { it in 0..100 } ?: return false
        return mutex.withLock {
            val previousSoc = stateStore.load().socPercent
            if (previousSoc != null && soc > previousSoc) {
                (data.power ?: 0.0) >= 0.0
            } else {
                stateStore.saveParkedAnchor(
                    socPercent = soc,
                    mileageKm = data.mileage?.toFloat(),
                    ts = now,
                )
                false
            }
        }
    }

    suspend fun runCatchUp(
        now: Long = System.currentTimeMillis(),
        observedKwAbs: Double? = null,
    ): CatchUpResult = mutex.withLock {
        _state.value = DetectorState.EVALUATING
        try {
            evaluateCatchUp(now, observedKwAbs)
        } catch (error: Exception) {
            android.util.Log.w(TAG, "runCatchUp failed: ${error.message}", error)
            logJournal(now, "ERROR ${error.message}")
            _state.value = DetectorState.ERROR
            CatchUpResult(CatchUpOutcome.AUTOSERVICE_UNAVAILABLE)
        }
    }

    private suspend fun evaluateCatchUp(now: Long, observedKwAbs: Double?): CatchUpResult {
        val battery = gateway.readBatterySnapshot()
        if (battery == null) {
            logJournal(now, "UNAVAILABLE")
            return finish(CatchUpOutcome.AUTOSERVICE_UNAVAILABLE)
        }
        val charging = gateway.readChargingSnapshot()
        val currentSoc = battery.socPercent?.toInt() ?: run {
            logJournal(now, "SENTINEL")
            return finish(CatchUpOutcome.SENTINEL)
        }
        val previous = stateStore.load()
        if (previous.socPercent == null) {
            stateStore.save(
                socPercent = currentSoc,
                mileageKm = battery.lifetimeMileageKm,
                capacityKwh = charging?.chargingCapacityKwh,
                ts = now,
            )
            logJournal(now, "BASELINE_INITIALIZED soc=$currentSoc")
            return finish(CatchUpOutcome.BASELINE_INITIALIZED)
        }

        val gun = charging?.gunConnectState
        val resolvedGun = gun?.takeIf { it != 0 }
        if (resolvedGun != null) consecutiveGunGlitches = 0
        var gunGlitch = resolvedGun == null && chargingDeviceHasDynamicData(charging)
        if (gunGlitch) {
            consecutiveGunGlitches += 1
            if (consecutiveGunGlitches > MAX_CONSECUTIVE_GUN_GLITCHES) {
                gunGlitch = false
                WjxFileLogger.i(
                    TAG,
                    "gun fid silent $consecutiveGunGlitches times; treating it as unsupported",
                )
            }
        }
        if (ChargeGunState.isConnected(resolvedGun) || gunGlitch) {
            stateStore.setChargePending(true)
            logJournal(
                now,
                "STILL_CHARGING gun=$gun" +
                    if (gunGlitch) " glitch=$consecutiveGunGlitches" else "",
            )
            return finish(CatchUpOutcome.STILL_CHARGING)
        }

        val socDelta = currentSoc - previous.socPercent
        val odometerMoved = previous.mileageKm != null && battery.lifetimeMileageKm != null &&
            battery.lifetimeMileageKm - previous.mileageKm > ODOMETER_MOVED_EPSILON_KM
        val chargePending = stateStore.loadChargePending()
        if (shouldRejectCatchUpCharge(socDelta, odometerMoved, chargePending)) {
            logJournal(
                now,
                "NO_DELTA soc=$currentSoc prev=${previous.socPercent} " +
                    "km=${battery.lifetimeMileageKm} prevKm=${previous.mileageKm} " +
                    "odoMoved=$odometerMoved pending=$chargePending",
            )
            stateStore.save(
                socPercent = currentSoc,
                mileageKm = battery.lifetimeMileageKm,
                capacityKwh = charging?.chargingCapacityKwh,
                ts = now,
            )
            stateStore.setChargePending(false)
            return finish(CatchUpOutcome.NO_DELTA)
        }

        val energy = resolveCatchUpEnergy(
            currentCapacityKwh = charging?.chargingCapacityKwh?.toDouble(),
            previousCapacityKwh = previous.capacityKwh?.toDouble(),
            socDelta = socDelta,
            nominalCapacityKwh = settings.getBatteryCapacity(),
            sohPercent = battery.sohPercent?.toDouble(),
        )
        logEnergyResolution(energy, socDelta)
        val elapsedHours = catchUpElapsedHours(now, previous.ts)
        val type = classifier.fromGunState(charging?.gunConnectState)
            ?: classifier.fromObservedPowerKw(observedKwAbs)
            ?: classifier.heuristicByPower(energy.kwh, elapsedHours)
        val tariff = if (type == "DC") settings.getDcTariff() else settings.getHomeTariff()
        val chargeId = recorder.record(
            CatchUpSessionRequest(
                now = now,
                socStart = previous.socPercent,
                socEnd = currentSoc,
                energy = energy,
                type = type,
                cost = energy.kwh * tariff,
                battery = battery,
                charging = charging,
                latestSample = lastSample,
                sourceSessionKey = catchUpSourceSessionKey(
                    baselineTimestamp = previous.ts,
                    socStart = previous.socPercent,
                    socEnd = currentSoc,
                ),
            ),
        )
        logJournal(
            now,
            "SESSION_CREATED kwh=${"%.2f".format(energy.kwh)} src=${energy.source} " +
                "type=$type soc=${previous.socPercent}->$currentSoc",
        )
        return finish(CatchUpOutcome.SESSION_CREATED, chargeId, energy.kwh)
    }

    private fun logEnergyResolution(energy: CatchUpEnergyResolution, socDelta: Int) {
        WjxFileLogger.i(
            TAG,
            "energy source=${energy.source} kwh=${"%.3f".format(energy.kwh)} " +
                "socDelta=$socDelta bms=${energy.bmsCandidateKwh} ratio=${energy.bmsToSocRatio}",
        )
    }

    private fun finish(
        outcome: CatchUpOutcome,
        chargeId: Long? = null,
        deltaKwh: Double? = null,
    ): CatchUpResult {
        _state.value = DetectorState.IDLE
        return CatchUpResult(outcome, chargeId, deltaKwh)
    }

    private suspend fun logJournal(now: Long, payload: String) {
        runCatching { journal.append(payload, now) }
        WjxFileLogger.i(TAG, "catchUp $payload")
    }

    companion object {
        const val MIN_DELTA_KWH = CATCH_UP_MIN_DELTA_KWH
        const val HEURISTIC_HOURS_FALLBACK = CATCH_UP_HEURISTIC_HOURS_FALLBACK
        const val MIN_ELAPSED_HOURS = CATCH_UP_MIN_ELAPSED_HOURS
        const val MAX_ELAPSED_HOURS = CATCH_UP_MAX_ELAPSED_HOURS
        const val SOC_SANITY_RATIO_LOW = CATCH_UP_SOC_SANITY_RATIO_LOW
        const val SOC_SANITY_RATIO_HIGH = CATCH_UP_SOC_SANITY_RATIO_HIGH
        const val MIN_SOC_DELTA_FOR_SNAPSHOT = CATCH_UP_MIN_SOC_DELTA_FOR_SNAPSHOT
        const val MIN_SOC_DELTA_FOR_CHARGE = CATCH_UP_MIN_SOC_DELTA_FOR_CHARGE
        const val SOC_DELTA_TRUSTED_CHARGE = CATCH_UP_SOC_DELTA_TRUSTED_CHARGE
        const val ODOMETER_MOVED_EPSILON_KM = CATCH_UP_ODOMETER_MOVED_EPSILON_KM
        const val MAX_CONSECUTIVE_GUN_GLITCHES = CATCH_UP_MAX_CONSECUTIVE_GUN_GLITCHES
        const val SOH_SANITY_MIN = CATCH_UP_SOH_SANITY_MIN
        private const val TAG = "AutoserviceDetector"
    }
}

internal fun catchUpSourceSessionKey(
    baselineTimestamp: Long,
    socStart: Int,
    socEnd: Int,
): String = "autoservice:$baselineTimestamp:$socStart:$socEnd"
