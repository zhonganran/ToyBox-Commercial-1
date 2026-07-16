package com.byd.wjx.data.nativestack

data class FidEntry(
    val field: String,      // matches DiParsData property name
    val device: Int,
    val fid: Int,
    val transact: Int,      // 5 = getInt, 7 = getFloat
    val decoder: Decoder,
    val scale: Double = 1.0, // used only when decoder == INT_SCALED
)

/**
 * Static map: each DiParsData source field → autoservice address + decoder.
 * Addresses are cross-checked against the BYD Settings feature catalog and, where available,
 * on-device probes. Decoded enum values retain the official BYD encoding; shared interpretation
 * lives in [BydSignalSemantics] so consumers cannot silently redefine the same value.
 *
 * [entries] is the selectable production catalog. [VehicleReadProfiles] keeps feature-specific
 * reads out of the regular full snapshot and only selects them when an enabled consumer needs them.
 */
object FidMap {
    val entries: List<FidEntry> = listOf(
        // Core energy + drive
        FidEntry("soc",                  1014, 1246777400,   7, Decoder.FLOAT_PERCENT),
        FidEntry("soh",                  1014, 1145045032,   5, Decoder.INT_PERCENT),
        FidEntry("fuelLevel",            1014, 1246785600,   5, Decoder.INT_PERCENT),
        FidEntry("speed",                1013, -1807745016,  7, Decoder.FLOAT_KW),
        FidEntry("mileage",              1014, 1246765072,   5, Decoder.INT_SCALED,  scale = 0.1),
        FidEntry("elecDrivingRangeKm",   1014, 1246765118,   5, Decoder.INT_RAW),
        FidEntry("fuelDrivingRangeKm",   1014, 1246773304,   5, Decoder.INT_RAW),
        FidEntry("power",                1012, 339738656,    5, Decoder.INT_RAW),
        FidEntry("totalElecConsumption", 1014, 1032871984,   7, Decoder.FLOAT_KWH),
        FidEntry("elecConPer100km",      1014, 1032863794,   7, Decoder.FLOAT_KWH),
        FidEntry("fuelConPer100km",      1014, 1032863782,   7, Decoder.FLOAT_KWH),
        FidEntry("currentTripElecKwh",   1014, 1246801976,   7, Decoder.FLOAT_KWH),
        FidEntry("currentTripFuelL",     1014, 1246785576,   7, Decoder.FLOAT_KWH),
        FidEntry("voltage12v",           1001, 1128267816,   7, Decoder.FLOAT_VOLT),
        // Battery
        FidEntry("maxCellVoltage",       1014, 1147142192,   5, Decoder.INT_SCALED,  scale = 0.001),
        FidEntry("minCellVoltage",       1014, 1147142160,   5, Decoder.INT_SCALED,  scale = 0.001),
        // Gear + state
        FidEntry("gear",                 1011, 555745336,    5, Decoder.INT_ENUM),
        // Reference catalog BrakePedal: braking demand used by the LED distance warning.
        FidEntry("brakePedalDepth",      1013, 874512400,    5, Decoder.INT_PERCENT),
        FidEntry("chargeGunState",       1009, 876609586,    5, Decoder.INT_ENUM),
        FidEntry("chargingCapCover",     1009, 1081081898,   5, Decoder.INT_ENUM),
        // CHARGING_BATTERRY_DEVICE_STATE. The former 0x49601010 is only the
        // charging-function enable/disable switch, not the live BMS state.
        FidEntry("chargingStatus",       1009, 876609560,    5, Decoder.INT_ENUM),
        // Climate
        FidEntry("acStatus",             1000, 1077936144,   5, Decoder.INT_ENUM),
        FidEntry("acTemp",               1000, 1077936168,   5, Decoder.INT_TEMP_C),
        FidEntry("fanLevel",             1000, 1077936156,   5, Decoder.INT_RAW),
        FidEntry("acCirc",               1000, 1077936148,   5, Decoder.INT_ENUM),
        FidEntry("insideTemp",           1000, 1031798832,   5, Decoder.INT_TEMP_C),
        FidEntry("exteriorTemp",         1000, 1077936184,   5, Decoder.INT_TEMP_C),
        // Body
        FidEntry("hood",                 1001, 692060188,    5, Decoder.INT_ENUM),
        // Left flap is the fuel-port cover on the supported PHEV layout. The former
        // instrument fid 0x3D902022 is a "charge pot not closed" fault reminder.
        FidEntry("fuelCap",              1009, 1081081896,   5, Decoder.INT_ENUM),
        // Safety
        FidEntry("seatbeltFL",           1007, 692060184,    5, Decoder.INT_ENUM),
        FidEntry("seatbeltFR",           1042, 824180744,    5, Decoder.INT_ENUM),
        FidEntry("seatbeltRL",           1042, 824180746,    5, Decoder.INT_ENUM),
        FidEntry("seatbeltRR",           1042, 824180748,    5, Decoder.INT_ENUM),
        // Tires
        FidEntry("tirePressFL",          1016, -1728052956,  5, Decoder.INT_KPA),
        FidEntry("tirePressFR",          1016, -1728052952,  5, Decoder.INT_KPA),
        FidEntry("tirePressRL",          1016, -1728052948,  5, Decoder.INT_KPA),
        FidEntry("tirePressRR",          1016, -1728052944,  5, Decoder.INT_KPA),
        // Lights
        FidEntry("lightLow",             1004, 950009866,    5, Decoder.INT_ENUM),
        FidEntry("lightHigh",            1004, 950009868,    5, Decoder.INT_ENUM),
        FidEntry("hazardLights",         1004, 960495667,    5, Decoder.INT_ENUM),
        // BYDAutoFeatureIds.Light.LIGHT_LEFT/RIGHT_TURN_SIGNAL on CAN-FD vehicles.
        FidEntry("turnSignalLeft",       1004, 339738672,    5, Decoder.INT_ENUM),
        FidEntry("turnSignalRight",      1004, 339738678,    5, Decoder.INT_ENUM),
        FidEntry("turnSignalFlash",      1004, 950009900,    5, Decoder.INT_ENUM),
        FidEntry("drl",                  1004, 1231040528,   5, Decoder.INT_ENUM),
        // Graduated from yaml status=candidate without formal D+ snap validation.
        // Smoke on real DiLink will surface sentinel returns or wrong values.
        // If a field shows sentinel/garbage in UI after upgrade — pull the fid from FidMap.
        // Battery temps carry a -40 CAN offset (raw 51 → 11°C). Validated against
        // D+ 10/11/11 and the vehicle reference offsets {-40} on Fangchengbao Bao 3 2026-05-29.
        FidEntry("maxBatTemp",           1014, 1148190752,   5, Decoder.INT_TEMP_C_OFS40),
        FidEntry("minBatTemp",           1014, 1148190736,   5, Decoder.INT_TEMP_C_OFS40),
        // BODYWORK_POWER_LEVEL. AutoTark's 0x12D00020 belongs to the Setting device
        // and describes the source of a vehicle-state change, not ignition level.
        FidEntry("powerState",           1001, 315621418,    5, Decoder.INT_ENUM),
        FidEntry("doorFL",               1001, 692060168,    5, Decoder.INT_ENUM),
        FidEntry("doorFR",               1001, 692060170,    5, Decoder.INT_ENUM),
        FidEntry("doorRL",               1001, 692060172,    5, Decoder.INT_ENUM),
        FidEntry("doorRR",               1001, 692060174,    5, Decoder.INT_ENUM),
        FidEntry("windowFL",             1001, 947912728,    5, Decoder.INT_PERCENT),
        FidEntry("windowFR",             1001, 1267728400,   5, Decoder.INT_PERCENT),
        FidEntry("windowRL",             1001, 947912736,    5, Decoder.INT_PERCENT),
        FidEntry("windowRR",             1001, 1267728408,   5, Decoder.INT_PERCENT),
        // Official Settings reads BODYWORK_MOON_ROOF_OPEN_PERCENT and renders raw / 100.
        FidEntry("sunroof",              1001, 1101004808,   5, Decoder.INT_PERCENT),
        FidEntry("trunk",                1001, 1074790416,   5, Decoder.INT_ENUM),
        FidEntry("lockFL",               1032, 1081081864,   5, Decoder.INT_ENUM),
        FidEntry("lockFR",               1001, 1081081866,   5, Decoder.INT_ENUM),
        FidEntry("lockRL",               1001, 1081081868,   5, Decoder.INT_ENUM),
        FidEntry("lockRR",               1001, 1081081870,   5, Decoder.INT_ENUM),
        FidEntry("driveMode",            1006, 555745294,    5, Decoder.INT_ENUM),
        FidEntry("workMode",             1006, 874512420,    5, Decoder.INT_ENUM),
        // ENERGY_STATE. Sampled with live power so regeneration can be split into
        // EV/HEV/assisted recovery and engine generation instead of one mixed total.
        FidEntry("energyState",           1006, 873463832,    5, Decoder.INT_ENUM),
        FidEntry("adasAccMode",          1038, 852492340,    5, Decoder.INT_ENUM),
        FidEntry("adasLccNcaStatus",     1038, 440401932,    5, Decoder.INT_ENUM),
        FidEntry("adasTjaState",         1038, 828375084,    5, Decoder.INT_ENUM),
        FidEntry("laneKeepState",        1038, 828375054,    5, Decoder.INT_ENUM),
        FidEntry("accCruiseState",       1007, 694157362,    5, Decoder.INT_ENUM),
        FidEntry("accCruisingSpeed",     1007, 710934536,    5, Decoder.INT_RAW),
        // AutoTark v3.6.0 FidMap: AdasApaParkingStatus / 自动泊车.
        FidEntry("adasApaParkingStatus", 1038, 749732128,    5, Decoder.INT_ENUM),
        // Validated comfort/light reads. These are production-addressable but on-demand only;
        // see VehicleReadProfiles.onDemandFields.
        FidEntry("acCtrlMode",            1000, 1077936146,   5, Decoder.INT_ENUM),
        FidEntry("acDefrostFront",        1000, 1077936150,   5, Decoder.INT_ENUM),
        FidEntry("acWindMode",            1000, 1077936152,   5, Decoder.INT_ENUM),
        FidEntry("seatHeatD",             1000, 702545948,    5, Decoder.INT_ENUM),
        FidEntry("seatVentD",             1000, 702545944,    5, Decoder.INT_ENUM),
        FidEntry("seatHeatP",             1000, 711983132,    5, Decoder.INT_ENUM),
        FidEntry("seatVentP",             1000, 711983128,    5, Decoder.INT_ENUM),
        FidEntry("lightSide",             1004, 950009864,    5, Decoder.INT_ENUM),
        // Extended on-demand telemetry. Candidate/imported addresses remain isolated from the
        // regular 71-FID snapshot so unsupported trims fail soft without permanent read cost.
        FidEntry("tireTempFL",             1007, 1246797848,   5, Decoder.INT_TEMP_C_WIDE),
        FidEntry("tireTempFR",             1007, 1246797860,   5, Decoder.INT_TEMP_C_WIDE),
        FidEntry("tireTempRL",             1007, 1246797872,   5, Decoder.INT_TEMP_C_WIDE),
        FidEntry("tireTempRR",             1007, 1246797884,   5, Decoder.INT_TEMP_C_WIDE),
        FidEntry("tireStateFL",            1016, -1728052957,  5, Decoder.INT_ENUM),
        FidEntry("tireStateFR",            1016, -1728052953,  5, Decoder.INT_ENUM),
        FidEntry("tireStateRL",            1016, -1728052949,  5, Decoder.INT_ENUM),
        FidEntry("tireStateRR",            1016, -1728052945,  5, Decoder.INT_ENUM),
        FidEntry("lightFrontFog",          1004, 1011875870,   5, Decoder.INT_ENUM),
        FidEntry("lightRearFog",           1004, 950009872,    5, Decoder.INT_ENUM),
        FidEntry("lightBrake",             1004, 950009874,    5, Decoder.INT_ENUM),
        FidEntry("lightReversing",         1004, 950009876,    5, Decoder.INT_ENUM),
        FidEntry("accelPedalDepth",        1013, 874512392,    5, Decoder.INT_PERCENT),
        FidEntry("epbState",               1011, 562036753,    5, Decoder.INT_ENUM),
        FidEntry("brakeState",             1011, 692060194,    5, Decoder.INT_ENUM),
        FidEntry("ePedalMode",             1006, 888143930,    5, Decoder.INT_ENUM),
        FidEntry("acDefrostRear",          1001, 1128267825,   5, Decoder.INT_ENUM),
        FidEntry("steeringHeat",           1000, 1116733454,   5, Decoder.INT_ENUM),
        FidEntry("sunshadePos",            1001, 1101004816,   5, Decoder.INT_PERCENT),
        FidEntry("mirrorFoldState",        1047, 1011875887,   5, Decoder.INT_ENUM),
        // ADAS feature states. These remain raw BYD enums because encodings can differ by
        // platform generation; UI and automation expose the source value without remapping.
        FidEntry("adasAebState",            1038, 852492332,    5, Decoder.INT_ENUM),
        FidEntry("adasAvhState",            1038, 304087110,    5, Decoder.INT_ENUM),
        FidEntry("adasBsdState",            1038, 1098907656,   5, Decoder.INT_ENUM),
        FidEntry("adasEspState",            1038, 305135676,    5, Decoder.INT_ENUM),
        FidEntry("adasAccGrayState",        1038, 535826448,    5, Decoder.INT_ENUM),
        FidEntry("adasNoaGrayState",        1038, 535826458,    5, Decoder.INT_ENUM),
        FidEntry("adasMnoaStatus",          1038, 790643093,    5, Decoder.INT_ENUM),
        FidEntry("adasExecSpeed",           1038, 513807420,    5, Decoder.INT_RAW),
        // Powertrain diagnostics. Values are read only while the vehicle-data page is visible
        // or when an enabled automation explicitly consumes one of the fields.
        FidEntry("frontMotorSpeed",         1012, 1141899272,   5, Decoder.INT_RAW),
        FidEntry("rearMotorSpeed",          1012, 621805576,    5, Decoder.INT_RAW),
        FidEntry("frontMotorTemp",          1039, 1154482192,   5, Decoder.INT_TEMP_C_WIDE),
        FidEntry("rearMotorTemp",           1039, 1155530768,   5, Decoder.INT_TEMP_C_WIDE),
        FidEntry("frontMotorCurrent",       1009, 1186988040,   5, Decoder.INT_SCALED, scale = 0.1),
        FidEntry("rearMotorCurrent",        1009, 1186988056,   5, Decoder.INT_SCALED, scale = 0.1),
        FidEntry("engineRpm",               1012, 339738642,    5, Decoder.INT_RAW),
        FidEntry("waterTemp",               1014, 839263,       5, Decoder.INT_TEMP_C_WIDE),
    )
}
