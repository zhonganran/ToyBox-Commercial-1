package com.byd.wjx.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.data.system.SettingsPermissionHealthCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stable screen facade; domain-specific UI state transitions live in focused controllers. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    preferenceCoordinator: SettingsPreferenceCoordinator,
    energyCoordinator: SettingsEnergyCoordinator,
    activationCoordinator: SettingsActivationCoordinator,
    vehicleEventSoundCoordinator: VehicleEventSoundCoordinator,
    maintenanceCoordinator: SettingsMaintenanceCoordinator,
    permissionHealthCoordinator: SettingsPermissionHealthCoordinator,
    private val serviceQrCoordinator: SettingsServiceQrCoordinator,
    vehicleProfileCoordinator: SettingsVehicleProfileCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            maintenance = SettingsMaintenanceUiState(
                appVersion = maintenanceCoordinator.appVersion(),
                autoCheckUpdates = maintenanceCoordinator.isAutoCheckEnabled(),
            ),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val energyUi = SettingsEnergyUiController(
        context = appContext,
        scope = viewModelScope,
        state = _uiState,
        energyCoordinator = energyCoordinator,
        preferenceCoordinator = preferenceCoordinator,
    )
    private val vehicleUi = SettingsVehicleUiController(
        scope = viewModelScope,
        state = _uiState,
        eventSoundCoordinator = vehicleEventSoundCoordinator,
        vehicleProfileCoordinator = vehicleProfileCoordinator,
    )
    private val activationUi = SettingsActivationUiController(
        scope = viewModelScope,
        state = _uiState,
        coordinator = activationCoordinator,
        refreshVehicleEventSound = vehicleUi::refreshEventSoundState,
        syncVehicleEventSound = vehicleUi::syncEventSoundConfig,
    )
    private val maintenanceUi = SettingsMaintenanceUiController(
        context = appContext,
        scope = viewModelScope,
        state = _uiState,
        coordinator = maintenanceCoordinator,
    )
    private val permissionHealthUi = SettingsPermissionHealthUiController(
        scope = viewModelScope,
        state = _uiState,
        coordinator = permissionHealthCoordinator,
    )
    init {
        energyUi.load()
        vehicleUi.load()
        maintenanceUi.loadDiagnostics()
        permissionHealthUi.refresh()
        refreshActivation(refreshDeviceCode = true)
        refreshServiceQrCodes()
    }

    fun reloadLockSoundChoices() = vehicleUi.reloadEventSoundChoices()

    fun setLockSoundEnabled(event: LockSoundEvent, enabled: Boolean) =
        vehicleUi.setEventSoundEnabled(event, enabled)

    fun setLockSoundPath(event: LockSoundEvent, path: String) =
        vehicleUi.setEventSoundPath(event, path)

    fun importEventSound(uri: Uri) = vehicleUi.importEventSound(uri)

    fun deleteEventSound(path: String) = vehicleUi.deleteEventSound(path)

    fun syncLockSoundNow(reason: String) = vehicleUi.syncEventSoundNow(reason)

    fun refreshServiceQrCodes() {
        if (_uiState.value.serviceQr.loading) return
        viewModelScope.launch {
            serviceQrCoordinator.refreshUpdates(_uiState.value.serviceQr.codes).collect { serviceQr ->
                _uiState.update { it.copy(serviceQr = serviceQr) }
            }
        }
    }

    fun serviceQrImageUpdates(key: String, imageUrl: String): Flow<ServiceQrImageUpdate> =
        serviceQrCoordinator.imageUpdates(key, imageUrl)

    fun refreshActivation(refreshDeviceCode: Boolean = true) = activationUi.refresh(refreshDeviceCode)

    fun updateActivationCode(value: String) = activationUi.updateCode(value)

    fun activate() = activationUi.activate()

    fun startActivationPayment(paymentType: String) = activationUi.startPayment(paymentType)

    fun dismissActivationPayment() = activationUi.dismissPayment()

    fun updateBatteryCapacity(value: String) = energyUi.updateBatteryCapacity(value)

    fun updateFuelTankCapacity(value: String) = energyUi.updateFuelTankCapacity(value)

    fun saveEnergyCapacities() = energyUi.saveCapacities()

    fun setThemeMode(mode: String) = energyUi.setThemeMode(mode)

    fun setAppWallpaper(wallpaper: String) = energyUi.setAppWallpaper(wallpaper)

    fun updateHomeTariff(value: String) = energyUi.updateHomeTariff(value)

    fun updateDcTariff(value: String) = energyUi.updateDcTariff(value)

    fun updateFuelPricePerLiter(value: String) = energyUi.updateFuelPricePerLiter(value)

    fun saveTariffs() = energyUi.saveTariffs()

    fun recalculateAllCosts() = energyUi.recalculateAllCosts()

    fun saveTripCostTariff(value: String) = energyUi.saveTripCostTariff(value)

    fun showRecalcConfirm() = energyUi.showRecalcConfirm()

    fun hideRecalcConfirm() = energyUi.hideRecalcConfirm()

    fun confirmRecalc() = energyUi.confirmRecalc()

    fun setTripGpsRecordingEnabled(enabled: Boolean) = energyUi.setTripGpsRecordingEnabled(enabled)

    fun saveConsumptionGood(value: String) = energyUi.saveConsumptionGood(value)

    fun saveConsumptionBad(value: String) = energyUi.saveConsumptionBad(value)
    fun uploadLogs() = maintenanceUi.uploadLogs()
    fun refreshPermissionHealth() = permissionHealthUi.refresh()
    fun repairPermissions() = permissionHealthUi.repair()
    fun showUpdateDialog() = maintenanceUi.showUpdateDialog()
    fun hideUpdateDialog() = maintenanceUi.hideUpdateDialog()

    fun setAutoCheckUpdates(enabled: Boolean) = maintenanceUi.setAutoCheckUpdates(enabled)

    fun checkForUpdate() = maintenanceUi.checkForUpdate()

    fun exportConfig() = maintenanceUi.exportConfig()

    fun restoreConfig(uri: Uri) = maintenanceUi.restoreConfig(uri)

    fun downloadUpdate() = maintenanceUi.downloadUpdate()

    fun continuePendingInstall() = maintenanceUi.continuePendingInstall()
}
