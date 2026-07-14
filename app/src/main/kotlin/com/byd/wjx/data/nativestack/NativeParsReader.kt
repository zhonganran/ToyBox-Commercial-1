package com.byd.wjx.data.nativestack

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.vehicle.VehicleGateway
import com.byd.wjx.data.vehicle.VehicleReadSpec
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind
import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Parses live vehicle parameters from the unified [VehicleGateway]. Production reads use one
 * helper Binder batch and never issue a duplicate ADB vehicle read.
 *
 * Returns null if the vehicle service is unavailable or all primary FIDs return sentinels.
 */
@Singleton
class NativeParsReader @Inject constructor(
    private val gateway: VehicleGateway,
    private val settings: SettingsRepository,
    private val actionSnapshotFieldProvider: ActionSnapshotFieldProvider,
) : ParsReader {

    override suspend fun fetch(): DiParsData? = fetchVehicleSnapshot()?.data

    override suspend fun fetchAction(): DiParsData? = fetchActionVehicleSnapshot()?.data

    override suspend fun fetchVehicleSnapshot(): VehicleSnapshot? =
        fetchEntries(FidMap.entries, VehicleSnapshotKind.FULL)

    override suspend fun fetchActionVehicleSnapshot(): VehicleSnapshot? {
        val selectedFields = CoreActionFieldNames + actionSnapshotFieldProvider.additionalFields()
        return fetchEntries(actionEntries(selectedFields), VehicleSnapshotKind.ACTION)
    }

    override suspend fun fetchLedVehicleSnapshot(): VehicleSnapshot? =
        fetchEntries(LedFastEntries, VehicleSnapshotKind.ACTION)

    @Volatile private var cachedActionSelection = ActionSelection(emptySet(), emptyList())

    private fun actionEntries(fields: Set<String>): List<FidEntry> {
        cachedActionSelection.takeIf { it.fields == fields }?.let { return it.entries }
        return FidMap.entries.filter { it.field in fields }.also { entries ->
            cachedActionSelection = ActionSelection(fields.toSet(), entries)
            WjxFileLogger.i(
                TAG,
                "action batch selected count=${entries.size} fields=${entries.joinToString(",") { it.field }}",
            )
        }
    }

    private suspend fun fetchEntries(
        entries: List<FidEntry>,
        kind: VehicleSnapshotKind,
    ): VehicleSnapshot? {
        val specs = entries.map { VehicleReadSpec(it.transact, it.device, it.fid) }
        val batch = gateway.readBatch(specs) ?: return null
        if (batch.values.size != entries.size) return null

        // Decoded values keyed by FidEntry.field.
        val decoded = mutableMapOf<String, Any?>()

        entries.forEachIndexed { index, entry ->
            val raw = batch.values[index].takeIf { it.accepted }?.value
            val value: Any? = when (entry.transact) {
                5 -> {
                    if (raw == null) null
                    else when (entry.decoder) {
                        Decoder.INT_SCALED -> ParamDecoder.decodeScaled(raw, entry.scale)
                        else               -> ParamDecoder.decodeInt(raw, entry.decoder)
                    }
                }
                7 -> {
                    if (raw == null) null else ParamDecoder.decodeFloat(raw, entry.decoder)
                }
                else -> null
            }
            decoded[entry.field] = value
        }

        // Action snapshots run every 300 ms and no automation trigger consumes battery capacity.
        // Avoid hitting Room on that hot path; the full snapshot owns display/calculation settings.
        val batteryCapacityKwh = if (kind == VehicleSnapshotKind.FULL) {
            settings.getBatteryCapacity()
        } else {
            null
        }

        // ── Domain-level guards ──────────────────────────────────────────────
        // Cell voltages: autoservice raw is in mV scaled to V via INT_SCALED ÷1000.
        // Filter anything ≤ 0.5 V (BMS not reporting).
        @Suppress("UNCHECKED_CAST")
        fun <T> field(name: String): T? = decoded[name] as? T
        fun intField(name: String): Int? = field<Int>(name)
        fun doubleField(name: String): Double? = field<Double>(name)
        fun rangeKmField(name: String): Int? = intField(name)?.takeIf { it in 0..2000 }
        fun percentField(name: String): Int? = intField(name)?.takeIf { it in 0..100 }
        fun consumptionField(name: String): Double? = doubleField(name)?.takeIf { it in 0.0..100.0 }
        fun tripEnergyField(name: String): Double? = doubleField(name)?.takeIf { it in 0.0..500.0 }
        fun speedField(name: String): Int? = intField(name)?.takeIf { it in 0..260 }

        val maxCellVoltage = field<Double>("maxCellVoltage")?.takeIf { it > 0.5 }
        val minCellVoltage = field<Double>("minCellVoltage")?.takeIf { it > 0.5 }

        // 12V: autoservice path uses FLOAT_VOLT which returns real volts directly —
        // no dual-unit handling needed. Filter ≤ 0.0 as unavailable.
        val voltage12v = field<Double>("voltage12v")?.takeIf { it > 0.0 }

        val soc = doubleField("soc")?.toInt()
        val mileage = doubleField("mileage")

        // Full snapshots require one stable primary signal. Action snapshots are intentionally
        // sparse and vehicle-dependent, so any decoded field proves that the helper batch is live.
        val hasLiveData = when (kind) {
            VehicleSnapshotKind.FULL -> soc != null || mileage != null || voltage12v != null
            VehicleSnapshotKind.ACTION -> decoded.values.any { it != null }
        }
        if (!hasLiveData) {
            return null
        }

        // Battery temps decode with a -40 offset via INT_TEMP_C_OFS40. avgBatTemp
        // was dropped on the native cutover; restore it as the mean of the two live
        // cell extremes (the dedicated avg fid is a suspected 24h-cached statistic).
        val maxBatTemp = field<Int>("maxBatTemp")
        val minBatTemp = field<Int>("minBatTemp")
        val avgBatTemp = when {
            maxBatTemp != null && minBatTemp != null -> ((maxBatTemp + minBatTemp) / 2.0).roundToInt()
            else -> maxBatTemp ?: minBatTemp
        }

        val data = DiParsData(
            soc                 = soc,
            speed               = doubleField("speed")?.toInt(),
            mileage             = mileage,
            power               = intField("power")?.toDouble(),
            chargeGunState      = intField("chargeGunState"),
            maxBatTemp          = maxBatTemp,
            avgBatTemp          = avgBatTemp,
            minBatTemp          = minBatTemp,
            chargingStatus      = intField("chargingStatus"),
            batteryCapacityKwh  = batteryCapacityKwh,
            totalElecConsumption = doubleField("totalElecConsumption"),
            voltage12v          = voltage12v,
            maxCellVoltage      = maxCellVoltage,
            minCellVoltage      = minCellVoltage,
            exteriorTemp        = intField("exteriorTemp"),
            gear                = intField("gear"),
            powerState          = intField("powerState"),
            insideTemp          = intField("insideTemp"),
            acStatus            = intField("acStatus"),
            acTemp              = intField("acTemp"),
            fanLevel            = intField("fanLevel"),
            acCirc              = intField("acCirc"),
            doorFL              = intField("doorFL"),
            doorFR              = intField("doorFR"),
            doorRL              = intField("doorRL"),
            doorRR              = intField("doorRR"),
            windowFL            = percentField("windowFL"),
            windowFR            = percentField("windowFR"),
            windowRL            = percentField("windowRL"),
            windowRR            = percentField("windowRR"),
            sunroof             = null,  // removed from FidMap: fid returns enum(1=open,2=closed,5=vent) not percent
            trunk               = intField("trunk"),
            hood                = intField("hood"),
            seatbeltFL          = intField("seatbeltFL"),
            lockFL              = intField("lockFL"),
            tirePressFL         = intField("tirePressFL"),
            tirePressFR         = intField("tirePressFR"),
            tirePressRL         = intField("tirePressRL"),
            tirePressRR         = intField("tirePressRR"),
            driveMode           = intField("driveMode"),
            workMode            = intField("workMode"),
            autoPark            = null,  // not in FidMap (unknown source)
            rain                = null,  // not in FidMap (user setting, no live sensor fid)
            lightLow            = intField("lightLow"),
            drl                 = intField("drl"),
            adasApaParkingStatus = intField("adasApaParkingStatus"),
            fuelLevel           = percentField("fuelLevel"),
            elecDrivingRangeKm  = rangeKmField("elecDrivingRangeKm"),
            fuelDrivingRangeKm  = rangeKmField("fuelDrivingRangeKm"),
            elecConPer100km     = consumptionField("elecConPer100km"),
            fuelConPer100km     = consumptionField("fuelConPer100km"),
            currentTripElecKwh  = tripEnergyField("currentTripElecKwh"),
            currentTripFuelL    = tripEnergyField("currentTripFuelL"),
            chargingCapCover    = intField("chargingCapCover"),
            fuelCap             = intField("fuelCap"),
            seatbeltFR          = intField("seatbeltFR"),
            seatbeltRL          = intField("seatbeltRL"),
            seatbeltRR          = intField("seatbeltRR"),
            lockFR              = intField("lockFR"),
            lockRL              = intField("lockRL"),
            lockRR              = intField("lockRR"),
            lightHigh           = intField("lightHigh"),
            hazardLights        = intField("hazardLights"),
            turnSignalFlash     = intField("turnSignalFlash"),
            adasAccMode         = intField("adasAccMode"),
            adasLccNcaStatus    = intField("adasLccNcaStatus"),
            adasTjaState        = intField("adasTjaState"),
            laneKeepState       = intField("laneKeepState"),
            accCruiseState      = intField("accCruiseState"),
            accCruisingSpeed    = speedField("accCruisingSpeed"),
            brakePedalDepth     = percentField("brakePedalDepth"),
            turnSignalLeft      = intField("turnSignalLeft"),
            turnSignalRight     = intField("turnSignalRight"),
        )
        return VehicleSnapshot(
            data = data,
            kind = kind,
            source = batch.source,
            readStartedAtMs = batch.startedAtMs,
            readCompletedAtMs = System.currentTimeMillis(),
            requestedFieldCount = entries.size,
            transportSuccessCount = batch.acceptedCount,
            decodedFieldCount = decoded.values.count { it != null },
        )
    }

    private companion object {
        private const val TAG = "NativeParsReader"
        private val CoreActionFieldNames = setOf(
            "soc",
            "speed",
            "gear",
            "chargeGunState",
            "powerState",
            "adasApaParkingStatus",
        )
        private val LedFastFieldNames = listOf(
            "turnSignalLeft",
            "turnSignalRight",
            "hazardLights",
            "gear",
            "brakePedalDepth",
        )
        private val LedFastEntries = FidMap.entries.filter { it.field in LedFastFieldNames }
    }

    private data class ActionSelection(
        val fields: Set<String>,
        val entries: List<FidEntry>,
    )
}
