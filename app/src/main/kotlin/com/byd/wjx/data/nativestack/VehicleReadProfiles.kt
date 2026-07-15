package com.byd.wjx.data.nativestack

/**
 * Explicit polling profiles for the shared vehicle reader.
 *
 * The helper executes one autoservice transaction per FID, so reading the entire catalog on every
 * driving tick creates avoidable Binder and vendor-service load. Core fields drive lifecycle,
 * trips, charging and the foreground notification; detail fields remain available to UI but may
 * be reused for a short interval between refreshes.
 */
internal object VehicleReadProfiles {
    /**
     * Feature-specific fields that do not justify permanent Binder transactions in every
     * initial/detail full snapshot. Unsupported trims therefore fail soft at zero idle cost.
     */
    private val featureOnDemandFields: Set<String> = setOf(
        "acCtrlMode",
        "acDefrostFront",
        "acWindMode",
        "seatHeatD",
        "seatVentD",
        "seatHeatP",
        "seatVentP",
        "lightSide",
        "lightFrontFog",
        "lightRearFog",
        "lightBrake",
        "lightReversing",
        "accelPedalDepth",
        "epbState",
        "brakeState",
        "ePedalMode",
        "acDefrostRear",
        "steeringHeat",
        "sunshadePos",
        "mirrorFoldState",
    )

    val tireTelemetryFields: Set<String> = setOf(
        "tireTempFL",
        "tireTempFR",
        "tireTempRL",
        "tireTempRR",
        "tireStateFL",
        "tireStateFR",
        "tireStateRL",
        "tireStateRR",
    )

    val adasTelemetryFields: Set<String> = setOf(
        "adasAebState",
        "adasAvhState",
        "adasBsdState",
        "adasEspState",
        "adasAccGrayState",
        "adasNoaGrayState",
        "adasMnoaStatus",
        "adasExecSpeed",
    )

    val powertrainTelemetryFields: Set<String> = setOf(
        "frontMotorSpeed",
        "rearMotorSpeed",
        "frontMotorTemp",
        "rearMotorTemp",
        "frontMotorCurrent",
        "rearMotorCurrent",
        "engineRpm",
        "waterTemp",
    )

    /** Complete catalog excluded from permanent full polling. */
    val onDemandFields: Set<String> = featureOnDemandFields +
        tireTelemetryFields + adasTelemetryFields + powertrainTelemetryFields

    /** Regular dashboard/trip snapshot. On-demand fields remain selectable by action plans. */
    val regularFull: List<FidEntry> = FidMap.entries.filterNot {
        it.field in onDemandFields
    }

    val coreFullFields: Set<String> = setOf(
        "soc",
        "speed",
        "mileage",
        "power",
        "chargeGunState",
        "maxBatTemp",
        "minBatTemp",
        "chargingStatus",
        "totalElecConsumption",
        "voltage12v",
        "gear",
        "powerState",
    )

    val coreFull: List<FidEntry> = regularFull.filter { it.field in coreFullFields }
    val detailFull: List<FidEntry> = regularFull.filterNot { it.field in coreFullFields }

    val actionCoreFields: Set<String> = setOf(
        "soc",
        "speed",
        "gear",
        "chargeGunState",
        "powerState",
        "adasApaParkingStatus",
    )

    val ledFastFields: Set<String> = setOf(
        "turnSignalLeft",
        "turnSignalRight",
        "hazardLights",
        "gear",
        "brakePedalDepth",
        "lightBrake",
        "lightReversing",
    )

    val ledFast: List<FidEntry> = FidMap.entries.filter { it.field in ledFastFields }
    val tireTelemetry: List<FidEntry> = FidMap.entries.filter {
        it.field in tireTelemetryFields
    }
    val adasTelemetry: List<FidEntry> = FidMap.entries.filter {
        it.field in adasTelemetryFields
    }
    val powertrainTelemetry: List<FidEntry> = FidMap.entries.filter {
        it.field in powertrainTelemetryFields
    }

    init {
        require(coreFull.isNotEmpty()) { "Core vehicle read profile must not be empty" }
        require(coreFull.size + detailFull.size == regularFull.size) {
            "Core/detail vehicle profiles must partition the regular full profile"
        }
        require(
            regularFull.map { it.field }.toSet() + onDemandFields ==
                FidMap.entries.map { it.field }.toSet()
        ) {
            "Regular and on-demand profiles must cover the selectable FID catalog"
        }
        require(regularFull.none { it.field in onDemandFields }) {
            "On-demand fields must not leak into regular full reads"
        }
        require(
            FidMap.entries.filter { it.field in onDemandFields }
                .map { it.field }.toSet() == onDemandFields
        ) {
            "On-demand profile references an unmapped field"
        }
        require(tireTelemetry.map { it.field }.toSet() == tireTelemetryFields) {
            "Tire telemetry profile references an unmapped field"
        }
        require(adasTelemetry.map { it.field }.toSet() == adasTelemetryFields) {
            "ADAS telemetry profile references an unmapped field"
        }
        require(powertrainTelemetry.map { it.field }.toSet() == powertrainTelemetryFields) {
            "Powertrain telemetry profile references an unmapped field"
        }
        require(coreFull.map { it.field }.toSet() == coreFullFields) {
            "Core vehicle read profile references an unmapped field"
        }
        require(ledFast.map { it.field }.toSet() == ledFastFields) {
            "LED fast read profile references an unmapped field"
        }
    }
}
