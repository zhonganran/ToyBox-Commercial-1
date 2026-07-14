package com.byd.wjx.data.charging

import com.byd.wjx.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists the last-known autoservice state snapshot so that runCatchUp can
 * detect SOC / capacity changes across DiLink power-cycles.
 *
 * Replaces ChargingBaselineStore (v2.4.16 and earlier used lifetime_kwh as the
 * baseline signal; v2.4.17 cascade uses SOC + per-session chargingCapacityKwh).
 */
@Singleton
class ChargingStateStore @Inject constructor(
    private val settings: SettingsRepository
) {
    private val mutex = Mutex()
    private var cachedState: State? = null

    data class State(
        val socPercent: Int?,
        val mileageKm: Float?,
        val capacityKwh: Float?,
        val ts: Long
    )

    suspend fun load(): State = mutex.withLock { loadLocked() }

    suspend fun save(socPercent: Int?, mileageKm: Float?, capacityKwh: Float?, ts: Long) =
        mutex.withLock {
            loadLocked()
            persistLocked(socPercent, mileageKm, capacityKwh, ts)
        }

    /**
     * Hot-path anchor update. SOC changes are persisted immediately; flat-SOC driving is
     * checkpointed after meaningful mileage movement or a bounded heartbeat interval.
     */
    suspend fun saveParkedAnchor(
        socPercent: Int,
        mileageKm: Float?,
        ts: Long,
    ) = mutex.withLock {
        val current = loadLocked()
        val mileageMoved = when {
            mileageKm == null && current.mileageKm == null -> false
            mileageKm == null || current.mileageKm == null -> true
            else -> abs(mileageKm - current.mileageKm) >= PARKED_MILEAGE_CHECKPOINT_KM
        }
        val heartbeatDue = ts - current.ts >= PARKED_ANCHOR_HEARTBEAT_MS
        if (current.socPercent != socPercent || mileageMoved || heartbeatDue) {
            persistLocked(
                socPercent = socPercent,
                mileageKm = mileageKm,
                capacityKwh = null,
                ts = ts,
            )
        }
    }

    private suspend fun loadLocked(): State {
        cachedState?.let { return it }
        return State(
            socPercent = settings.getChargingBaselineSoc(),
            mileageKm = settings.getLastMileageKm(),
            capacityKwh = settings.getLastCapacityKwh(),
            ts = settings.getLastStateTs(),
        ).also { cachedState = it }
    }

    private suspend fun persistLocked(
        socPercent: Int?,
        mileageKm: Float?,
        capacityKwh: Float?,
        ts: Long,
    ) {
        // Single transactional write: a process kill mid-save must never leave
        // a mixed anchor (new mileage + stale SOC → false odometerMoved at the
        // next catch-up) — audit 2026-06-11.
        val values = buildMap {
            socPercent?.let { put(SettingsRepository.KEY_CHARGING_BASELINE_SOC, it.toString()) }
            put(SettingsRepository.KEY_LAST_MILEAGE_KM, mileageKm?.toString() ?: "")
            put(SettingsRepository.KEY_LAST_CAPACITY_KWH, capacityKwh?.toString() ?: "")
            put(SettingsRepository.KEY_LAST_STATE_TS, ts.toString())
        }
        settings.setStrings(values)
        cachedState = State(
            socPercent = socPercent ?: cachedState?.socPercent,
            mileageKm = mileageKm,
            capacityKwh = capacityKwh,
            ts = ts,
        )
    }

    /** True when a charge session was observed in progress (gun connected) and
     *  has not yet been reconstructed or dismissed. */
    suspend fun loadChargePending(): Boolean = settings.getChargePending()

    suspend fun setChargePending(pending: Boolean) = settings.setChargePending(pending)

    private companion object {
        const val PARKED_MILEAGE_CHECKPOINT_KM = 0.1f
        const val PARKED_ANCHOR_HEARTBEAT_MS = 15_000L
    }
}
