package com.byd.wjx.ui.dashboard

import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.dashboard.DashboardLiveSnapshot
import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.domain.vehicle.totalVehicleRangeKm

/** Maps repository-facing live data into the dashboard presentation state. */
internal object DashboardLiveStateMapper {

    fun map(current: DashboardUiState, snapshot: DashboardLiveSnapshot): DashboardUiState {
        val data = snapshot.data
        val newSoc = data?.soc ?: current.soc
        val newFuelLevel = data?.fuelLevel ?: current.fuelLevel
        val electricRangeKm = data?.elecDrivingRangeKm ?: current.electricRangeKm
        val fuelRangeKm = data?.fuelDrivingRangeKm ?: current.fuelRangeKm
        val combinedRangeKm = totalVehicleRangeKm(electricRangeKm, fuelRangeKm)
        val batteryRemaining = remainingAmount(newSoc, snapshot.batteryCapacityKwh)
        val fuelRemaining = remainingAmount(newFuelLevel, snapshot.fuelTankCapacityLiters)
        val fuelConsumption = data?.fuelConPer100km.validFuelConsumption()
            ?: current.fuelConPer100km
        val tripElecKwh = data?.currentTripElecKwh ?: current.currentTripElecKwh
        val tripFuelL = data?.currentTripFuelL ?: current.currentTripFuelL

        return current.copy(
            soc = newSoc,
            speed = data?.speed ?: current.speed,
            power = data?.power ?: current.power,
            odometer = data?.mileage ?: current.odometer,
            isServiceRunning = snapshot.running,
            avgBatTemp = data?.avgBatTemp ?: current.avgBatTemp,
            maxBatTemp = data?.maxBatTemp ?: current.maxBatTemp,
            minBatTemp = data?.minBatTemp ?: current.minBatTemp,
            cellVoltageMin = data?.minCellVoltage ?: current.cellVoltageMin,
            cellVoltageMax = data?.maxCellVoltage ?: current.cellVoltageMax,
            cellVoltageDelta = if (data?.maxCellVoltage != null && data.minCellVoltage != null) {
                data.maxCellVoltage - data.minCellVoltage
            } else {
                current.cellVoltageDelta
            },
            voltage12v = data?.voltage12v ?: current.voltage12v,
            currentSoh = data?.soh?.toFloat() ?: current.currentSoh,
            currentLifetimeKm = data?.mileage?.toFloat() ?: current.currentLifetimeKm,
            currentLifetimeKwh = data?.totalElecConsumption?.toFloat() ?: current.currentLifetimeKwh,
            exteriorTemp = data?.exteriorTemp ?: current.exteriorTemp,
            batteryHealthStatus = calculateBatteryStatus(data, current),
            voltage12vStatus = calculate12vStatus(data?.voltage12v ?: current.voltage12v),
            electricRangeKm = electricRangeKm,
            fuelRangeKm = fuelRangeKm,
            combinedRangeKm = combinedRangeKm,
            fuelLevel = newFuelLevel,
            batteryRemainingKwh = batteryRemaining,
            fuelRemainingLiters = fuelRemaining,
            elecConPer100km = data?.elecConPer100km.validElectricConsumption()
                ?: current.elecConPer100km,
            fuelConPer100km = fuelConsumption,
            currentTripElecKwh = tripElecKwh,
            currentTripFuelL = tripFuelL,
            currentTripElecCost = tripElecKwh.validUsage()?.times(snapshot.tripElectricTariff),
            currentTripFuelCost = tripFuelL.validUsage()?.times(snapshot.fuelPricePerLiter),
            vehicleDataConnected = snapshot.connected,
            lastVehicleDataAt = snapshot.dataUpdatedAt ?: current.lastVehicleDataAt,
            insideTemp = data?.insideTemp ?: current.insideTemp,
            tripDistanceKm = snapshot.tripDistanceKm,
            sessionStartedAt = snapshot.sessionStartedAt,
            consumption = snapshot.consumption.displayValue,
            consumptionTrend = snapshot.consumption.trend,
            // Prefer explicit BMS charging status, then fall back to gun-connected + negative power.
            // Regen has gun=0, so it is filtered.
            isCharging = data?.let(::isChargingSample) ?: current.isCharging,
            gear = data?.gear ?: current.gear,
            powerState = data?.powerState ?: current.powerState,
            // A fresh full snapshot owns these enums, including an unavailable value.
            // Keeping the previous non-null mode would make the home screen lie after a failed
            // or unsupported FID read, or a real platform-mode transition.
            driveMode = if (data != null) data.driveMode else current.driveMode,
            workMode = if (data != null) data.workMode else current.workMode,
            energyState = data?.energyState ?: current.energyState,
            energyFlowCategory = snapshot.energyFlow.currentCategory,
            sessionEnergyFlow = snapshot.energyFlow.session,
            lifetimeEnergyFlow = snapshot.energyFlow.lifetime,
            chargeGunState = data?.chargeGunState ?: current.chargeGunState,
            chargingStatus = data?.chargingStatus ?: current.chargingStatus,
            chargingCapCover = data?.chargingCapCover ?: current.chargingCapCover,
            fuelCap = data?.fuelCap ?: current.fuelCap,
            acStatus = data?.acStatus ?: current.acStatus,
            acTemp = data?.acTemp ?: current.acTemp,
            fanLevel = data?.fanLevel ?: current.fanLevel,
            acCirc = data?.acCirc ?: current.acCirc,
            doorFL = data?.doorFL ?: current.doorFL,
            doorFR = data?.doorFR ?: current.doorFR,
            doorRL = data?.doorRL ?: current.doorRL,
            doorRR = data?.doorRR ?: current.doorRR,
            windowFL = data?.windowFL ?: current.windowFL,
            windowFR = data?.windowFR ?: current.windowFR,
            windowRL = data?.windowRL ?: current.windowRL,
            windowRR = data?.windowRR ?: current.windowRR,
            sunroof = data?.sunroof ?: current.sunroof,
            trunk = data?.trunk ?: current.trunk,
            hood = data?.hood ?: current.hood,
            lockFL = data?.lockFL ?: current.lockFL,
            lockFR = data?.lockFR ?: current.lockFR,
            lockRL = data?.lockRL ?: current.lockRL,
            lockRR = data?.lockRR ?: current.lockRR,
            seatbeltFL = data?.seatbeltFL ?: current.seatbeltFL,
            seatbeltFR = data?.seatbeltFR ?: current.seatbeltFR,
            seatbeltRL = data?.seatbeltRL ?: current.seatbeltRL,
            seatbeltRR = data?.seatbeltRR ?: current.seatbeltRR,
            tirePressFL = data?.tirePressFL ?: current.tirePressFL,
            tirePressFR = data?.tirePressFR ?: current.tirePressFR,
            tirePressRL = data?.tirePressRL ?: current.tirePressRL,
            tirePressRR = data?.tirePressRR ?: current.tirePressRR,
            lightLow = data?.lightLow ?: current.lightLow,
            lightHigh = data?.lightHigh ?: current.lightHigh,
            drl = data?.drl ?: current.drl,
            hazardLights = data?.hazardLights ?: current.hazardLights,
            turnSignalFlash = data?.turnSignalFlash ?: current.turnSignalFlash,
            adasApaParkingStatus = data?.adasApaParkingStatus ?: current.adasApaParkingStatus,
            adasAccMode = data?.adasAccMode ?: current.adasAccMode,
            adasLccNcaStatus = data?.adasLccNcaStatus ?: current.adasLccNcaStatus,
            adasTjaState = data?.adasTjaState ?: current.adasTjaState,
            laneKeepState = data?.laneKeepState ?: current.laneKeepState,
            accCruiseState = data?.accCruiseState ?: current.accCruiseState,
            accCruisingSpeed = data?.accCruisingSpeed ?: current.accCruisingSpeed,
        )
    }

