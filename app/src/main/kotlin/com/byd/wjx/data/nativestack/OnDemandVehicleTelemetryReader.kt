package com.byd.wjx.data.nativestack

import com.byd.wjx.data.remote.DiParsData

/**
 * Feature-scoped vehicle reads that must never join the permanent full polling loop.
 * Each method maps to one explicit read profile so UI lifecycle controls its Binder cost.
 */
interface OnDemandVehicleTelemetryReader {
    suspend fun readTireTelemetry(): DiParsData?

    suspend fun readPowertrainTelemetry(): DiParsData?

    /** Reads only the requested powertrain fields for lightweight always-on surfaces. */
    suspend fun readPowertrainTelemetry(fields: Set<String>): DiParsData? = readPowertrainTelemetry()
}
