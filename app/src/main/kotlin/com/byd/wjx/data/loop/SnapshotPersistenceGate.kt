package com.byd.wjx.data.loop

import kotlin.math.abs

/**
 * Reduces redundant writes to the single-row last_state table without delaying meaningful
 * vehicle-state edges. The persisted row is a crash-recovery checkpoint, not a per-tick log.
 */
internal class SnapshotPersistenceGate(
    private val maxIntervalMs: Long = DEFAULT_MAX_INTERVAL_MS,
    private val mileageDeltaKm: Double = DEFAULT_MILEAGE_DELTA_KM,
    private val totalEnergyDeltaKwh: Double = DEFAULT_TOTAL_ENERGY_DELTA_KWH,
) {
    private var lastPersisted: SnapshotPersistenceSample? = null
    private var lastPersistedAtMs: Long = 0L

    fun shouldPersist(sample: SnapshotPersistenceSample, nowMs: Long): Boolean {
        val previous = lastPersisted ?: return true
        if (nowMs < lastPersistedAtMs) return true
        if (nowMs - lastPersistedAtMs >= maxIntervalMs) return true
        if (sample.soc != previous.soc) return true
        if (sample.ignition != previous.ignition) return true
        if (sample.energyDataAvailable != previous.energyDataAvailable) return true
        if (changedBy(sample.mileageKm, previous.mileageKm, mileageDeltaKm)) return true
        if (changedBy(sample.totalEnergyKwh, previous.totalEnergyKwh, totalEnergyDeltaKwh)) return true
        return false
    }

    fun markPersisted(sample: SnapshotPersistenceSample, nowMs: Long) {
        lastPersisted = sample
        lastPersistedAtMs = nowMs
    }

    private fun changedBy(current: Double?, previous: Double?, threshold: Double): Boolean {
        if (current == null || previous == null) return current != previous
        if (!current.isFinite() || !previous.isFinite()) return current != previous
        return abs(current - previous) + DOUBLE_COMPARISON_EPSILON >= threshold
    }

    private companion object {
        const val DEFAULT_MAX_INTERVAL_MS = 5_000L
        const val DEFAULT_MILEAGE_DELTA_KM = 0.1
        const val DEFAULT_TOTAL_ENERGY_DELTA_KWH = 0.05
        const val DOUBLE_COMPARISON_EPSILON = 1e-9
    }
}

internal data class SnapshotPersistenceSample(
    val soc: Int?,
    val mileageKm: Double?,
    val totalEnergyKwh: Double?,
    val ignition: Int?,
    val energyDataAvailable: Boolean,
)