    private fun calculateBatteryStatus(data: DiParsData?, current: DashboardUiState): String {
        val maxV = data?.maxCellVoltage ?: current.cellVoltageMax
        val minV = data?.minCellVoltage ?: current.cellVoltageMin
        val delta = if (maxV != null && minV != null) maxV - minV else null
        val temp = data?.avgBatTemp ?: current.avgBatTemp
        if (delta == null && temp == null) return current.batteryHealthStatus
        return when {
            (delta != null && delta > 0.10) || (temp != null && (temp < 5 || temp > 50)) -> "critical"
            (delta != null && delta > 0.05) || (temp != null && (temp < 5 || temp > 45)) -> "warning"
            else -> "ok"
        }
    }

    private fun calculate12vStatus(voltage: Double?): String {
        if (voltage == null) return "ok"
        return when {
            voltage < 11.8 -> "critical"
            voltage < 12.4 -> "warning"
            else -> "ok"
        }
    }

    private fun isChargingSample(data: DiParsData): Boolean {
        if (BydSignalSemantics.isCharging(data.chargingStatus)) return true
        return ChargeGunState.isConnected(data.chargeGunState) && (data.power ?: 0.0) < -0.3
    }
}

internal fun remainingAmount(percent: Int?, capacity: Double?): Double? {
    if (percent == null || percent !in 0..100) return null
    if (capacity == null || !capacity.isFinite() || capacity <= 0.0) return null
    return (capacity * percent / 100.0).coerceIn(0.0, capacity)
}

private fun Double?.validUsage(): Double? =
    this?.takeIf { it.isFinite() && it in 0.0..500.0 }

private fun Double?.validElectricConsumption(): Double? =
    this?.takeIf { it.isFinite() && it in 1.0..100.0 }

private fun Double?.validFuelConsumption(): Double? =
    this?.takeIf { it.isFinite() && it in 0.5..50.0 }
