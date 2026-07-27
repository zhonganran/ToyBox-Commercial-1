package com.byd.wjx.data.nativestack

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.vehicle.VehicleBatchRead
import com.byd.wjx.data.vehicle.VehicleGateway
import com.byd.wjx.data.vehicle.VehicleReadSpec
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Parses live vehicle parameters from the unified [VehicleGateway]. Production reads use bounded
 * helper Binder batches and never issue a duplicate ADB vehicle read. The first full snapshot
 * loads the complete regular catalog; feature telemetry remains in explicit page/action profiles.
 * Later driving ticks refresh the lifecycle-critical core every time and reuse detail values for
 * at most a short interval.
 *
 * Returns null if the vehicle service is unavailable or all primary FIDs return sentinels.
 */
@Singleton
class NativeParsReader @Inject constructor(
    private val gateway: VehicleGateway,
    private val settings: SettingsRepository,
    private val actionSnapshotFieldProvider: ActionSnapshotFieldProvider,
) : ParsReader, OnDemandVehicleTelemetryReader {

    override suspend fun fetchVehicleSnapshot(): VehicleSnapshot? = fullReadMutex.withLock {
        fetchTieredFullSnapshot()
    }

    override suspend fun fetchActionVehicleSnapshot(): VehicleSnapshot? {
        val requestedFields = VehicleReadProfiles.actionCoreFields +
            actionSnapshotFieldProvider.additionalFields()
        val selectedFields = buildSet {
            addAll(requestedFields)
            if ("driveMode" in requestedFields) add("driveModeIndicator")
            if ("workMode" in requestedFields) add("workModeAlt")
        }
        return fetchPlan(actionPlan(selectedFields), VehicleSnapshotKind.ACTION)
    }

    override suspend fun fetchLedMotionVehicleSnapshot(): VehicleSnapshot? =
        fetchPlan(LedMotionReadPlan, VehicleSnapshotKind.ACTION)

    override suspend fun fetchLedBrakeVehicleSnapshot(): VehicleSnapshot? =
        fetchPlan(LedBrakeReadPlan, VehicleSnapshotKind.ACTION)

    override suspend fun fetchLedBodyVehicleSnapshot(): VehicleSnapshot? =
        fetchPlan(LedBodyReadPlan, VehicleSnapshotKind.ACTION)

    override suspend fun readTireTelemetry(): DiParsData? =
        fetchPlan(TireTelemetryReadPlan, VehicleSnapshotKind.ACTION)?.data

    override suspend fun readPowertrainTelemetry(): DiParsData? =
        fetchPlan(PowertrainTelemetryReadPlan, VehicleSnapshotKind.ACTION)?.data

    override suspend fun readPowertrainTelemetry(fields: Set<String>): DiParsData? {
        val selectedFields = fields.intersect(VehicleReadProfiles.powertrainTelemetryFields)
        if (selectedFields.isEmpty()) return null
        val normalizedFields = buildSet {
            addAll(selectedFields)
            if ("frontMotorSpeed" in selectedFields || "rearMotorSpeed" in selectedFields) {
                add("gear")
            }
        }
        return fetchPlan(instrumentPlan(normalizedFields), VehicleSnapshotKind.ACTION)?.data
    }

    @Volatile private var cachedActionSelection = ActionSelection(emptySet(), ReadPlan(emptyList()))
    @Volatile private var cachedInstrumentSelection = ActionSelection(emptySet(), ReadPlan(emptyList()))
    private val fullReadMutex = Mutex()
    private val fullRawCache = HashMap<VehicleReadSpec, com.byd.wjx.data.vehicle.VehicleRawRead>()
    private var fullCacheInitialized = false
    private var lastDetailAttemptAtNanos = 0L
    @Volatile private var workModeConflictLogged = false
    internal var monotonicNanos: () -> Long = System::nanoTime

    private suspend fun fetchTieredFullSnapshot(): VehicleSnapshot? {
        val nowNanos = monotonicNanos()
        if (!fullCacheInitialized) {
            val batch = gateway.readBatch(FullReadPlan.specs) ?: return null
            cacheBatch(batch)
            val snapshot = parsePlan(FullReadPlan, VehicleSnapshotKind.FULL, batch)
            if (snapshot != null) {
                fullCacheInitialized = true
                lastDetailAttemptAtNanos = nowNanos
            }
            return snapshot
        }

        val coreBatch = gateway.readBatch(CoreFullReadPlan.specs) ?: return null
        cacheBatch(coreBatch)

        var requestedCount = coreBatch.specs.size
        var acceptedCount = coreBatch.acceptedCount
        var readStartedAtMs = coreBatch.startedAtMs
        var readCompletedAtMs = coreBatch.completedAtMs

        if (detailRefreshDue(nowNanos)) {
            lastDetailAttemptAtNanos = nowNanos
            val detailBatch = gateway.readBatch(DetailFullReadPlan.specs)
            requestedCount += DetailFullReadPlan.specs.size
            if (detailBatch != null) {
                cacheBatch(detailBatch)
                acceptedCount += detailBatch.acceptedCount
                readStartedAtMs = minOf(readStartedAtMs, detailBatch.startedAtMs)
                readCompletedAtMs = maxOf(readCompletedAtMs, detailBatch.completedAtMs)
            } else {
                // Do not present indefinitely stale detail values after a failed refresh.
                DetailFullReadPlan.specs.forEach { spec ->
                    fullRawCache[spec] = UNAVAILABLE_RAW_READ
                }
            }
        }

        val mergedBatch = VehicleBatchRead(
            specs = FullReadPlan.specs,
            values = FullReadPlan.specs.map { spec ->
                fullRawCache[spec] ?: UNAVAILABLE_RAW_READ
            },
            source = coreBatch.source,
            startedAtMs = readStartedAtMs,
            completedAtMs = readCompletedAtMs,
        )
        return parsePlan(
            plan = FullReadPlan,
            kind = VehicleSnapshotKind.FULL,
            batch = mergedBatch,
            requestedFieldCount = requestedCount,
            transportSuccessCount = acceptedCount,
        )
    }

    private fun detailRefreshDue(nowNanos: Long): Boolean {
        val elapsed = nowNanos - lastDetailAttemptAtNanos
        return elapsed < 0L || elapsed >= DETAIL_REFRESH_INTERVAL_NANOS
    }

    private fun cacheBatch(batch: VehicleBatchRead) {
        batch.specs.forEachIndexed { index, spec ->
            fullRawCache[spec] = batch.values[index]
        }
    }

    private fun actionPlan(fields: Set<String>): ReadPlan {
        cachedActionSelection.takeIf { it.fields == fields }?.let { return it.plan }
        val entries = FidMap.entries.filter { it.field in fields }
        val plan = ReadPlan(entries)
        cachedActionSelection = ActionSelection(fields.toSet(), plan)
        WjxFileLogger.i(
            TAG,
            "action batch selected count=${entries.size} fields=${entries.joinToString(",") { it.field }}",
        )
        return plan
    }

    private fun instrumentPlan(fields: Set<String>): ReadPlan {
        cachedInstrumentSelection.takeIf { it.fields == fields }?.let { return it.plan }
        val entries = FidMap.entries.filter { it.field in fields }
        val plan = ReadPlan(entries)
        cachedInstrumentSelection = ActionSelection(fields.toSet(), plan)
        WjxFileLogger.i(
            TAG,
            "instrument batch selected count=${entries.size} fields=${entries.joinToString(",") { it.field }}",
        )
        return plan
    }

    private suspend fun fetchPlan(
        plan: ReadPlan,
        kind: VehicleSnapshotKind,
    ): VehicleSnapshot? {
        val batch = gateway.readBatch(plan.specs) ?: return null
        return parsePlan(plan, kind, batch)
    }

    private suspend fun parsePlan(
        plan: ReadPlan,
        kind: VehicleSnapshotKind,
        batch: VehicleBatchRead,
        requestedFieldCount: Int = batch.specs.size,
        transportSuccessCount: Int = batch.acceptedCount,
    ): VehicleSnapshot? {
        val entries = plan.entries
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

        // Action snapshots are the adaptive fast path and no automation trigger consumes battery
        // capacity. Avoid hitting Room there; the full snapshot owns display/calculation settings.
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
        fun percentField(name: String): Int? = intField(name)?.takeIf { it in 0..100 }
        fun consumptionField(name: String): Double? = doubleField(name)?.takeIf { it in 0.0..100.0 }
        fun tripEnergyField(name: String): Double? = doubleField(name)?.takeIf { it in 0.0..500.0 }
        fun speedField(name: String): Int? = intField(name)?.takeIf { it in 0..260 }
        fun engineRpmField(): Int? = intField("engineRpm")?.let { raw ->
            raw.takeIf { it in 1..MAX_ENGINE_RPM } ?: 0
        }
        fun motorRpmField(name: String, gear: Int?): Int? = intField(name)?.let { raw ->
            if (gear == GEAR_PARK || gear == GEAR_NEUTRAL) return@let 0
            raw.takeIf { it in -MAX_MOTOR_RPM..MAX_MOTOR_RPM }
                ?.let { if (it in -1..1) 0 else abs(it) }
        }
        fun motorCurrentField(name: String): Double? =
            doubleField(name)?.takeIf { it in -2_000.0..2_000.0 }

        val maxCellVoltage = field<Double>("maxCellVoltage")?.takeIf { it > 0.5 }
        val minCellVoltage = field<Double>("minCellVoltage")?.takeIf { it > 0.5 }

        // 12V: autoservice path uses FLOAT_VOLT which returns real volts directly —
        // no dual-unit handling needed. Filter ≤ 0.0 as unavailable.
        val voltage12v = field<Double>("voltage12v")?.takeIf { it > 0.0 }

        val soc = doubleField("soc")?.toInt()
        val mileage = doubleField("mileage")
        val gear = intField("gear")
        val driveMode = BydSignalSemantics.selectDriveMode(
            baseMode = intField("driveMode"),
            indicatorMode = intField("driveModeIndicator"),
            gear = gear,
        )
        val workMode50 = intField("workModeAlt")?.takeIf(BydSignalSemantics::isValidWorkMode)
        val workModeDirect = intField("workMode")?.takeIf(BydSignalSemantics::isValidWorkMode)
        val workMode = BydSignalSemantics.selectWorkMode(workMode50, workModeDirect)
        if (
            workMode50 != null && workModeDirect != null && workMode50 != workModeDirect &&
            !workModeConflictLogged
        ) {
            workModeConflictLogged = true
            WjxFileLogger.w(
                TAG,
                "work mode FID conflict instrument50=$workMode50 direct=$workModeDirect selected=$workMode",
            )
        }

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
            soh                 = percentField("soh"),
            speed               = doubleField("speed")?.toInt(),
            mileage             = mileage,
            power               = intField("power")?.toDouble(),
            chargeGunState      = intField("chargeGunState")?.takeIf { it in 0..5 },
            maxBatTemp          = maxBatTemp,
            avgBatTemp          = avgBatTemp,
            minBatTemp          = minBatTemp,
            chargingStatus      = intField("chargingStatus")?.takeIf { it in 0..13 },
            batteryCapacityKwh  = batteryCapacityKwh,
            totalElecConsumption = doubleField("totalElecConsumption"),
            voltage12v          = voltage12v,
            maxCellVoltage      = maxCellVoltage,
            minCellVoltage      = minCellVoltage,
            exteriorTemp        = intField("exteriorTemp"),
            gear                = gear,
            powerState          = intField("powerState")?.takeIf { it in 0..4 },
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
            sunroof             = percentField("sunroof"),
            trunk               = intField("trunk")?.takeIf { it in 1..3 },
            hood                = intField("hood"),
            seatbeltFL          = intField("seatbeltFL"),
            lockFL              = intField("lockFL")?.takeIf { it in 1..2 },
            tirePressFL         = intField("tirePressFL"),
            tirePressFR         = intField("tirePressFR"),
            tirePressRL         = intField("tirePressRL"),
            tirePressRR         = intField("tirePressRR"),
            driveMode           = driveMode,
            workMode            = workMode,
            autoPark            = null,  // not in FidMap (unknown source)
            rain                = null,  // not in FidMap (user setting, no live sensor fid)
            lightLow            = intField("lightLow"),
            drl                 = intField("drl"),
            adasApaParkingStatus = intField("adasApaParkingStatus"),
            fuelLevel           = percentField("fuelLevel"),
            elecConPer100km     = consumptionField("elecConPer100km"),
            fuelConPer100km     = consumptionField("fuelConPer100km"),
            currentTripElecKwh  = tripEnergyField("currentTripElecKwh"),
            currentTripFuelL    = tripEnergyField("currentTripFuelL"),
            chargingCapCover    = intField("chargingCapCover")?.takeIf { it in 1..2 },
            fuelCap             = intField("fuelCap")?.takeIf { it in 1..2 },
            seatbeltFR          = intField("seatbeltFR"),
            seatbeltRL          = intField("seatbeltRL"),
            seatbeltRR          = intField("seatbeltRR"),
            lockFR              = intField("lockFR")?.takeIf { it in 1..2 },
            lockRL              = intField("lockRL")?.takeIf { it in 1..2 },
            lockRR              = intField("lockRR")?.takeIf { it in 1..2 },
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
            acCtrlMode          = intField("acCtrlMode"),
            acDefrostFront      = intField("acDefrostFront"),
            acWindMode          = intField("acWindMode"),
            seatHeatD           = intField("seatHeatD")?.takeIf { it in 0..5 },
            seatVentD           = intField("seatVentD")?.takeIf { it in 0..5 },
            seatHeatP           = intField("seatHeatP")?.takeIf { it in 0..5 },
            seatVentP           = intField("seatVentP")?.takeIf { it in 0..5 },
            lightSide           = intField("lightSide"),
            tireTempFL          = intField("tireTempFL"),
            tireTempFR          = intField("tireTempFR"),
            tireTempRL          = intField("tireTempRL"),
            tireTempRR          = intField("tireTempRR"),
            lightFrontFog       = intField("lightFrontFog"),
            lightRearFog        = intField("lightRearFog"),
            lightBrake          = intField("lightBrake"),
            lightReversing      = intField("lightReversing"),
            accelPedalDepth     = percentField("accelPedalDepth"),
            epbState            = intField("epbState"),
            brakeState          = intField("brakeState"),
            ePedalMode          = intField("ePedalMode"),
            acDefrostRear       = intField("acDefrostRear"),
            steeringHeat        = intField("steeringHeat"),
            sunshadePos         = percentField("sunshadePos"),
            mirrorFoldState     = intField("mirrorFoldState"),
            adasAebState        = intField("adasAebState"),
            adasAvhState        = intField("adasAvhState"),
            adasBsdState        = intField("adasBsdState"),
            adasEspState        = intField("adasEspState"),
            adasAccGrayState    = intField("adasAccGrayState"),
            adasNoaGrayState    = intField("adasNoaGrayState"),
            adasMnoaStatus      = intField("adasMnoaStatus"),
            adasExecSpeed       = speedField("adasExecSpeed"),
            frontMotorSpeed     = motorRpmField("frontMotorSpeed", gear),
            rearMotorSpeed      = motorRpmField("rearMotorSpeed", gear),
            frontMotorTemp      = intField("frontMotorTemp"),
            rearMotorTemp       = intField("rearMotorTemp"),
            frontMotorCurrent   = motorCurrentField("frontMotorCurrent"),
            rearMotorCurrent    = motorCurrentField("rearMotorCurrent"),
            engineRpm           = engineRpmField(),
            waterTemp           = intField("waterTemp"),
            tireStateFL         = intField("tireStateFL"),
            tireStateFR         = intField("tireStateFR"),
            tireStateRL         = intField("tireStateRL"),
            tireStateRR         = intField("tireStateRR"),
            energyState         = intField("energyState"),
        )
        return VehicleSnapshot(
            data = data,
            kind = kind,
            source = batch.source,
            readStartedAtMs = batch.startedAtMs,
            readCompletedAtMs = System.currentTimeMillis(),
            requestedFieldCount = requestedFieldCount,
            transportSuccessCount = transportSuccessCount,
            decodedFieldCount = decoded.values.count { it != null },
        )
    }

    private companion object {
        private const val TAG = "NativeParsReader"
        private const val DETAIL_REFRESH_INTERVAL_NANOS = 3_000_000_000L
        private const val MAX_ENGINE_RPM = 8_000
        private const val MAX_MOTOR_RPM = 50_000
        private const val GEAR_PARK = 1
        private const val GEAR_NEUTRAL = 3
        private val UNAVAILABLE_RAW_READ = com.byd.wjx.data.vehicle.VehicleRawRead(
            status = -1,
            value = 0,
        )
        private val FullReadPlan = ReadPlan(VehicleReadProfiles.regularFull)
        private val CoreFullReadPlan = ReadPlan(VehicleReadProfiles.coreFull)
        private val DetailFullReadPlan = ReadPlan(VehicleReadProfiles.detailFull)
        private val LedMotionReadPlan = ReadPlan(VehicleReadProfiles.ledMotion)
        private val LedBrakeReadPlan = ReadPlan(VehicleReadProfiles.ledBrake)
        private val LedBodyReadPlan = ReadPlan(VehicleReadProfiles.ledBody)
        private val TireTelemetryReadPlan = ReadPlan(VehicleReadProfiles.tireTelemetry)
        private val PowertrainTelemetryReadPlan = ReadPlan(
            VehicleReadProfiles.powertrainTelemetry + FidMap.entries.single { it.field == "gear" },
        )
    }

    private data class ActionSelection(
        val fields: Set<String>,
        val plan: ReadPlan,
    )

    private data class ReadPlan(
        val entries: List<FidEntry>,
        val specs: List<VehicleReadSpec> = entries.map {
            VehicleReadSpec(it.transact, it.device, it.fid)
        },
    )
}
