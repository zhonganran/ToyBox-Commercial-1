package com.byd.wjx.data.local

data class BydTripRecord(
    val id: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val duration: Long,
    val tripKm: Double,
    val electricityKwh: Double,
)

internal data class EnergyDatabaseColumn(val name: String, val type: String)

internal data class EnergyDiagnosticSample(
    val record: BydTripRecord,
    val deleted: Int,
)

internal data class EnergyDatabaseInspection(
    val tables: List<String>,
    val columns: List<EnergyDatabaseColumn>,
    val totalCount: Int,
    val activeCount: Int,
    val samples: List<EnergyDiagnosticSample>,
)

/** Thrown when BYD energy data is unavailable or unreadable. */
class EnergyDataException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
