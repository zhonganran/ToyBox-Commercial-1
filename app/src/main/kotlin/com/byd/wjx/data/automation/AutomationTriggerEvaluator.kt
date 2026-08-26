package com.byd.wjx.data.automation

import android.location.Location
import com.byd.wjx.data.local.entity.TriggerDef
import com.byd.wjx.data.remote.DiParsData
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import org.json.JSONObject

internal class AutomationTriggerEvaluator {
    private val lastIntervalPulseAt = ConcurrentHashMap<String, Long>()

    private companion object {
        val EVENT_TRIGGER_KINDS = setOf(
            "service_start",
            "network_available",
            "interval",
            "board_vehicle",
        )
    }

    fun retainRules(activeRuleIds: Set<Long>) {
        lastIntervalPulseAt.keys.removeAll { key ->
            key.substringBefore(':').toLongOrNull() !in activeRuleIds
        }
    }

    // --- Trigger evaluation ---

    fun evaluateEachTrigger(
        ruleId: Long,
        triggers: List<TriggerDef>,
        data: DiParsData,
        location: Location?,
        serviceStartActive: Boolean,
        networkAvailableEdge: Boolean,
        boardVehicleEdge: Boolean,
        now: Long,
        allowIntervalPulse: Boolean,
    ): List<Boolean?> = triggers.mapIndexed { index, trigger ->
        when (trigger.kind) {
            "time_of_day" -> evaluateTimeOfDay(trigger, location)
            "time_range" -> evaluateSchedule(trigger)
            "interval" -> evaluateInterval(ruleId, index, trigger, now, allowIntervalPulse)
            "service_start" -> serviceStartActive
            "network_available" -> networkAvailableEdge
            "board_vehicle" -> boardVehicleEdge
            else -> { // "param" (default)
                val actual = getParamValue(data, trigger.param) ?: return@mapIndexed null
                val expected = trigger.value.toDoubleOrNull() ?: return@mapIndexed false
                compare(actual, trigger.operator, expected)
            }
        }
    }

    private fun evaluateInterval(
        ruleId: Long,
        triggerIndex: Int,
        trigger: TriggerDef,
        now: Long,
        allowPulse: Boolean,
    ): Boolean {
        if (!allowPulse) return false
        val seconds = trigger.value.toIntOrNull()?.takeIf { it in 15..300 } ?: return false
        val key = "$ruleId:$triggerIndex"
        val last = lastIntervalPulseAt.putIfAbsent(key, now) ?: return false
        if (now - last < seconds * 1000L) return false
        lastIntervalPulseAt[key] = now
        return true
    }

    fun combineByLogic(results: List<Boolean?>, logic: String): Boolean? = when (logic) {
        "OR" -> when {
            results.any { it == true } -> true
            results.all { it == false } -> false
            else -> null
        }
        else -> when {
            results.any { it == false } -> false
            results.all { it == true } -> true
            else -> null
        }
    }

    /**
     * Determines whether the rule's matched=true was actually driven by an event
     * trigger (service_start / network_available), so the bypass-edge-detection
     * branch only fires for genuine events. Without this gate, a rule like
     * `network_available OR speed > 50` would re-fire on every poll where speed
     * exceeds 50 (after cooldown), because the bypass logic would see a present
     * event-trigger regardless of whether the event itself contributed.
     */
    fun matchedViaEventTrigger(
        triggers: List<TriggerDef>,
        perTrigger: List<Boolean?>,
        logic: String
    ): Boolean {
        return when (logic) {
            // OR: at least one event-trigger must be true on its own.
            "OR" -> triggers.zip(perTrigger).any {
                (trigger, result) -> result == true && trigger.kind in EVENT_TRIGGER_KINDS
            }
            // AND: every trigger must be true; if at least one of them is an
            // event, the whole composition fires only when that event was true,
            // so the rule is event-driven.
            else -> triggers.any { it.kind in EVENT_TRIGGER_KINDS } && perTrigger.all { it == true }
        }
    }

    fun persistentMatch(
        triggers: List<TriggerDef>,
        perTrigger: List<Boolean?>,
        logic: String,
    ): Boolean? {
        if (triggers.none { it.kind in EVENT_TRIGGER_KINDS }) {
            return combineByLogic(perTrigger, logic)
        }
        if (logic != "OR") return false
        val persistentResults = triggers.zip(perTrigger)
            .filterNot { (trigger, _) -> trigger.kind in EVENT_TRIGGER_KINDS }
            .map { (_, result) -> result }
        return if (persistentResults.isEmpty()) false else combineByLogic(persistentResults, "OR")
    }

