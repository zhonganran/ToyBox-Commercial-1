package com.byd.wjx.data.nativestack

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.VehicleBatchRead
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind
import com.byd.wjx.log.WjxFileLogger
import kotlin.math.abs
import kotlin.math.roundToInt

/** Decodes one ordered native batch and applies domain-level signal guards. */
internal class NativeVehicleSnapshotParser {
    @Volatile private var workModeConflictLogged = false

    fun parse(
        plan: NativeReadPlan,
        kind: VehicleSnapshotKind,
        batch: VehicleBatchRead,
        batteryCapacityKwh: Double?,
        requestedFieldCount: Int = batch.specs.size,
        transportSuccessCount: Int = batch.acceptedCount,
    ): VehicleSnapshot? {
        if (batch.values.size != plan.entries.size) return null
        val decoded = decode(plan, batch)

        @Suppress("UNCHECKED_CAST")
        fun <T> field(name: String): T? = decoded[name] as? T
        fun intField(name: String): Int? = field<Int>(name)
        fun doubleField(name: String): Double? = field<Double>(name)
        fun rangeKmField(name: String, maximum: Int): Int? =
            intField(name)?.takeIf { it in 0..maximum }
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
        logWorkModeConflictOnce(workMode50, workModeDirect, workMode)

        val hasLiveData = when (kind) {
            VehicleSnapshotKind.FULL -> soc != null || mileage != null || voltage12v != null
            VehicleSnapshotKind.ACTION -> decoded.values.any { it != null }
        }
        if (!hasLiveData) return null

        val maxBatTemp = field<Int>("maxBatTemp")
        val minBatTemp = field<Int>("minBatTemp")
        val avgBatTemp = when {
            maxBatTemp != null && minBatTemp != null -> ((maxBatTemp + minBatTemp) / 2.0).roundToInt()
            else -> maxBatTemp ?: minBatTemp
        }
        val data = DiParsData(
            soc = soc,
            soh = percentField("soh"),
            speed = doubleField("speed")?.toInt(),
            mileage = mileage,
            power = intField("power")?.toDouble(),
            chargeGunState = intField("chargeGunState")?.takeIf { it in 0..5 },
            maxBatTemp = maxBatTemp,
            avgBatTemp = avgBatTemp,
            minBatTemp = minBatTemp,
            chargingStatus = intField("chargingStatus")?.takeIf { it in 0..13 },
            batteryCapacityKwh = batteryCapacityKwh,
            totalElecConsumption = doubleField("totalElecConsumption"),
            voltage12v = voltage12v,
            maxCellVoltage = maxCellVoltage,
            minCellVoltage = minCellVoltage,
            exteriorTemp = intField("exteriorTemp"),
            gear = gear,
            powerState = intField("powerState")?.takeIf { it in 0..4 },
            insideTemp = intField("insideTemp"),
            acStatus = intField("acStatus"),
            acTemp = intField("acTemp"),
            fanLevel = intField("fanLevel"),
            acCirc = intField("acCirc"),
            doorFL = intField("doorFL"),
            doorFR = intField("doorFR"),
            doorRL = intField("doorRL"),
            doorRR = intField("doorRR"),
            windowFL = percentField("windowFL"),
            windowFR = percentField("windowFR"),
            windowRL = percentField("windowRL"),
            windowRR = percentField("windowRR"),
            sunroof = percentField("sunroof"),
            trunk = intField("trunk")?.takeIf { it in 1..3 },
            hood = intField("hood"),
            seatbeltFL = intField("seatbeltFL"),
            lockFL = intField("lockFL")?.takeIf { it in 1..2 },
            tirePressFL = intField("tirePressFL"),
            tirePressFR = intField("tirePressFR"),
            tirePressRL = intField("tirePressRL"),
            tirePressRR = intField("tirePressRR"),
            driveMode = driveMode,
            workMode = workMode,
            autoPark = null,
            rain = null,
            lightLow = intField("lightLow"),
            drl = intField("drl"),
            adasApaParkingStatus = intField("adasApaParkingStatus"),
            fuelLevel = percentField("fuelLevel"),
            elecDrivingRangeKm = rangeKmField("elecDrivingRangeKm", MAX_ELECTRIC_RANGE_KM),
            fuelDrivingRangeKm = rangeKmField("fuelDrivingRangeKm", MAX_FUEL_RANGE_KM),
            elecConPer100km = consumptionField("elecConPer100km"),
            fuelConPer100km = consumptionField("fuelConPer100km"),
            currentTripElecKwh = tripEnergyField("currentTripElecKwh"),
            currentTripFuelL = tripEnergyField("currentTripFuelL"),
            chargingCapCover = intField("chargingCapCover")?.takeIf { it in 1..2 },
            fuelCap = intField("fuelCap")?.takeIf { it in 1..2 },
            seatbeltFR = intField("seatbeltFR"),
            seatbeltRL = intField("seatbeltRL"),
            seatbeltRR = intField("seatbeltRR"),
            lockFR = intField("lockFR")?.takeIf { it in 1..2 },
            lockRL = intField("lockRL")?.takeIf { it in 1..2 },
            lockRR = intField("lockRR")?.takeIf { it in 1..2 },
            lightHigh = intField("lightHigh"),
            hazardLights = intField("hazardLights"),
            turnSignalFlash = intField("turnSignalFlash"),
            adasAccMode = intField("adasAccMode"),
            adasLccNcaStatus = intField("adasLccNcaStatus"),
            adasTjaState = intField("adasTjaState"),
            laneKeepState = intField("laneKeepState"),
            accCruiseState = intField("accCruiseState"),
            accCruisingSpeed = speedField("accCruisingSpeed"),
            brakePedalDepth = percentField("brakePedalDepth"),
            turnSignalLeft = intField("turnSignalLeft"),
            turnSignalRight = intField("turnSignalRight"),
            acCtrlMode = intField("acCtrlMode"),
            acDefrostFront = intField("acDefrostFront"),
            acWindMode = intField("acWindMode"),
            seatHeatD = intField("seatHeatD")?.takeIf { it in 0..5 },
            seatVentD = intField("seatVentD")?.takeIf { it in 0..5 },
            seatHeatP = intField("seatHeatP")?.takeIf { it in 0..5 },
            seatVentP = intField("seatVentP")?.takeIf { it in 0..5 },
            lightSide = intField("lightSide"),
            tireTempFL = intField("tireTempFL"),
            tireTempFR = intField("tireTempFR"),
            tireTempRL = intField("tireTempRL"),
            tireTempRR = intField("tireTempRR"),
            lightFrontFog = intField("lightFrontFog"),
            lightRearFog = intField("lightRearFog"),
            lightBrake = intField("lightBrake"),
            lightReversing = intField("lightReversing"),
            accelPedalDepth = percentField("accelPedalDepth"),
            epbState = intField("epbState"),
            brakeState = intField("brakeState"),
            ePedalMode = intField("ePedalMode"),
            acDefrostRear = intField("acDefrostRear"),
            steeringHeat = intField("steeringHeat"),
            sunshadePos = percentField("sunshadePos"),
            mirrorFoldState = intField("mirrorFoldState"),
            adasAebState = intField("adasAebState"),
            adasAvhState = intField("adasAvhState"),
            adasBsdState = intField("adasBsdState"),
            adasEspState = intField("adasEspState"),
            adasAccGrayState = intField("adasAccGrayState"),
            adasNoaGrayState = intField("adasNoaGrayState"),
            adasMnoaStatus = intField("adasMnoaStatus"),
            adasExecSpeed = speedField("adasExecSpeed"),
            frontMotorSpeed = motorRpmField("frontMotorSpeed", gear),
            rearMotorSpeed = motorRpmField("rearMotorSpeed", gear),
            frontMotorTemp = intField("frontMotorTemp"),
            rearMotorTemp = intField("rearMotorTemp"),
            frontMotorCurrent = motorCurrentField("frontMotorCurrent"),
            rearMotorCurrent = motorCurrentField("rearMotorCurrent"),
            engineRpm = engineRpmField(),
            waterTemp = intField("waterTemp"),
            tireStateFL = intField("tireStateFL"),
            tireStateFR = intField("tireStateFR"),
            tireStateRL = intField("tireStateRL"),
            tireStateRR = intField("tireStateRR"),
            energyState = intField("energyState"),
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

    private fun decode(
        plan: NativeReadPlan,
        batch: VehicleBatchRead,
    ): Map<String, Any?> = buildMap {
        plan.entries.forEachIndexed { index, entry ->
            val raw = batch.values[index].takeIf { it.accepted }?.value
            val value = when (entry.transact) {
                5 -> raw?.let {
                    if (entry.decoder == Decoder.INT_SCALED) {
                        ParamDecoder.decodeScaled(it, entry.scale)
                    } else {
                        ParamDecoder.decodeInt(it, entry.decoder)
                    }
                }
                7 -> raw?.let { ParamDecoder.decodeFloat(it, entry.decoder) }
                else -> null
            }
            put(entry.field, value)
        }
    }

    private fun logWorkModeConflictOnce(alternate: Int?, direct: Int?, selected: Int?) {
        if (alternate == null || direct == null || alternate == direct || workModeConflictLogged) return
        workModeConflictLogged = true
        WjxFileLogger.w(
            TAG,
            "work mode FID conflict instrument50=$alternate direct=$direct selected=$selected",
        )
    }

    private companion object {
        const val TAG = "NativeSnapshotParser"
        const val MAX_ENGINE_RPM = 8_000
        const val MAX_MOTOR_RPM = 50_000
        const val MAX_ELECTRIC_RANGE_KM = 1_500
        const val MAX_FUEL_RANGE_KM = 2_500
        const val GEAR_PARK = 1
        const val GEAR_NEUTRAL = 3
    }
}
