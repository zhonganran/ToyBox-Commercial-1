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
    val chargingStatus: Int?,
    val batteryCapacityKwh: Double?,
    val totalElecConsumption: Double?,
    val voltage12v: Double?,
    val maxCellVoltage: Double?,
    val minCellVoltage: Double?,
    val exteriorTemp: Int?,
    // Automation params (v2.2.0)
    val gear: Int?,               // 1=P, 2=R, 3=N, 4=D
    val powerState: Int?,         // 0=OFF, 1=ON, 2=DRIVE
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
    val trunk: Int?,              // 0=closed, 1=open
    val hood: Int?,               // 0=closed, 1=open
    val seatbeltFL: Int?,         // 0=unbuckled, 1=buckled, 2=invalid
    val lockFL: Int?,             // 1=unlocked, 2=locked
    val tirePressFL: Int?,        // kPa
    val tirePressFR: Int?,
    val tirePressRL: Int?,
    val tirePressRR: Int?,
    val driveMode: Int?,          // 1=ECO, 2=SPORT
    val workMode: Int?,           // 0=stop, 1=EV, 2=forced EV, 3=HEV
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
    val chargingCapCover: Int? = null,
    val fuelCap: Int? = null,
    val seatbeltFR: Int? = null,
    val seatbeltRL: Int? = null,
    val seatbeltRR: Int? = null,
    val lockFR: Int? = null,
    val lockRL: Int? = null,
    val lockRR: Int? = null,
    val lightHigh: Int? = null,
    val hazardLights: Int? = null,
    val turnSignalFlash: Int? = null,
    val adasAccMode: Int? = null,
    val adasLccNcaStatus: Int? = null,
    val adasTjaState: Int? = null,
    val laneKeepState: Int? = null,
    val accCruiseState: Int? = null,
    val accCruisingSpeed: Int? = null,
    val brakePedalDepth: Int? = null,    // 0-100%
    val turnSignalLeft: Int? = null,     // 0=off, 1=on
    val turnSignalRight: Int? = null,    // 0=off, 1=on
)
