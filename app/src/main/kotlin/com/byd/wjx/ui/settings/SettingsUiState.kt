package com.byd.wjx.ui.settings

import com.byd.wjx.activation.ActivationSnapshot
import com.byd.wjx.audio.LockSoundConfig
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.vehicle.CarProfile

/**
 * UI state for the Settings screen.
 * Contains current setting values and export operation status.
 */
data class SettingsUiState(
    val batteryCapacity: String = SettingsRepository.DEFAULT_BATTERY_CAPACITY,
    val fuelTankCapacity: String = SettingsRepository.DEFAULT_FUEL_TANK_CAPACITY,
    val themeMode: String = SettingsRepository.DEFAULT_THEME_MODE,
    val appWallpaper: String = SettingsRepository.DEFAULT_APP_WALLPAPER,
    val homeTariff: String = SettingsRepository.DEFAULT_HOME_TARIFF,
    val dcTariff: String = SettingsRepository.DEFAULT_DC_TARIFF,
    val fuelPricePerLiter: String = SettingsRepository.DEFAULT_FUEL_PRICE_PER_LITER,
    val currencySymbol: String = "元",
    val appVersion: String = "0.0.0",
    val updateStatus: String? = null,
    val updateDialogState: UpdateState = UpdateState.Idle,
    val showUpdateDialog: Boolean = false,
    val logUploadStatus: String? = null,
    val isUploadingLogs: Boolean = false,
    val tripCostTariff: String = "home",
    val consumptionGood: String = SettingsRepository.DEFAULT_CONSUMPTION_GOOD,
    val consumptionBad: String = SettingsRepository.DEFAULT_CONSUMPTION_BAD,
    val lastBootInfo: String? = null,
    val chainLog: String? = null,
    val capacitySaveStatus: String? = null,
    val tariffSaveStatus: String? = null,
    val recalcStatus: String? = null,
    val showRecalcConfirm: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    /** Status of the last config backup/restore operation. Red if starts with error prefix. */
    val configStatus: String? = null,
    val tripGpsRecordingEnabled: Boolean = true,
    val activation: ActivationSnapshot = ActivationSnapshot(),
    val activationCodeInput: String = "",
    val activationMessage: String? = null,
    val activationLoading: Boolean = false,
    val serviceQrCodes: Map<String, ServiceQrCode> = emptyMap(),
    val serviceQrLoading: Boolean = false,
    val serviceQrMessage: String? = null,
    val lockSound: LockSoundConfig = LockSoundConfig(),
    val lockSoundChoices: List<LockSoundChoice> = emptyList(),
    val lockSoundSyncing: Boolean = false,
    val lockSoundStatus: String? = null,
    val carProfile: CarProfile? = null,
)

data class LockSoundChoice(
    val path: String,
    val name: String,
    val sizeLabel: String,
)

data class ServiceQrCode(
    val key: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val imageUrl: String,
    val expiresAt: String,
    val updatedAt: String,
)
