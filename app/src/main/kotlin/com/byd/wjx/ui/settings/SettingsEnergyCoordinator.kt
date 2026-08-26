package com.byd.wjx.ui.settings

import com.byd.wjx.data.local.HistoryImporter
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

internal data class VehicleEnergySettings(
    val batteryCapacity: String,
    val fuelTankCapacity: String,
    val homeTariff: String,
    val dcTariff: String,
    val fuelPricePerLiter: String,
    val tripCostTariff: String,
    val consumptionGood: String,
    val consumptionBad: String,
)

internal data class NormalizedEnergyCapacities(
    val batteryCapacity: String,
    val fuelTankCapacity: String,
)

class SettingsEnergyCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val tripRepository: TripRepository,
    private val historyImporter: HistoryImporter,
) {
    internal suspend fun load(): VehicleEnergySettings = VehicleEnergySettings(
        batteryCapacity = settingsRepository.getString(
            SettingsRepository.KEY_BATTERY_CAPACITY,
            SettingsRepository.DEFAULT_BATTERY_CAPACITY,
        ),
        fuelTankCapacity = settingsRepository.getString(
            SettingsRepository.KEY_FUEL_TANK_CAPACITY,
            SettingsRepository.DEFAULT_FUEL_TANK_CAPACITY,
        ),
        homeTariff = settingsRepository.getString(
            SettingsRepository.KEY_HOME_TARIFF,
            SettingsRepository.DEFAULT_HOME_TARIFF,
        ),
        dcTariff = settingsRepository.getString(
            SettingsRepository.KEY_DC_TARIFF,
            SettingsRepository.DEFAULT_DC_TARIFF,
        ),
        fuelPricePerLiter = settingsRepository.getString(
            SettingsRepository.KEY_FUEL_PRICE_PER_LITER,
            SettingsRepository.DEFAULT_FUEL_PRICE_PER_LITER,
        ),
        tripCostTariff = settingsRepository.getTripCostTariffKey(),
        consumptionGood = settingsRepository.getString(
            SettingsRepository.KEY_CONSUMPTION_GOOD,
            SettingsRepository.DEFAULT_CONSUMPTION_GOOD,
        ),
        consumptionBad = settingsRepository.getString(
            SettingsRepository.KEY_CONSUMPTION_BAD,
            SettingsRepository.DEFAULT_CONSUMPTION_BAD,
        ),
    )

    internal fun normalizeCapacities(
        batteryCapacity: String,
        fuelTankCapacity: String,
    ): NormalizedEnergyCapacities? {
        val normalizedBattery = batteryCapacity.normalizedPositiveNumber() ?: return null
        val normalizedFuelTank = fuelTankCapacity.replace(',', '.').trim()
        val fuelTankValid = normalizedFuelTank.isEmpty() ||
            normalizedFuelTank.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true
        if (!fuelTankValid) return null
        return NormalizedEnergyCapacities(normalizedBattery, normalizedFuelTank)
    }

    internal suspend fun saveCapacities(capacities: NormalizedEnergyCapacities) {
        settingsRepository.setStrings(
            mapOf(
                SettingsRepository.KEY_BATTERY_CAPACITY to capacities.batteryCapacity,
                SettingsRepository.KEY_FUEL_TANK_CAPACITY to capacities.fuelTankCapacity,
            ),
        )
    }

    internal suspend fun saveTariffs(
        homeTariff: String,
        dcTariff: String,
        fuelPricePerLiter: String,
    ) {
        settingsRepository.setStrings(
            mapOf(
                SettingsRepository.KEY_HOME_TARIFF to homeTariff,
                SettingsRepository.KEY_DC_TARIFF to dcTariff,
                SettingsRepository.KEY_FUEL_PRICE_PER_LITER to fuelPricePerLiter,
            ),
        )
        historyImporter.calculateMissingCosts(
            settingsRepository.getTripCostTariff(),
            settingsRepository.getFuelPricePerLiter(),
        )
    }

    internal suspend fun recalculateAllCosts(): Int {
        val tariff = settingsRepository.getTripCostTariff()
        val fuelPrice = settingsRepository.getFuelPricePerLiter()
        val allTrips = tripRepository.getAllTrips().firstOrNull().orEmpty()
        var count = 0
        allTrips.forEach { trip ->
            val electricCost = trip.kwhConsumed?.let { it * tariff }
            val fuelCost = trip.fuelConsumedL?.let { it * fuelPrice }
            if (electricCost == null && fuelCost == null) return@forEach
            tripRepository.updateTrip(trip.copy(cost = electricCost, fuelCost = fuelCost))
            count += 1
        }
        return count
    }

    internal suspend fun saveTripCostTariff(value: String) {
        settingsRepository.setString(SettingsRepository.KEY_TRIP_COST_TARIFF, value)
    }

    internal suspend fun saveConsumptionGood(value: String) {
        settingsRepository.setString(SettingsRepository.KEY_CONSUMPTION_GOOD, value)
    }

    internal suspend fun saveConsumptionBad(value: String) {
        settingsRepository.setString(SettingsRepository.KEY_CONSUMPTION_BAD, value)
    }

    private fun String.normalizedPositiveNumber(): String? =
        replace(',', '.').trim().takeIf { normalized ->
            normalized.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true
        }
}
