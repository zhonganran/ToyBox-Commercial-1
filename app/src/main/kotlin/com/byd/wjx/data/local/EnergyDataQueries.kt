package com.byd.wjx.data.local

import android.database.Cursor

internal object EnergyDataQueries {
    const val TABLE = "EnergyConsumption"
    const val TABLES = "SELECT name FROM sqlite_master WHERE type='table'"
    const val COLUMNS = "PRAGMA table_info(EnergyConsumption)"
    const val TOTAL_COUNT = "SELECT COUNT(*) FROM EnergyConsumption"
    const val ACTIVE_COUNT = "SELECT COUNT(*) FROM EnergyConsumption WHERE is_deleted = 0"
    const val ALL_TRIPS = """SELECT _id, start_timestamp, end_timestamp, duration, trip, electricity
        FROM EnergyConsumption
        WHERE is_deleted = 0
        ORDER BY start_timestamp DESC"""
    const val TRIPS_SINCE = """SELECT _id, start_timestamp, end_timestamp, duration, trip, electricity
        FROM EnergyConsumption
        WHERE is_deleted = 0 AND start_timestamp > ?
        ORDER BY start_timestamp"""
    const val DIAGNOSTIC_SAMPLES = """SELECT _id, start_timestamp, end_timestamp, duration, trip,
        electricity, is_deleted
        FROM EnergyConsumption
        ORDER BY start_timestamp DESC
        LIMIT 5"""
}

internal fun Cursor.readBydTripRecord(): BydTripRecord = BydTripRecord(
    id = getLong(getColumnIndexOrThrow("_id")),
    startTimestamp = getLong(getColumnIndexOrThrow("start_timestamp")),
    endTimestamp = getLong(getColumnIndexOrThrow("end_timestamp")),
    duration = getLong(getColumnIndexOrThrow("duration")),
    tripKm = getDouble(getColumnIndexOrThrow("trip")),
    electricityKwh = getDouble(getColumnIndexOrThrow("electricity")),
)
