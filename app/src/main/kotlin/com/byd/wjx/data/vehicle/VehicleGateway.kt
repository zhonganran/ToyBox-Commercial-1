package com.byd.wjx.data.vehicle

import com.byd.wjx.data.autoservice.BatteryReading
import com.byd.wjx.data.autoservice.ChargingReading
import com.byd.wjx.data.autoservice.FidRegistry
import com.byd.wjx.data.autoservice.SentinelDecoder
import com.byd.wjx.data.charging.ChargingTelemetrySemantics
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton

/** Raw autoservice address. [transaction] is normally getInt=5 or getFloat=7. */
data class VehicleReadSpec(
    val transaction: Int,
    val device: Int,
    val fid: Int,
)

/** Raw autoservice reply. A read is valid only when [status] is zero. */
data class VehicleRawRead(
    val status: Int,
    val value: Int,
) {
    val accepted: Boolean get() = status == 0
}

enum class VehicleReadSource {
    HELPER_BINDER,
    UNKNOWN,
}

/** One ordered batch. [values] always has the same size and order as [specs]. */
data class VehicleBatchRead(
    val specs: List<VehicleReadSpec>,
    val values: List<VehicleRawRead>,
    val source: VehicleReadSource,
    val startedAtMs: Long,
    val completedAtMs: Long,
) {
    init {
        require(specs.size == values.size) {
            "Batch result size mismatch specs=${specs.size} values=${values.size}"
        }
    }

    val latencyMs: Long get() = (completedAtMs - startedAtMs).coerceAtLeast(0L)
    val acceptedCount: Int get() = values.count { it.accepted }
}

/**
 * Single low-level entry point for vehicle reads and writes.
 *
 * Production reads use the shell-uid helper's direct Binder connection to autoservice.
 */
interface VehicleGateway {
    suspend fun readBatch(specs: List<VehicleReadSpec>): VehicleBatchRead?
    suspend fun writeStatus(device: Int, fid: Int, value: Int): Int?

    suspend fun readRaw(spec: VehicleReadSpec): VehicleRawRead? =
        readBatch(listOf(spec))?.values?.singleOrNull()?.takeIf { it.accepted }

    suspend fun readInt(device: Int, fid: Int): Int? =
        readRaw(VehicleReadSpec(FidRegistry.TX_GET_INT, device, fid))
            ?.value
            ?.let(SentinelDecoder::decodeInt)

    suspend fun readFloat(device: Int, fid: Int): Float? =
        readRaw(VehicleReadSpec(FidRegistry.TX_GET_FLOAT, device, fid))
            ?.value
            ?.let(SentinelDecoder::parseFloatFromShellInt)

    suspend fun isAvailable(): Boolean {
        val batch = readBatch(AVAILABILITY_PROBES) ?: return false
        return batch.specs.indices.any { index ->
            val raw = batch.values[index].takeIf { it.accepted }?.value ?: return@any false
            when (batch.specs[index].transaction) {
                FidRegistry.TX_GET_INT -> SentinelDecoder.decodeInt(raw) != null
                FidRegistry.TX_GET_FLOAT -> SentinelDecoder.parseFloatFromShellInt(raw) != null
                else -> false
            }
        }
    }

    suspend fun readBatterySnapshot(): BatteryReading? {
        val specs = listOf(
            VehicleReadSpec(FidRegistry.TX_GET_INT, FidRegistry.DEV_STATISTIC, FidRegistry.FID_SOH),
            VehicleReadSpec(FidRegistry.TX_GET_FLOAT, FidRegistry.DEV_STATISTIC, FidRegistry.FID_SOC),
            VehicleReadSpec(FidRegistry.TX_GET_FLOAT, FidRegistry.DEV_STATISTIC, FidRegistry.FID_LIFETIME_KWH),
            VehicleReadSpec(FidRegistry.TX_GET_INT, FidRegistry.DEV_STATISTIC, FidRegistry.FID_LIFETIME_MILEAGE),
            VehicleReadSpec(FidRegistry.TX_GET_FLOAT, FidRegistry.DEV_BODYWORK, FidRegistry.FID_OTA_BATTERY_POWER_VOLTAGE),
        )
        val batch = readBatch(specs) ?: return null
        fun intAt(index: Int): Int? = batch.values[index].takeIf { it.accepted }
            ?.value?.let(SentinelDecoder::decodeInt)
        fun floatAt(index: Int): Float? = batch.values[index].takeIf { it.accepted }
            ?.value?.let(SentinelDecoder::parseFloatFromShellInt)
        return BatteryReading(
            sohPercent = intAt(0)?.toFloat(),
            socPercent = floatAt(1),
            lifetimeKwh = floatAt(2),
            lifetimeMileageKm = intAt(3)?.let { it / 10f },
            voltage12v = floatAt(4),
            readAtMs = batch.completedAtMs,
        )
    }

