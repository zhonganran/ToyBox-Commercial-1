package com.byd.wjx.data.automation

import com.byd.wjx.audio.AutoParkingSoundSettings
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.nativestack.ActionSnapshotFieldProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationActionSnapshotFieldProvider @Inject constructor(
    private val repository: AutomationConfigRepository,
    private val autoParkingSoundSettings: AutoParkingSoundSettings,
) : ActionSnapshotFieldProvider {
    override suspend fun additionalFields(): Set<String> {
        val autoParkingFields = requiredRuntimeFeatureFields(autoParkingSoundSettings.enabled)
        return repository.current().requiredVehicleFields + autoParkingFields
    }
}

internal fun requiredRuntimeFeatureFields(autoParkingSoundEnabled: Boolean): Set<String> =
    if (autoParkingSoundEnabled) setOf("adasApaParkingStatus") else emptySet()

internal fun requiredAutomationVehicleFields(rules: List<RuleEntity>): Set<String> = buildSet {
    rules.asSequence()
        .flatMap { TriggerDef.listFromJson(it.triggers).asSequence() }
        .filterNot { it.kind in NON_VEHICLE_TRIGGER_KINDS }
        .forEach { trigger ->
            PARAM_TO_VEHICLE_FIELDS[trigger.param.trim()].orEmpty().forEach(::add)
        }
}

private val NON_VEHICLE_TRIGGER_KINDS = setOf(
    "place_enter",
    "place_exit",
    "time_of_day",
    "time_range",
    "interval",
    "service_start",
    "network_available",
    "board_vehicle",
    "button_press",
)

private val PARAM_TO_VEHICLE_FIELDS = mapOf(
    "Speed" to setOf("speed"),
    "SOC" to setOf("soc"),
    "ExtTemp" to setOf("exteriorTemp"),
    "InsideTemp" to setOf("insideTemp"),
    "ChargingStatus" to setOf("chargingStatus"),
    "ChargeGunState" to setOf("chargeGunState"),
    "PowerState" to setOf("powerState"),
    "Gear" to setOf("gear"),
    "ACStatus" to setOf("acStatus"),
    "ACTemp" to setOf("acTemp"),
    "FanLevel" to setOf("fanLevel"),
    "ACCirc" to setOf("acCirc"),
    "ACCtrlMode" to setOf("acCtrlMode"),
    "ACDefrostFront" to setOf("acDefrostFront"),
    "ACWindMode" to setOf("acWindMode"),
    "SeatHeatD" to setOf("seatHeatD"),
    "SeatVentD" to setOf("seatVentD"),
    "SeatHeatP" to setOf("seatHeatP"),
    "SeatVentP" to setOf("seatVentP"),
    "ACDefrostRear" to setOf("acDefrostRear"),
    "SteeringHeat" to setOf("steeringHeat"),
    "SunshadePos" to setOf("sunshadePos"),
    "MirrorFoldState" to setOf("mirrorFoldState"),
    "DoorFL" to setOf("doorFL"),
    "DoorFR" to setOf("doorFR"),
    "DoorRL" to setOf("doorRL"),
    "DoorRR" to setOf("doorRR"),
    "WindowFL" to setOf("windowFL"),
    "WindowFR" to setOf("windowFR"),
    "WindowRL" to setOf("windowRL"),
    "WindowRR" to setOf("windowRR"),
    "Sunroof" to setOf("sunroof"),
    "Trunk" to setOf("trunk"),
    "Hood" to setOf("hood"),
    "SeatbeltFL" to setOf("seatbeltFL"),
    "LockFL" to setOf("lockFL"),
    "TirePressFL" to setOf("tirePressFL"),
    "TirePressFR" to setOf("tirePressFR"),
    "TirePressRL" to setOf("tirePressRL"),
    "TirePressRR" to setOf("tirePressRR"),
    "TireTempFL" to setOf("tireTempFL"),
    "TireTempFR" to setOf("tireTempFR"),
    "TireTempRL" to setOf("tireTempRL"),
    "TireTempRR" to setOf("tireTempRR"),
    "TireStateFL" to setOf("tireStateFL"),
    "TireStateFR" to setOf("tireStateFR"),
    "TireStateRL" to setOf("tireStateRL"),
    "TireStateRR" to setOf("tireStateRR"),
    "DriveMode" to setOf("driveMode"),
    "WorkMode" to setOf("workMode"),
    "EnergyState" to setOf("energyState"),
    "AutoPark" to setOf("adasApaParkingStatus"),
    "AdasApaParkingStatus" to setOf("adasApaParkingStatus"),
    "ChargingCap" to setOf("chargingCapCover"),
    "FuelCap" to setOf("fuelCap"),
    "LightLow" to setOf("lightLow"),
    "LightSide" to setOf("lightSide"),
    "LightFrontFog" to setOf("lightFrontFog"),
    "LightRearFog" to setOf("lightRearFog"),
    "LightBrake" to setOf("lightBrake"),
    "LightReversing" to setOf("lightReversing"),
    "LightHigh" to setOf("lightHigh"),
    "DRL" to setOf("drl"),
    "TurnSignalFlash" to setOf("turnSignalFlash"),
    "AdasAccMode" to setOf("adasAccMode"),
    "AdasLccNcaStatus" to setOf("adasLccNcaStatus"),
    "AdasTjaState" to setOf("adasTjaState"),
    "LaneKeepState" to setOf("laneKeepState"),
    "AccCruiseState" to setOf("accCruiseState"),
    "AccCruisingSpeed" to setOf("accCruisingSpeed"),
    "AdasAebState" to setOf("adasAebState"),
    "AdasAvhState" to setOf("adasAvhState"),
    "AdasBsdState" to setOf("adasBsdState"),
    "AdasEspState" to setOf("adasEspState"),
    "AdasAccGrayState" to setOf("adasAccGrayState"),
    "AdasNoaGrayState" to setOf("adasNoaGrayState"),
    "AdasMnoaStatus" to setOf("adasMnoaStatus"),
    "AdasExecSpeed" to setOf("adasExecSpeed"),
    "BrakePedal" to setOf("brakePedalDepth"),
    "AccelPedal" to setOf("accelPedalDepth"),
    "EpbState" to setOf("epbState"),
    "BrakeState" to setOf("brakeState"),
    "EpedalMode" to setOf("ePedalMode"),
    "TurnSignalLeft" to setOf("turnSignalLeft"),
    "TurnSignalRight" to setOf("turnSignalRight"),
    "MaxBatTemp" to setOf("maxBatTemp"),
    "AvgBatTemp" to setOf("maxBatTemp", "minBatTemp"),
    "MinBatTemp" to setOf("minBatTemp"),
    "Power" to setOf("power"),
    "Mileage" to setOf("mileage"),
    "Voltage12V" to setOf("voltage12v"),
    "MinCellVoltage" to setOf("minCellVoltage"),
    "MaxCellVoltage" to setOf("maxCellVoltage"),
    "FrontMotorSpeed" to setOf("frontMotorSpeed"),
    "RearMotorSpeed" to setOf("rearMotorSpeed"),
    "FrontMotorTemp" to setOf("frontMotorTemp"),
    "RearMotorTemp" to setOf("rearMotorTemp"),
    "FrontMotorCurrent" to setOf("frontMotorCurrent"),
    "RearMotorCurrent" to setOf("rearMotorCurrent"),
    "EngineRPM" to setOf("engineRpm"),
    "WaterTemp" to setOf("waterTemp"),
)
