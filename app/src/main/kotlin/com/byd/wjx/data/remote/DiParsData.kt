package com.byd.wjx.data.remote

data class DiParsData(
    val soc: Int?,
    val speed: Int?,
    val mileage: Double?,
    val power: Double?,
    val chargeGunState: Int?,
    val maxBatTemp: Int?,
    val avgBatTemp: Int?,
    val minBatTemp: Int?,
    val chargingStatus: Int?,     // BYD BMS: 0=ready, 1=charging, 2=finished, 3=discharge, 13=paused
    val batteryCapacityKwh: Double?,
    val totalElecConsumption: Double?,
    val voltage12v: Double?,
    val maxCellVoltage: Double?,
    val minCellVoltage: Double?,
    val exteriorTemp: Int?,
    // Automation params (v2.2.0)
    val gear: Int?,               // 1=P, 2=R, 3=N, 4=D
    val powerState: Int?,         // BYD power level: 0=OFF, 1=ACC, 2=ON, 3=OK, 4=FAKE_OK
    val insideTemp: Int?,
    val acStatus: Int?,           // 0=OFF, 1=ON
    val acTemp: Int?,
    val fanLevel: Int?,
    val acCirc: Int?,             // 0=external, 1=internal
    val doorFL: Int?,             // 0=closed, 1=open
    val doorFR: Int?,
    val doorRL: Int?,
    val doorRR: Int?,
    val windowFL: Int?,           // 0-100%
    val windowFR: Int?,
    val windowRL: Int?,
    val windowRR: Int?,
    val sunroof: Int?,            // 0-100%
    val trunk: Int?,              // 1=open, 2=closed, 3=intermediate/paused
    val hood: Int?,               // 0=closed, 1=open
    val seatbeltFL: Int?,         // 0=unbuckled, 1=buckled, 2=invalid
    val lockFL: Int?,             // 1=unlocked, 2=locked
    val tirePressFL: Int?,        // kPa
    val tirePressFR: Int?,
    val tirePressRL: Int?,
    val tirePressRR: Int?,
    val driveMode: Int?,          // 1=NORMAL, 2=ECO, 3=SPORT, 4=SNOW, 5=MUD, 6=SAND
    val workMode: Int?,           // 0=stop, 1=EV, 2=forced EV, 3=HEV, 4=fuel, 5=keep
    val autoPark: Int?,           // 0=disabled, 1=standby, 2=active
    val rain: Int?,
    val lightLow: Int?,           // 0=OFF, 1=ON
    val drl: Int?,                // 0=invalid, 1=ON, 2=OFF
    val adasApaParkingStatus: Int? = null, // 0=off, 1=searching, 2=parking-ready, 3=parkout/pause, 4=parking-active, 5/6=complete, 7=selecting
    val fuelLevel: Int? = null,             // 0-100%
    val elecDrivingRangeKm: Int? = null,
    val fuelDrivingRangeKm: Int? = null,
    val elecConPer100km: Double? = null,
    val fuelConPer100km: Double? = null,
    val currentTripElecKwh: Double? = null,
    val currentTripFuelL: Double? = null,
    val chargingCapCover: Int? = null, // 1=closed, 2=open
    val fuelCap: Int? = null,          // 1=closed, 2=open
    val seatbeltFR: Int? = null,       // 0=unbuckled, 1=buckled, 2=invalid
    val seatbeltRL: Int? = null,
    val seatbeltRR: Int? = null,
    val lockFR: Int? = null,           // 1=unlocked, 2=locked
    val lockRL: Int? = null,
    val lockRR: Int? = null,
    val lightHigh: Int? = null,
    val hazardLights: Int? = null,
    val turnSignalFlash: Int? = null,    // stable switch enum: 1=off, 2=left, 4=right, 6=hazard
    val adasAccMode: Int? = null,
    val adasLccNcaStatus: Int? = null,
    val adasTjaState: Int? = null,
    val laneKeepState: Int? = null,
    val accCruiseState: Int? = null,
    val accCruisingSpeed: Int? = null,
    val brakePedalDepth: Int? = null,    // 0-100%
    val turnSignalLeft: Int? = null,     // 0=off, 1=on
    val turnSignalRight: Int? = null,    // 0=off, 1=on
    // Validated comfort/light reads. These fields are selected on demand by enabled
    // automations and stay out of the regular 71-FID full snapshot.
    val acCtrlMode: Int? = null,
    val acDefrostFront: Int? = null,
    val acWindMode: Int? = null,
    val seatHeatD: Int? = null,           // official raw level: 0=off, 1..5=level
    val seatVentD: Int? = null,
    val seatHeatP: Int? = null,
    val seatVentP: Int? = null,
    val lightSide: Int? = null,
    // Extended on-demand telemetry. Raw BYD enums are retained without local remapping.
    val tireTempFL: Int? = null,
    val tireTempFR: Int? = null,
    val tireTempRL: Int? = null,
    val tireTempRR: Int? = null,
    val lightFrontFog: Int? = null,
    val lightRearFog: Int? = null,
    val lightBrake: Int? = null,
    val lightReversing: Int? = null,
    val accelPedalDepth: Int? = null,
    val epbState: Int? = null,
    val brakeState: Int? = null,
    val ePedalMode: Int? = null,
    val acDefrostRear: Int? = null,
    val steeringHeat: Int? = null,
    val sunshadePos: Int? = null,
    val mirrorFoldState: Int? = null,
    // High-value on-demand safety/ADAS telemetry. Official raw enums are retained.
    val adasAebState: Int? = null,
    val adasAvhState: Int? = null,
    val adasBsdState: Int? = null,
    val adasEspState: Int? = null,
    val adasAccGrayState: Int? = null,
    val adasNoaGrayState: Int? = null,
    val adasMnoaStatus: Int? = null,
    val adasExecSpeed: Int? = null,
    // Powertrain diagnostics. Motor current keeps its signed 0.1 A scaling.
    val frontMotorSpeed: Int? = null,
    val rearMotorSpeed: Int? = null,
    val frontMotorTemp: Int? = null,
    val rearMotorTemp: Int? = null,
    val frontMotorCurrent: Double? = null,
    val rearMotorCurrent: Double? = null,
    val engineRpm: Int? = null,
    val waterTemp: Int? = null,
    // TPMS status values are deliberately not locally re-enumerated.
    val tireStateFL: Int? = null,
    val tireStateFR: Int? = null,
    val tireStateRL: Int? = null,
    val tireStateRR: Int? = null,
    // Detailed BYD power-flow state. Used to distinguish EV/HEV/assist regeneration
    // and engine generation; the official raw enum is retained without remapping.
    val energyState: Int? = null,
    // BMS state of health, 0..100%. Shared full snapshot field; no separate persistence.
    val soh: Int? = null,
)
