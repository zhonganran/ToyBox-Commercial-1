package com.byd.wjx.domain.insight

import com.byd.wjx.data.local.dao.BatterySnapshotDao
import com.byd.wjx.data.local.dao.ChargeDao
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.local.entity.IdleDrainEntity
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.data.repository.VehicleStateRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalInsightStatsAggregator @Inject constructor(
    private val tripDao: TripDao,
    private val chargeDao: ChargeDao,
    private val idleDrainDao: IdleDrainDao,
    private val batterySnapshotDao: BatterySnapshotDao,
    private val vehicleApi: VehicleApi,
    private val vehicleStateRepository: VehicleStateRepository,
) {
    suspend fun build(windowDays: Int = DEFAULT_WINDOW_DAYS): LocalInsightStats {
        val now = System.currentTimeMillis()
        val windowMs = windowDays.coerceAtLeast(1) * DAY_MS
        val recentFrom = now - windowMs
        val previousFrom = recentFrom - windowMs

        val recentTrips = tripDao.getByDateRange(recentFrom, now).first().eligibleTrips()
        val previousTrips = tripDao.getByDateRange(previousFrom, recentFrom - 1).first().eligibleTrips()
        val recentCharges = chargeDao.getByDateRange(recentFrom, now).first().eligibleCharges()
        val idleDrains = idleDrainDao.getByDateRange(recentFrom, now).first()
        val batterySnapshots = batterySnapshotDao.getRecent(100).first()
            .filter { it.timestamp in recentFrom..now }
        val cachedData = vehicleStateRepository.fullSnapshot.value?.data
        val snapshotData = cachedData ?: runCatching { vehicleApi.readSnapshot() }.getOrNull()
        val liveBattery = if (snapshotData?.voltage12v == null && cachedData?.voltage12v == null) {
            runCatching { vehicleApi.readBatterySnapshot() }.getOrNull()
        } else {
            null
        }
        val liveVoltage12v = (snapshotData?.voltage12v ?: cachedData?.voltage12v ?: liveBattery?.voltage12v?.toDouble())
            ?.takeIf { it in 8.0..16.5 }
        val cellData = listOfNotNull(snapshotData, cachedData)
            .firstOrNull { it.minCellVoltage != null && it.maxCellVoltage != null }
        val liveCellDelta = cellData?.let { data ->
            val min = data.minCellVoltage
            val max = data.maxCellVoltage
            if (min != null && max != null && max >= min) max - min else null
        }?.takeIf { it in 0.0..1.0 }

        val recentTripTotals = recentTrips.toTripTotals()
        val previousTripTotals = previousTrips.toTripTotals()
        val chargeKwh = recentCharges.sumOf { it.energyKwh() }
        val chargeCost = recentCharges.sumOf { it.cost ?: 0.0 }
        val acKwh = recentCharges
            .filter { it.type?.uppercase(Locale.ROOT) == "AC" }
            .sumOf { it.energyKwh() }
        val dcKwh = recentCharges
            .filter { it.type?.uppercase(Locale.ROOT) == "DC" }
            .sumOf { it.energyKwh() }
        val acCharges = recentCharges.filter { it.type?.uppercase(Locale.ROOT) == "AC" }
        val dcCharges = recentCharges.filter { it.type?.uppercase(Locale.ROOT) == "DC" }
        val acCost = acCharges.sumOf { it.cost ?: 0.0 }
        val dcCost = dcCharges.sumOf { it.cost ?: 0.0 }
        val nightDrainKwh = idleDrains.nightDrainKwh()
        val idleDrainKwh = idleDrains.sumOf { it.kwhConsumed ?: 0.0 }

        val voltageSamples = buildList {
            liveVoltage12v?.let(::add)
            recentCharges.mapNotNullTo(this) { it.voltage12v?.takeIf { voltage -> voltage in 8.0..16.5 } }
        }
        val voltageTrend = recentCharges
            .mapNotNull { charge -> charge.voltage12v?.takeIf { it in 8.0..16.5 }?.let { charge.startTs to it } }
            .sortedBy { it.first }
            .v12TrendDelta()
        val cellDeltas = buildList {
            recentCharges.forEach { charge ->
                val min = charge.cellVoltageMin
                val max = charge.cellVoltageMax
                if (min != null && max != null && max >= min) add(max - min)
            }
            batterySnapshots.mapNotNullTo(this) { it.cellDeltaV }
            liveCellDelta?.let(::add)
        }.filter { it in 0.0..1.0 }
        val batteryTemps = buildList {
            recentTrips.mapNotNullTo(this) { it.batTempAvg }
            recentCharges.mapNotNullTo(this) { it.batTempAvg }
            batterySnapshots.mapNotNullTo(this) { it.batTempAvg }
            (snapshotData?.avgBatTemp ?: cachedData?.avgBatTemp)?.toDouble()?.let(::add)
        }.filter { it in -40.0..90.0 }
        val exteriorTemps = buildList {
            recentTrips.mapNotNullTo(this) { it.exteriorTemp?.toDouble() ?: it.tempAvgC }
            recentCharges.mapNotNullTo(this) { it.exteriorTemp?.toDouble() }
            (snapshotData?.exteriorTemp ?: cachedData?.exteriorTemp)?.toDouble()?.let(::add)
        }.filter { it in -50.0..60.0 }
        val tripSamples = recentTrips.mapNotNull { it.toTripSample() }
            .filter { it.distanceKm >= 3.0 && it.consumptionKwhPer100Km in 1.0..60.0 }

        return LocalInsightStats(
            generatedAt = now,
            windowDays = windowDays,
            recentTripCount = recentTrips.size,
            previousTripCount = previousTrips.size,
            recentDistanceKm = recentTripTotals.distanceKm,
            previousDistanceKm = previousTripTotals.distanceKm,
            recentKwh = recentTripTotals.kwh,
            previousKwh = previousTripTotals.kwh,
            recentCost = recentTripTotals.cost,
            previousCost = previousTripTotals.cost,
            recentAvgSpeedKmh = recentTrips.weightedAvgSpeed(),
            recentAvgConsumption = recentTripTotals.avgConsumption,
            previousAvgConsumption = previousTripTotals.avgConsumption,
            shortTripCount = recentTrips.count { (it.distanceKm ?: 0.0) in 0.5..5.0 },
            coldTripCount = recentTrips.count { (it.exteriorTemp ?: it.tempAvgC?.toInt())?.let { temp -> temp <= 5 } == true },
            hotTripCount = recentTrips.count { (it.exteriorTemp ?: it.tempAvgC?.toInt())?.let { temp -> temp >= 32 } == true },
            idleDrainKwh = idleDrainKwh,
            idleDrainHours = idleDrains.sumOf {
                val endTs = it.endTs ?: now
                (endTs - it.startTs).coerceAtLeast(0L) / 3_600_000.0
            },
            chargeCount = recentCharges.size,
            chargeKwh = chargeKwh,
            chargeCost = chargeCost,
            acChargeKwh = acKwh,
            dcChargeKwh = dcKwh,
            acChargeCount = acCharges.size,
            dcChargeCount = dcCharges.size,
            acCostPerKwh = if (acKwh > 0.1 && acCost > 0.0) acCost / acKwh else null,
            dcCostPerKwh = if (dcKwh > 0.1 && dcCost > 0.0) dcCost / dcKwh else null,
            nightIdleDrainKwh = nightDrainKwh,
            nightIdleDrainSharePercent = if (idleDrainKwh > 0.1) nightDrainKwh / idleDrainKwh * 100.0 else null,
            latestVoltage12v = voltageSamples.firstOrNull(),
            minVoltage12v = voltageSamples.minOrNull(),
            avgVoltage12v = voltageSamples.averageOrNull(),
            voltage12vTrendDelta = voltageTrend,
            maxCellDeltaV = cellDeltas.maxOrNull(),
            avgExteriorTempC = exteriorTemps.averageOrNull(),
            avgBatteryTempC = batteryTemps.averageOrNull(),
            bestTrip = tripSamples.minByOrNull { it.consumptionKwhPer100Km },
            worstTrip = tripSamples.maxByOrNull { it.consumptionKwhPer100Km },
        )
    }

    private fun List<TripEntity>.eligibleTrips(): List<TripEntity> =
        filter { (it.distanceKm ?: 0.0) >= 0.5 }

    private fun List<ChargeEntity>.eligibleCharges(): List<ChargeEntity> =
        filter { it.energyKwh() > 0.05 || it.status == "COMPLETED" }

    private fun List<TripEntity>.toTripTotals(): TripTotals {
        var distance = 0.0
        var kwh = 0.0
        var cost = 0.0
        for (trip in this) {
            val tripDistance = trip.distanceKm ?: 0.0
            val tripKwh = trip.energyKwh() ?: 0.0
            distance += tripDistance
            kwh += tripKwh
            cost += trip.cost ?: 0.0
        }
        return TripTotals(
            distanceKm = distance,
            kwh = kwh,
            cost = cost,
            avgConsumption = if (distance > 0.0 && kwh > 0.0) kwh / distance * 100.0 else null,
        )
    }

    private fun TripEntity.toTripSample(): InsightTripSample? {
        val distance = distanceKm ?: return null
        val consumption = normalizedConsumption() ?: return null
        return InsightTripSample(startTs = startTs, distanceKm = distance, consumptionKwhPer100Km = consumption)
    }

    private fun TripEntity.energyKwh(): Double? {
        kwhConsumed?.takeIf { it > 0.0 }?.let { return it }
        val distance = distanceKm ?: return null
        val consumption = normalizedConsumption() ?: return null
        return distance * consumption / 100.0
    }

    private fun TripEntity.normalizedConsumption(): Double? {
        kwhPer100km?.takeIf { it in 1.0..60.0 }?.let { return it }
        val distance = distanceKm ?: return null
        val kwh = kwhConsumed ?: return null
        if (distance <= 0.0 || kwh <= 0.0) return null
        return (kwh / distance * 100.0).takeIf { it in 1.0..60.0 }
    }

    private fun ChargeEntity.energyKwh(): Double =
        (kwhCharged ?: kwhChargedSoc ?: 0.0).coerceAtLeast(0.0)

    private fun List<TripEntity>.weightedAvgSpeed(): Double? {
        var distance = 0.0
        var speedDistance = 0.0
        for (trip in this) {
            val km = trip.distanceKm ?: continue
            val speed = trip.avgSpeedKmh ?: continue
            if (km <= 0.0 || speed !in 1.0..180.0) continue
            distance += km
            speedDistance += speed * km
        }
        return if (distance > 0.0) speedDistance / distance else null
    }

    private fun List<IdleDrainEntity>.nightDrainKwh(): Double {
        val calendar = Calendar.getInstance()
        return sumOf { drain ->
            val kwh = drain.kwhConsumed ?: return@sumOf 0.0
            calendar.timeInMillis = drain.startTs
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 6) kwh else 0.0
        }
    }

    private fun List<Pair<Long, Double>>.v12TrendDelta(): Double? {
        if (size < 4) return null
        val mid = size / 2
        val first = take(mid).map { it.second }.averageOrNull() ?: return null
        val second = drop(mid).map { it.second }.averageOrNull() ?: return null
        return second - first
    }

    private fun List<Double>.averageOrNull(): Double? =
        if (isEmpty()) null else average()

    private data class TripTotals(
        val distanceKm: Double,
        val kwh: Double,
        val cost: Double,
        val avgConsumption: Double?,
    )

    private companion object {
        const val DEFAULT_WINDOW_DAYS = 7
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
