package com.byd.wjx.ui.settings

import com.byd.wjx.activation.ActivationSnapshot
import com.byd.wjx.activation.ActivationPaymentOrder
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.vehicle.HeadlightAvailability
import com.byd.wjx.data.vehicle.HeadlightControlSnapshot
import com.byd.wjx.data.vehicle.HeadlightMode
import com.byd.wjx.vehicle.CarProfile

/**
 * UI state for the Settings screen.
 * Contains current setting values and export operation status.
 */
data class SettingsUiState(
    val energy: SettingsEnergyUiState = SettingsEnergyUiState(),
    val appPreferences: SettingsAppPreferencesUiState = SettingsAppPreferencesUiState(),
    val maintenance: SettingsMaintenanceUiState = SettingsMaintenanceUiState(),
    val currencySymbol: String = "元",
    val activation: ActivationSnapshot = ActivationSnapshot(),
    val activationCodeInput: String = "",
    val activationMessage: String? = null,
    val activationLoading: Boolean = false,
    val activationPayment: ActivationPaymentUiState = ActivationPaymentUiState(),
    val serviceQr: SettingsServiceQrUiState = SettingsServiceQrUiState(),
    val vehicleEventSound: SettingsVehicleEventSoundUiState = SettingsVehicleEventSoundUiState(),
    val vehicleControl: SettingsVehicleControlUiState = SettingsVehicleControlUiState(),
)

data class SettingsServiceQrUiState(
    val codes: Map<String, ServiceQrCode> = emptyMap(),
    val loading: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

data class SettingsAppPreferencesUiState(
    val themeMode: String = SettingsRepository.DEFAULT_THEME_MODE,
    val appWallpaper: String = SettingsRepository.DEFAULT_APP_WALLPAPER,
    val tripGpsRecordingEnabled: Boolean = true,
) {
    internal constructor(settings: AppPreferenceSettings) : this(
        themeMode = settings.themeMode,
        appWallpaper = settings.appWallpaper,
        tripGpsRecordingEnabled = settings.tripGpsRecordingEnabled,
    )
}

data class SettingsVehicleControlUiState(
    val headlightAvailability: HeadlightAvailability = HeadlightAvailability.UNKNOWN,
    val headlightMode: HeadlightMode? = null,
    val headlightLoading: Boolean = false,
    val headlightMessage: String = "尚未检测车辆灯光服务",
    val carProfile: CarProfile? = null,
) {
    internal fun withSnapshot(snapshot: HeadlightControlSnapshot) = copy(
        headlightAvailability = snapshot.availability,
        headlightMode = snapshot.mode,
        headlightLoading = false,
        headlightMessage = snapshot.message,
    )
}

data class SettingsMaintenanceUiState(
    val appVersion: String = "0.0.0",
    val updateDialogState: UpdateState = UpdateState.Idle,
    val showUpdateDialog: Boolean = false,
    val logUploadStatus: String? = null,
    val isUploadingLogs: Boolean = false,
    val lastBootInfo: String? = null,
    val autoCheckUpdates: Boolean = true,
    /** Status of the last config backup/restore operation. Red if starts with error prefix. */
    val configStatus: String? = null,
)

data class SettingsEnergyUiState(
    val batteryCapacity: String = SettingsRepository.DEFAULT_BATTERY_CAPACITY,
    val fuelTankCapacity: String = SettingsRepository.DEFAULT_FUEL_TANK_CAPACITY,
    val homeTariff: String = SettingsRepository.DEFAULT_HOME_TARIFF,
    val dcTariff: String = SettingsRepository.DEFAULT_DC_TARIFF,
    val fuelPricePerLiter: String = SettingsRepository.DEFAULT_FUEL_PRICE_PER_LITER,
    val tripCostTariff: String = "home",
    val consumptionGood: String = SettingsRepository.DEFAULT_CONSUMPTION_GOOD,
    val consumptionBad: String = SettingsRepository.DEFAULT_CONSUMPTION_BAD,
    val capacitySaveStatus: String? = null,
    val tariffSaveStatus: String? = null,
    val recalcStatus: String? = null,
    val showRecalcConfirm: Boolean = false,
) {
    internal constructor(settings: VehicleEnergySettings) : this(
        batteryCapacity = settings.batteryCapacity,
        fuelTankCapacity = settings.fuelTankCapacity,
        homeTariff = settings.homeTariff,
        dcTariff = settings.dcTariff,
        fuelPricePerLiter = settings.fuelPricePerLiter,
        tripCostTariff = settings.tripCostTariff,
        consumptionGood = settings.consumptionGood,
        consumptionBad = settings.consumptionBad,
    )
}

data class ActivationPaymentUiState(
    val order: ActivationPaymentOrder? = null,
    val loading: Boolean = false,
    val message: String? = null,
) {
    val dialogVisible: Boolean
        get() = loading || order != null || !message.isNullOrBlank()
}

data class ServiceQrCode(
    val key: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val imageUrl: String,
    val expiresAt: String,
    val updatedAt: String,
)
