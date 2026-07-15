package com.byd.wjx.data.charging

import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.autoservice.ChargingReading
import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.data.vehicle.VehicleGateway
import javax.inject.Inject
import javax.inject.Singleton

object ChargingTelemetrySemantics {
    fun isChargingSession(gunConnectState: Int?, bmsState: Int?): Boolean =
        gunConnectState in ChargeGunState.AC..ChargeGunState.AC_DC ||
            BydSignalSemantics.isCharging(bmsState)

    fun isChargingSession(reading: ChargingReading?): Boolean = reading != null &&
        isChargingSession(reading.gunConnectState, reading.bmsState)
}

interface ChargingTelemetryReader {
    suspend fun read(): ChargingReading?
}

/** Screen-scoped caller: electrical FIDs are requested only while the charging page is visible. */
@Singleton
class AutoserviceChargingTelemetryReader @Inject constructor(
    private val gateway: VehicleGateway,
) : ChargingTelemetryReader {
    override suspend fun read(): ChargingReading? =
        gateway.readChargingSnapshot(includeElectricalTelemetry = true)
}