    private fun evaluateTimeOfDay(trigger: TriggerDef, location: Location?): Boolean? {
        val loc = location ?: return null
        val phase = SunTimeCalculator.currentPhase(loc)
        val target = trigger.value.uppercase()
        return phase.name == target
    }

    private fun evaluateSchedule(trigger: TriggerDef): Boolean {
        val spec = ScheduleSpec.fromJson(trigger.value) ?: return false
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // Calendar: SUNDAY=1..SATURDAY=7 → ISO: MONDAY=1..SUNDAY=7
        val nowDow = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
            else cal.get(Calendar.DAY_OF_WEEK) - 1
        return isWithinSchedule(spec, nowMinute, nowDow)
    }

    private fun compare(actual: Double, op: String, expected: Double): Boolean = when (op) {
        ">" -> actual > expected
        "<" -> actual < expected
        ">=" -> actual >= expected
        "<=" -> actual <= expected
        "==" -> abs(actual - expected) < 0.01
        "!=" -> abs(actual - expected) >= 0.01
        else -> false
    }

    private fun getParamValue(data: DiParsData, param: String): Double? = when (param) {
        "Speed" -> data.speed?.toDouble()
        "SOC" -> data.soc?.toDouble()
        "ExtTemp" -> data.exteriorTemp?.toDouble()
        "InsideTemp" -> data.insideTemp?.toDouble()
        "ChargingStatus" -> data.chargingStatus?.toDouble()
        "ChargeGunState" -> data.chargeGunState?.toDouble()
        "PowerState" -> data.powerState?.toDouble()
        "Gear" -> data.gear?.toDouble()
        "ACStatus" -> data.acStatus?.toDouble()
        "ACTemp" -> data.acTemp?.toDouble()
        "FanLevel" -> data.fanLevel?.toDouble()
        "ACCirc" -> data.acCirc?.toDouble()
        "ACCtrlMode" -> data.acCtrlMode?.toDouble()
        "ACDefrostFront" -> data.acDefrostFront?.toDouble()
        "ACWindMode" -> data.acWindMode?.toDouble()
        "SeatHeatD" -> data.seatHeatD?.toDouble()
        "SeatVentD" -> data.seatVentD?.toDouble()
        "SeatHeatP" -> data.seatHeatP?.toDouble()
        "SeatVentP" -> data.seatVentP?.toDouble()
        "ACDefrostRear" -> data.acDefrostRear?.toDouble()
        "SteeringHeat" -> data.steeringHeat?.toDouble()
        "SunshadePos" -> data.sunshadePos?.toDouble()
        "MirrorFoldState" -> data.mirrorFoldState?.toDouble()
        "DoorFL" -> data.doorFL?.toDouble()
        "DoorFR" -> data.doorFR?.toDouble()
        "DoorRL" -> data.doorRL?.toDouble()
        "DoorRR" -> data.doorRR?.toDouble()
        "WindowFL" -> data.windowFL?.toDouble()
        "WindowFR" -> data.windowFR?.toDouble()
        "WindowRL" -> data.windowRL?.toDouble()
        "WindowRR" -> data.windowRR?.toDouble()
        "Sunroof" -> data.sunroof?.toDouble()
        "Trunk" -> data.trunk?.toDouble()
        "Hood" -> data.hood?.toDouble()
        "SeatbeltFL" -> data.seatbeltFL?.toDouble()
        "LockFL" -> data.lockFL?.toDouble()
        "TirePressFL" -> data.tirePressFL?.toDouble()
        "TirePressFR" -> data.tirePressFR?.toDouble()
        "TirePressRL" -> data.tirePressRL?.toDouble()
        "TirePressRR" -> data.tirePressRR?.toDouble()
        "TireTempFL" -> data.tireTempFL?.toDouble()
        "TireTempFR" -> data.tireTempFR?.toDouble()
        "TireTempRL" -> data.tireTempRL?.toDouble()
        "TireTempRR" -> data.tireTempRR?.toDouble()
        "TireStateFL" -> data.tireStateFL?.toDouble()
        "TireStateFR" -> data.tireStateFR?.toDouble()
        "TireStateRL" -> data.tireStateRL?.toDouble()
        "TireStateRR" -> data.tireStateRR?.toDouble()
        "DriveMode" -> data.driveMode?.toDouble()
        "WorkMode" -> data.workMode?.toDouble()
        "EnergyState" -> data.energyState?.toDouble()
        "AutoPark" -> (data.autoPark ?: data.adasApaParkingStatus)?.toDouble()
        "AdasApaParkingStatus" -> data.adasApaParkingStatus?.toDouble()
        "ChargingCap" -> data.chargingCapCover?.toDouble()
        "FuelCap" -> data.fuelCap?.toDouble()
        "Rain" -> data.rain?.toDouble()
        "LightLow" -> data.lightLow?.toDouble()
        "LightSide" -> data.lightSide?.toDouble()
        "LightFrontFog" -> data.lightFrontFog?.toDouble()
        "LightRearFog" -> data.lightRearFog?.toDouble()
        "LightBrake" -> data.lightBrake?.toDouble()
        "LightReversing" -> data.lightReversing?.toDouble()
        "LightHigh" -> data.lightHigh?.toDouble()
        "DRL" -> data.drl?.toDouble()
        "TurnSignalFlash" -> data.turnSignalFlash?.toDouble()
        "AdasAccMode" -> data.adasAccMode?.toDouble()
        "AdasLccNcaStatus" -> data.adasLccNcaStatus?.toDouble()
        "AdasTjaState" -> data.adasTjaState?.toDouble()
        "LaneKeepState" -> data.laneKeepState?.toDouble()
        "AccCruiseState" -> data.accCruiseState?.toDouble()
        "AccCruisingSpeed" -> data.accCruisingSpeed?.toDouble()
        "AdasAebState" -> data.adasAebState?.toDouble()
        "AdasAvhState" -> data.adasAvhState?.toDouble()
        "AdasBsdState" -> data.adasBsdState?.toDouble()
        "AdasEspState" -> data.adasEspState?.toDouble()
        "AdasAccGrayState" -> data.adasAccGrayState?.toDouble()
        "AdasNoaGrayState" -> data.adasNoaGrayState?.toDouble()
        "AdasMnoaStatus" -> data.adasMnoaStatus?.toDouble()
        "AdasExecSpeed" -> data.adasExecSpeed?.toDouble()
        "BrakePedal" -> data.brakePedalDepth?.toDouble()
        "AccelPedal" -> data.accelPedalDepth?.toDouble()
        "EpbState" -> data.epbState?.toDouble()
        "BrakeState" -> data.brakeState?.toDouble()
        "EpedalMode" -> data.ePedalMode?.toDouble()
        "TurnSignalLeft" -> data.turnSignalLeft?.toDouble()
        "TurnSignalRight" -> data.turnSignalRight?.toDouble()
        "MaxBatTemp" -> data.maxBatTemp?.toDouble()
        "AvgBatTemp" -> data.avgBatTemp?.toDouble()
        "MinBatTemp" -> data.minBatTemp?.toDouble()
        "Power" -> data.power
        "Mileage" -> data.mileage
        "Voltage12V" -> data.voltage12v
        "MinCellVoltage" -> data.minCellVoltage
        "MaxCellVoltage" -> data.maxCellVoltage
        "FrontMotorSpeed" -> data.frontMotorSpeed?.toDouble()
        "RearMotorSpeed" -> data.rearMotorSpeed?.toDouble()
        "FrontMotorTemp" -> data.frontMotorTemp?.toDouble()
        "RearMotorTemp" -> data.rearMotorTemp?.toDouble()
        "FrontMotorCurrent" -> data.frontMotorCurrent
        "RearMotorCurrent" -> data.rearMotorCurrent
        "EngineRPM" -> data.engineRpm?.toDouble()
        "WaterTemp" -> data.waterTemp?.toDouble()
        else -> null
    }

    fun buildSnapshot(triggers: List<TriggerDef>, data: DiParsData): String {
        val json = JSONObject()
        triggers.forEach { t ->
            when (t.kind) {
                "time_of_day" -> json.put("time_of_day", t.value)
                "time_range" -> json.put("time_range", t.value)
                "interval" -> json.put("interval_sec", t.value)
                "service_start" -> json.put("service_start", true)
                "network_available" -> json.put("network_available", true)
                "board_vehicle" -> json.put("board_vehicle", true)
                else -> json.put(t.param, getParamValue(data, t.param) ?: JSONObject.NULL)
            }
        }
        return json.toString()
    }
}