    suspend fun readChargingSnapshot(
        includeElectricalTelemetry: Boolean = false,
    ): ChargingReading? {
        val specs = listOf(
            VehicleReadSpec(FidRegistry.TX_GET_INT, FidRegistry.DEV_CHARGING, FidRegistry.FID_GUN_CONNECT_STATE),
            VehicleReadSpec(FidRegistry.TX_GET_INT, FidRegistry.DEV_CHARGING, FidRegistry.FID_CHARGING_TYPE),
            VehicleReadSpec(FidRegistry.TX_GET_INT, FidRegistry.DEV_CHARGING, FidRegistry.FID_CHARGE_BATTERY_VOLT),
            VehicleReadSpec(FidRegistry.TX_GET_INT, FidRegistry.DEV_CHARGING, FidRegistry.FID_BATTERY_TYPE),
            VehicleReadSpec(FidRegistry.TX_GET_FLOAT, FidRegistry.DEV_CHARGING, FidRegistry.FID_CHARGING_CAPACITY),
            VehicleReadSpec(FidRegistry.TX_GET_INT, FidRegistry.DEV_CHARGING, FidRegistry.FID_CHARGING_BMS_STATE),
        )
        val batch = readBatch(specs) ?: return null
        fun intAt(source: VehicleBatchRead, index: Int): Int? =
            source.values[index].takeIf { it.accepted }
            ?.value?.let(SentinelDecoder::decodeInt)
        fun floatAt(source: VehicleBatchRead, index: Int): Float? =
            source.values[index].takeIf { it.accepted }
            ?.value?.let(SentinelDecoder::parseFloatFromShellInt)

        val gunConnectState = intAt(batch, 0)
        val chargingType = intAt(batch, 1)
        val chargeBatteryVoltV = intAt(batch, 2)
        val batteryType = intAt(batch, 3)
        val chargingCapacityKwh = floatAt(batch, 4)
        val bmsState = intAt(batch, 5)

        var chargeCurrentA: Int? = null
        var chargePowerKw: Float? = null
        var batteryMaxChargePowerKw: Int? = null
        var batteryMaxDischargePowerKw: Int? = null
        var batteryInsulationKohm: Int? = null
        var chargeStopSoc: Int? = null
        var readAtMs = batch.completedAtMs
        if (
            includeElectricalTelemetry &&
            ChargingTelemetrySemantics.isChargingSession(gunConnectState, bmsState)
        ) {
            val electricalSpecs = listOf(
                VehicleReadSpec(
                    FidRegistry.TX_GET_INT,
                    FidRegistry.DEV_CHARGING,
                    FidRegistry.FID_CHARGE_CURRENT,
                ),
                VehicleReadSpec(
                    FidRegistry.TX_GET_FLOAT,
                    FidRegistry.DEV_CHARGING,
                    FidRegistry.FID_CHARGE_POWER,
                ),
                VehicleReadSpec(
                    FidRegistry.TX_GET_INT,
                    FidRegistry.DEV_STATISTIC,
                    FidRegistry.FID_BATTERY_MAX_CHARGE_POWER,
                ),
                VehicleReadSpec(
                    FidRegistry.TX_GET_INT,
                    FidRegistry.DEV_STATISTIC,
                    FidRegistry.FID_BATTERY_MAX_DISCHARGE_POWER,
                ),
                VehicleReadSpec(
                    FidRegistry.TX_GET_INT,
                    FidRegistry.DEV_BATTERY_MGMT,
                    FidRegistry.FID_BATTERY_INSULATION,
                ),
                VehicleReadSpec(
                    FidRegistry.TX_GET_INT,
                    FidRegistry.DEV_CHARGING,
                    FidRegistry.FID_CHARGE_STOP_SOC,
                ),
            )
            readBatch(electricalSpecs)?.let { electrical ->
                chargeCurrentA = intAt(electrical, 0)
                chargePowerKw = floatAt(electrical, 1)
                batteryMaxChargePowerKw = intAt(electrical, 2)
                batteryMaxDischargePowerKw = intAt(electrical, 3)
                batteryInsulationKohm = intAt(electrical, 4)
                chargeStopSoc = intAt(electrical, 5)?.takeIf { it in 0..100 }
                readAtMs = maxOf(readAtMs, electrical.completedAtMs)
            }
        }
        return ChargingReading(
            gunConnectState = gunConnectState,
            chargingType = chargingType,
            chargeBatteryVoltV = chargeBatteryVoltV,
            batteryType = batteryType,
            chargingCapacityKwh = chargingCapacityKwh,
            bmsState = bmsState,
            readAtMs = readAtMs,
            chargeCurrentA = chargeCurrentA,
            chargePowerKw = chargePowerKw,
            batteryMaxChargePowerKw = batteryMaxChargePowerKw,
            batteryMaxDischargePowerKw = batteryMaxDischargePowerKw,
            batteryInsulationKohm = batteryInsulationKohm,
            chargeStopSoc = chargeStopSoc,
        )
    }

