package com.byd.wjx.data.nativestack

import com.byd.wjx.data.remote.DiParsData

/**
 * Feature-scoped vehicle reads that must never join the permanent full polling loop.
 * Each method maps to one explicit read profile so UI lifecycle controls its Binder cost.
 */
interface OnDemandVehicleTelemetryReader {
    suspend fun readTireTelemetry(): DiParsData?

    suspend fun readAdasTelemetry(): DiParsData?

    suspend fun readPowertrainTelemetry(): DiParsData?
}
