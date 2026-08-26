package com.byd.wjx.ui.settings

import android.content.Context
import com.byd.wjx.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns editable energy values and persisted application preference UI state. */
internal class SettingsEnergyUiController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val energyCoordinator: SettingsEnergyCoordinator,
    private val preferenceCoordinator: SettingsPreferenceCoordinator,
) {
    fun load() {
        scope.launch {
            val energy = energyCoordinator.load()
            val appPreferences = preferenceCoordinator.load()
            state.update {
                it.copy(
                    energy = SettingsEnergyUiState(energy),
                    appPreferences = SettingsAppPreferencesUiState(appPreferences),
                )
            }
        }
    }

    fun updateBatteryCapacity(value: String) {
        updateEnergy { it.copy(batteryCapacity = value, capacitySaveStatus = null) }
    }

    fun updateFuelTankCapacity(value: String) {
        updateEnergy { it.copy(fuelTankCapacity = value, capacitySaveStatus = null) }
    }

    fun saveCapacities() {
        val energy = state.value.energy
        val capacities = energyCoordinator.normalizeCapacities(
            energy.batteryCapacity,
            energy.fuelTankCapacity,
        )
        if (capacities == null) {
            updateEnergy {
                it.copy(capacitySaveStatus = context.getString(R.string.settings_capacity_invalid))
            }
            return
        }
        scope.launch {
            energyCoordinator.saveCapacities(capacities)
            updateEnergy {
                it.copy(capacitySaveStatus = context.getString(R.string.settings_saved))
            }
            delay(STATUS_CLEAR_DELAY_MS)
            updateEnergy { it.copy(capacitySaveStatus = null) }
        }
    }

    fun setThemeMode(mode: String) {
        updateAppPreferences { it.copy(themeMode = mode) }
        scope.launch { preferenceCoordinator.setThemeMode(mode) }
    }

    fun setAppWallpaper(wallpaper: String) {
        updateAppPreferences { it.copy(appWallpaper = wallpaper) }
        scope.launch { preferenceCoordinator.setAppWallpaper(wallpaper) }
    }

    fun updateHomeTariff(value: String) {
        updateEnergy { it.copy(homeTariff = value) }
    }

    fun updateDcTariff(value: String) {
        updateEnergy { it.copy(dcTariff = value) }
    }

    fun updateFuelPricePerLiter(value: String) {
        updateEnergy { it.copy(fuelPricePerLiter = value) }
    }

    fun saveTariffs() {
        val energy = state.value.energy
        scope.launch {
            energyCoordinator.saveTariffs(
                homeTariff = energy.homeTariff,
                dcTariff = energy.dcTariff,
                fuelPricePerLiter = energy.fuelPricePerLiter,
            )
            updateEnergy { it.copy(tariffSaveStatus = context.getString(R.string.settings_saved)) }
            delay(STATUS_CLEAR_DELAY_MS)
            updateEnergy { it.copy(tariffSaveStatus = null) }
        }
    }

    fun recalculateAllCosts() {
        scope.launch {
            val count = energyCoordinator.recalculateAllCosts()
            updateEnergy {
                it.copy(recalcStatus = context.getString(R.string.settings_recalc_done, count))
            }
            delay(RECALC_STATUS_CLEAR_DELAY_MS)
            updateEnergy { it.copy(recalcStatus = null) }
        }
    }

    fun saveTripCostTariff(value: String) {
        updateEnergy { it.copy(tripCostTariff = value) }
        scope.launch { energyCoordinator.saveTripCostTariff(value) }
    }

    fun showRecalcConfirm() {
        updateEnergy { it.copy(showRecalcConfirm = true) }
    }

    fun hideRecalcConfirm() {
        updateEnergy { it.copy(showRecalcConfirm = false) }
    }

    fun confirmRecalc() {
        hideRecalcConfirm()
        recalculateAllCosts()
    }

    fun setTripGpsRecordingEnabled(enabled: Boolean) {
        updateAppPreferences { it.copy(tripGpsRecordingEnabled = enabled) }
        scope.launch { preferenceCoordinator.setTripGpsRecordingEnabled(enabled) }
    }

    fun saveConsumptionGood(value: String) {
        updateEnergy { it.copy(consumptionGood = value) }
        scope.launch { energyCoordinator.saveConsumptionGood(value) }
    }

    fun saveConsumptionBad(value: String) {
        updateEnergy { it.copy(consumptionBad = value) }
        scope.launch { energyCoordinator.saveConsumptionBad(value) }
    }

    private fun updateEnergy(transform: (SettingsEnergyUiState) -> SettingsEnergyUiState) {
        state.update { it.copy(energy = transform(it.energy)) }
    }

    private fun updateAppPreferences(
        transform: (SettingsAppPreferencesUiState) -> SettingsAppPreferencesUiState,
    ) {
        state.update { it.copy(appPreferences = transform(it.appPreferences)) }
    }

    private companion object {
        const val STATUS_CLEAR_DELAY_MS = 2_000L
        const val RECALC_STATUS_CLEAR_DELAY_MS = 3_000L
    }
}