    suspend fun getEnginePowerKw(): Int? =
        readInt(FidRegistry.DEV_ENGINE, FidRegistry.FID_ENGINE_POWER)

    companion object {
        val AVAILABILITY_PROBES = listOf(
            VehicleReadSpec(FidRegistry.TX_GET_INT, FidRegistry.DEV_STATISTIC, FidRegistry.FID_SOH),
            VehicleReadSpec(FidRegistry.TX_GET_FLOAT, FidRegistry.DEV_STATISTIC, FidRegistry.FID_LIFETIME_KWH),
            VehicleReadSpec(FidRegistry.TX_GET_FLOAT, FidRegistry.DEV_STATISTIC, FidRegistry.FID_SOC),
        )
    }
}

@Singleton
class HelperVehicleGateway @Inject constructor(
    private val helper: HelperClient,
) : VehicleGateway {
    @Volatile private var lastAvailable: Boolean? = null

    override suspend fun readBatch(specs: List<VehicleReadSpec>): VehicleBatchRead? {
        if (specs.isEmpty()) {
            val now = System.currentTimeMillis()
            return VehicleBatchRead(emptyList(), emptyList(), VehicleReadSource.HELPER_BINDER, now, now)
        }

        val helperStarted = System.currentTimeMillis()
        val helperValues = helper.readBatch(specs)
        if (helperValues != null && helperValues.size == specs.size) {
            val result = VehicleBatchRead(
                specs = specs,
                values = helperValues,
                source = VehicleReadSource.HELPER_BINDER,
                startedAtMs = helperStarted,
                completedAtMs = System.currentTimeMillis(),
            )
            noteAvailable(result)
            return result
        }
        noteUnavailable(specs.size)
        return null
    }

    override suspend fun writeStatus(device: Int, fid: Int, value: Int): Int? =
        helper.writeStatus(device, fid, value)

    private fun noteAvailable(batch: VehicleBatchRead) {
        if (lastAvailable != true) {
            lastAvailable = true
            WjxFileLogger.i(
                TAG,
                "helper vehicle reads available count=${batch.specs.size} " +
                    "accepted=${batch.acceptedCount} latency=${batch.latencyMs}ms",
            )
        }
    }

    private fun noteUnavailable(count: Int) {
        if (lastAvailable != false) {
            lastAvailable = false
            WjxFileLogger.w(TAG, "helper vehicle reads unavailable count=$count")
        }
    }

    private companion object {
        const val TAG = "VehicleGateway"
    }
}
