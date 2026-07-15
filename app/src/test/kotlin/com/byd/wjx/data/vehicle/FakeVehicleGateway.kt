package com.byd.wjx.data.vehicle

import com.byd.wjx.data.autoservice.BatteryReading
import com.byd.wjx.data.autoservice.ChargingReading
import com.byd.wjx.data.autoservice.FidRegistry

/** Mutable gateway fixture used by parser and charging tests. */
open class FakeVehicleGateway(
    var battery: BatteryReading? = null,
    var charging: ChargingReading? = null,
    var available: Boolean = true,
    val intValues: MutableMap<Pair<Int, Int>, Int> = mutableMapOf(),
    val floatValues: MutableMap<Pair<Int, Int>, Float> = mutableMapOf(),
    var enginePowerKw: Int? = null,
) : VehicleGateway {

    var lastBatchSpecs: List<VehicleReadSpec> = emptyList()
    val batchHistory: MutableList<List<VehicleReadSpec>> = mutableListOf()

    override suspend fun readBatch(specs: List<VehicleReadSpec>): VehicleBatchRead? {
        if (!available) return null
        lastBatchSpecs = specs
        batchHistory += specs
        val startedAtMs = System.currentTimeMillis()
        val values = specs.map { spec ->
            val rawValue = when (spec.transaction) {
                FidRegistry.TX_GET_INT -> intValues[spec.device to spec.fid]
                FidRegistry.TX_GET_FLOAT -> floatValues[spec.device to spec.fid]
                    ?.let(java.lang.Float::floatToRawIntBits)
                else -> null
            }
            rawValue?.let { VehicleRawRead(status = 0, value = it) }
                ?: VehicleRawRead(status = -1, value = 0)
        }
        return VehicleBatchRead(
            specs = specs,
            values = values,
            source = VehicleReadSource.UNKNOWN,
            startedAtMs = startedAtMs,
            completedAtMs = System.currentTimeMillis(),
        )
    }

    override suspend fun writeStatus(device: Int, fid: Int, value: Int): Int? = null

    override suspend fun isAvailable(): Boolean = available

    override suspend fun readBatterySnapshot(): BatteryReading? = battery.takeIf { available }

    override suspend fun readChargingSnapshot(
        includeElectricalTelemetry: Boolean,
    ): ChargingReading? = charging.takeIf { available }

    override suspend fun getEnginePowerKw(): Int? = enginePowerKw.takeIf { available }
}
