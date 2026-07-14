package com.byd.wjx.data.vehicle

import com.byd.wjx.data.remote.DiParsData

enum class VehicleSnapshotKind { FULL, ACTION }

/**
 * Parsed vehicle state plus transport diagnostics. Existing consumers continue receiving
 * [DiParsData]; new consumers can use this model to reason about freshness and data quality.
 */
data class VehicleSnapshot(
    val data: DiParsData,
    val kind: VehicleSnapshotKind,
    val source: VehicleReadSource,
    val readStartedAtMs: Long,
    val readCompletedAtMs: Long,
    val requestedFieldCount: Int,
    val transportSuccessCount: Int,
    val decodedFieldCount: Int,
) {
    val latencyMs: Long get() = (readCompletedAtMs - readStartedAtMs).coerceAtLeast(0L)
    val ageMs: Long get() = (System.currentTimeMillis() - readCompletedAtMs).coerceAtLeast(0L)

    companion object {
        fun legacy(data: DiParsData, kind: VehicleSnapshotKind): VehicleSnapshot {
            val now = System.currentTimeMillis()
            return VehicleSnapshot(
                data = data,
                kind = kind,
                source = VehicleReadSource.UNKNOWN,
                readStartedAtMs = now,
                readCompletedAtMs = now,
                requestedFieldCount = 0,
                transportSuccessCount = 0,
                decodedFieldCount = 0,
            )
        }
    }
}
