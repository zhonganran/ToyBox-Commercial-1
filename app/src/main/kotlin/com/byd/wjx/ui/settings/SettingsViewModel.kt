package com.byd.wjx.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.byd.wjx.BuildConfig
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.ActivationSnapshot
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.backup.BackupManager
import com.byd.wjx.data.local.EnergyDataReader
import com.byd.wjx.data.local.HistoryImporter
import com.byd.wjx.data.local.LocalePreferences
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.service.UpdateChecker
import com.byd.wjx.R
import com.byd.wjx.audio.AutoParkingSoundSettings
import com.byd.wjx.audio.LockSoundConfig
import com.byd.wjx.audio.LockSoundConfigSync
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.audio.LockSoundSettings
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.storage.WjxDirectories
import com.byd.wjx.vehicle.CarProfile
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.byd.wjx.service.BootReceiver
import com.byd.wjx.ui.widget.WidgetController
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * UI state for the Settings screen.
 * Contains current setting values and export operation status.
 */
data class SettingsUiState(
    val batteryCapacity: String = SettingsRepository.DEFAULT_BATTERY_CAPACITY,
    val homeTariff: String = SettingsRepository.DEFAULT_HOME_TARIFF,
    val dcTariff: String = SettingsRepository.DEFAULT_DC_TARIFF,
    val units: String = SettingsRepository.DEFAULT_UNITS,
    val currency: String = SettingsRepository.DEFAULT_CURRENCY,
    val currencySymbol: String = "元",
    val exportStatus: String? = null,
    val importStatus: String? = null,
    val appVersion: String = "0.0.0",
    val updateStatus: String? = null,
    val updateDialogState: UpdateState = UpdateState.Idle,
    val showUpdateDialog: Boolean = false,
    val diagnosticLog: String? = null,
    val logSaveStatus: String? = null,
    val isRecordingLogs: Boolean = false,
    val tripCostTariff: String = "home",
    val consumptionGood: String = SettingsRepository.DEFAULT_CONSUMPTION_GOOD,
    val consumptionBad: String = SettingsRepository.DEFAULT_CONSUMPTION_BAD,
    val lastBootInfo: String? = null,
    val chainLog: String? = null,
    val tariffSaveStatus: String? = null,
    val recalcStatus: String? = null,
    val showRecalcConfirm: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    /** Status of the last config backup/restore operation. Red if starts with error prefix. */
    val configStatus: String? = null,
    val mapTileSource: String = SettingsRepository.DEFAULT_MAP_TILE_SOURCE,
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
    val autoParkingSoundEnabled: Boolean = false,
    val autoParkingSoundPath: String = "",
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val tripRepository: TripRepository,
    private val chargeRepository: ChargeRepository,
    private val updateChecker: UpdateChecker,
    private val historyImporter: HistoryImporter,
    private val energyDataReader: EnergyDataReader,
    private val idleDrainDao: IdleDrainDao,
    private val tripPointDao: TripPointDao,
    private val localePreferences: LocalePreferences,
    private val backupManager: BackupManager,
    private val chargingStateStore: com.byd.wjx.data.charging.ChargingStateStore,
    private val catchUpJournal: com.byd.wjx.data.charging.CatchUpJournal,
    private val activationManager: ActivationManager,
    private val soundLibrary: SoundLibrary,
    private val lockSoundConfigSync: LockSoundConfigSync,
    private val okHttpClient: OkHttpClient,
    private val carProfileDetector: CarProfileDetector = CarProfileDetector(),
) : ViewModel() {
    private val lockSoundSettings = LockSoundSettings(appContext)
    private val autoParkingSoundSettings = AutoParkingSoundSettings(appContext)

    private val _uiState = MutableStateFlow(SettingsUiState(
        appVersion = getVersion(),
        autoCheckUpdates = UpdateChecker.isAutoCheckEnabled(appContext)
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Tracks the in-flight update download so "Close" can cancel it (issue #23).
    private var downloadJob: Job? = null

    private fun getVersion(): String = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    init {
        loadSettings()
        loadLockSoundSettings()
        refreshActivation(refreshDeviceCode = true)
        refreshServiceQrCodes()
    }

    fun reloadLockSoundChoices() {
        loadLockSoundSettings()
    }

    fun setLockSoundEnabled(event: LockSoundEvent, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !canUseVehicleEventSounds()) {
                refreshLockSoundState("试用已结束，${event.title}需要激活后使用")
                return@launch
            }
            val current = lockSoundSettings.snapshot()
            if (enabled && !soundLibrary.isVehicleSound(current.path(event))) {
                val first = soundLibrary.listVehicleSounds().firstOrNull()
                if (first == null) {
                    refreshLockSoundState("车辆音效为空，请先下载或导入车辆音效")
                    return@launch
                }
                lockSoundSettings.setSoundPath(event, first.file.absolutePath)
            }
            lockSoundSettings.setEnabled(event, enabled)
            refreshLockSoundState(if (enabled) "${event.title}已开启" else "${event.title}已关闭")
            syncLockSoundConfig("settings:${event.configKey}:enabled")
        }
    }

    fun setLockSoundPath(event: LockSoundEvent, path: String) {
        viewModelScope.launch {
            if (!canUseVehicleEventSounds()) {
                refreshLockSoundState("试用已结束，${event.title}需要激活后使用")
                return@launch
            }
            val source = File(path)
            if (!source.isFile) {
                refreshLockSoundState("音效文件不存在")
                return@launch
            }
            if (!soundLibrary.isVehicleSound(source.absolutePath)) {
                refreshLockSoundState("请从车辆音效中选择")
                return@launch
            }
            lockSoundSettings.setSoundPath(event, source.absolutePath)
            lockSoundSettings.setEnabled(event, true)
            refreshLockSoundState("${event.title}已选择：${source.nameWithoutExtension}")
            syncLockSoundConfig("settings:${event.configKey}:path")
        }
    }

    fun setAutoParkingSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !activationManager.canUse(PremiumFeature.AUTO_PARKING_SOUND)) {
                refreshLockSoundState("试用已结束，自动泊车音效需要激活后使用")
                return@launch
            }
            if (enabled && !soundLibrary.isVehicleSound(autoParkingSoundSettings.soundPath)) {
                val first = soundLibrary.listVehicleSounds().firstOrNull()
                if (first == null) {
                    refreshLockSoundState("车辆音效为空，请先下载或导入车辆音效")
                    return@launch
                }
                autoParkingSoundSettings.soundPath = first.file.absolutePath
            }
            autoParkingSoundSettings.enabled = enabled
            refreshLockSoundState(if (enabled) "自动泊车音效已开启" else "自动泊车音效已关闭")
        }
    }

    fun setAutoParkingSoundPath(path: String) {
        viewModelScope.launch {
            if (!activationManager.canUse(PremiumFeature.AUTO_PARKING_SOUND)) {
                refreshLockSoundState("试用已结束，自动泊车音效需要激活后使用")
                return@launch
            }
            val source = File(path)
            if (!source.isFile) {
                refreshLockSoundState("音效文件不存在")
                return@launch
            }
            if (!soundLibrary.isVehicleSound(source.absolutePath)) {
                refreshLockSoundState("请从车辆音效中选择")
                return@launch
            }
            autoParkingSoundSettings.soundPath = source.absolutePath
            autoParkingSoundSettings.enabled = true
            refreshLockSoundState("自动泊车音效已选择：${source.nameWithoutExtension}")
        }
    }

    fun importVehicleSound(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val displayName = appContext.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty().ifBlank { "车辆音效" }
                val extension = displayName.substringAfterLast('.', "").lowercase()
                require(extension in SoundLibrary.SUPPORTED_EXT) {
                    "仅支持 MP3、WAV、AAC、M4A、OGG、FLAC"
                }
                val baseName = displayName.substringBeforeLast('.', displayName)
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: error("无法读取所选音效")
                input.use { soundLibrary.saveVehicleSound(baseName, extension, it) }
            }.onSuccess { saved ->
                refreshLockSoundState("已导入车辆音效：${saved.name}")
            }.onFailure { error ->
                refreshLockSoundState("导入失败：${error.message ?: "文件不可用"}")
            }
        }
    }

    fun deleteVehicleSound(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!soundLibrary.deleteVehicleSound(path)) {
                refreshLockSoundState("删除失败：音效文件不存在")
                return@launch
            }
            lockSoundSettings.clearIfPath(path)
            autoParkingSoundSettings.clearIfPath(path)
            refreshLockSoundState("车辆音效已删除")
            syncLockSoundConfig("vehicle_sound_delete")
        }
    }

    fun syncLockSoundNow(reason: String) {
        viewModelScope.launch {
            refreshLockSoundState(status = null)
            syncLockSoundConfig(reason)
        }
    }

    private fun loadLockSoundSettings() {
        refreshLockSoundState(status = null)
    }

    private fun canUseVehicleEventSounds(): Boolean =
        activationManager.canUse(PremiumFeature.VEHICLE_EVENT_SOUNDS)

    private fun LockSoundConfig.forVehicleEventUi(allowed: Boolean): LockSoundConfig =
        if (allowed) this else copy(
            unlockEnabled = false,
            lockEnabled = false,
            sentryEnabled = false,
        )

    private fun refreshLockSoundState(status: String?) {
        val choices = soundLibrary.listVehicleSounds().map {
            LockSoundChoice(
                path = it.file.absolutePath,
                name = it.name,
                sizeLabel = it.sizeLabel,
            )
        }
        val vehicleEventAllowed = canUseVehicleEventSounds()
        _uiState.update {
            it.copy(
                lockSound = lockSoundSettings.snapshot().forVehicleEventUi(vehicleEventAllowed),
                lockSoundChoices = choices,
                lockSoundStatus = status ?: it.lockSoundStatus,
                autoParkingSoundEnabled = autoParkingSoundSettings.enabled &&
                    activationManager.canUse(PremiumFeature.AUTO_PARKING_SOUND),
                autoParkingSoundPath = autoParkingSoundSettings.soundPath,
            )
        }
    }

    private suspend fun syncLockSoundConfig(reason: String) {
        _uiState.update { it.copy(lockSoundSyncing = true) }
        val ok = lockSoundConfigSync.sync(reason)
        val vehicleEventAllowed = canUseVehicleEventSounds()
        _uiState.update {
            it.copy(
                lockSound = lockSoundSettings.snapshot().forVehicleEventUi(vehicleEventAllowed),
                autoParkingSoundEnabled = autoParkingSoundSettings.enabled &&
                    activationManager.canUse(PremiumFeature.AUTO_PARKING_SOUND),
                autoParkingSoundPath = autoParkingSoundSettings.soundPath,
                lockSoundSyncing = false,
                lockSoundStatus = if (ok) "车辆事件音效已同步到后台 helper" else "helper 暂不可用，设置已保存，稍后会自动重试",
            )
        }
    }

    fun refreshServiceQrCodes() {
        viewModelScope.launch {
            val cached = readCachedServiceQrCodes()
            if (cached.isNotEmpty()) {
                _uiState.update { it.copy(serviceQrCodes = cached) }
            }
            _uiState.update { it.copy(serviceQrLoading = true, serviceQrMessage = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(BuildConfig.SERVICE_QR_API_URL)
                        .get()
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        response.body?.string().orEmpty().also { body ->
                            if (body.isBlank()) error("empty body")
                        }
                    }
                }
            }
            result.onSuccess { body ->
                val parsed = parseServiceQrCodes(body)
                cacheServiceQrCodes(body)
                _uiState.update {
                    it.copy(
                        serviceQrCodes = parsed,
                        serviceQrLoading = false,
                        serviceQrMessage = if (parsed.isEmpty()) "后台暂未配置二维码" else "二维码已更新",
                    )
                }
            }.onFailure { error ->
                val fallback = _uiState.value.serviceQrCodes
                WjxFileLogger.w("SettingsViewModel", "service qr refresh failed: ${error.message}")
                _uiState.update {
                    it.copy(
                        serviceQrLoading = false,
                        serviceQrMessage = if (fallback.isNotEmpty()) "网络不可用，已显示缓存二维码" else "二维码读取失败：${error.message ?: "网络异常"}",
                    )
                }
            }
        }
    }

    private fun readCachedServiceQrCodes(): Map<String, ServiceQrCode> {
        val json = appContext.getSharedPreferences(SERVICE_QR_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SERVICE_QR_JSON, null)
            .orEmpty()
        return parseServiceQrCodes(json)
    }

    private fun cacheServiceQrCodes(json: String) {
        appContext.getSharedPreferences(SERVICE_QR_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVICE_QR_JSON, json)
            .apply()
    }

    private fun parseServiceQrCodes(json: String): Map<String, ServiceQrCode> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val items = JSONObject(json).optJSONArray("items") ?: return@runCatching emptyMap()
            buildMap {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val key = item.optString("key").trim()
                    if (key.isBlank() || !item.optBoolean("enabled", true)) continue
                    put(
                        key,
                        ServiceQrCode(
                            key = key,
                            title = item.optString("title").ifBlank { key },
                            subtitle = item.optString("subtitle"),
                            description = item.optString("description"),
                            imageUrl = item.optString("imageUrl"),
                            expiresAt = item.optString("expiresAt"),
                            updatedAt = item.optString("updatedAt"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun refreshActivation(refreshDeviceCode: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(activationLoading = true, activationMessage = null) }
            val snapshot = if (refreshDeviceCode) {
                activationManager.refreshSnapshot()
            } else {
                activationManager.currentSnapshot()
            }
            _uiState.update {
                it.copy(
                    activation = snapshot,
                    activationLoading = false,
                )
            }
            refreshLockSoundState(status = null)
        }
    }

    fun updateActivationCode(value: String) {
        _uiState.update { it.copy(activationCodeInput = value.uppercase(Locale.US)) }
    }

    fun activate() {
        val code = _uiState.value.activationCodeInput
        viewModelScope.launch {
            _uiState.update { it.copy(activationLoading = true, activationMessage = null) }
            val result = activationManager.activate(code)
            _uiState.update {
                it.copy(
                    activation = result.snapshot,
                    activationLoading = false,
                    activationMessage = result.message,
                    activationCodeInput = if (result.success) "" else it.activationCodeInput,
                )
            }
            if (result.success) {
                syncLockSoundConfig("activation_success")
            } else {
                refreshLockSoundState(status = null)
            }
        }
    }

    /** Load all settings from the repository on init. */
    private fun loadSettings() {
        viewModelScope.launch {
            val capacity = settingsRepository.getString(
                SettingsRepository.KEY_BATTERY_CAPACITY,
                SettingsRepository.DEFAULT_BATTERY_CAPACITY
            )
            val homeTariff = settingsRepository.getString(
                SettingsRepository.KEY_HOME_TARIFF,
                SettingsRepository.DEFAULT_HOME_TARIFF
            )
            val dcTariff = settingsRepository.getString(
                SettingsRepository.KEY_DC_TARIFF,
                SettingsRepository.DEFAULT_DC_TARIFF
            )
            val units = settingsRepository.getString(
                SettingsRepository.KEY_UNITS,
                SettingsRepository.DEFAULT_UNITS
            )
            val currency = settingsRepository.getCurrency()
            val tripCostTariff = settingsRepository.getTripCostTariffKey()
            val consumptionGood = settingsRepository.getString(
                SettingsRepository.KEY_CONSUMPTION_GOOD,
                SettingsRepository.DEFAULT_CONSUMPTION_GOOD
            )
            val consumptionBad = settingsRepository.getString(
                SettingsRepository.KEY_CONSUMPTION_BAD,
                SettingsRepository.DEFAULT_CONSUMPTION_BAD
            )

            // Read boot log from SharedPreferences
            val bootInfo = readBootInfo()
            val chainLog = readChainLog()
            val mapTileSource = settingsRepository.getMapTileSource()
            val tripGpsRecordingEnabled = settingsRepository.isTripGpsRecordingEnabled()
            val carProfile = withContext(Dispatchers.IO) { carProfileDetector.detect() }

            _uiState.update {
                it.copy(
                    batteryCapacity = capacity,
                    homeTariff = homeTariff,
                    dcTariff = dcTariff,
                    units = units,
                    currency = currency.code,
                    currencySymbol = currency.symbol,
                    tripCostTariff = tripCostTariff,
                    consumptionGood = consumptionGood,
                    consumptionBad = consumptionBad,
                    lastBootInfo = bootInfo,
                    chainLog = chainLog,
                    mapTileSource = mapTileSource,
                    tripGpsRecordingEnabled = tripGpsRecordingEnabled,
                    carProfile = carProfile,
                )
            }
        }
    }

    /** Save battery capacity setting. */
    fun saveBatteryCapacity(value: String) {
        _uiState.update { it.copy(batteryCapacity = value) }
        viewModelScope.launch {
            settingsRepository.setString(SettingsRepository.KEY_BATTERY_CAPACITY, value)
        }
    }

    /** Update tariff in UI only (no DB save until explicit "Save" press). */
    fun updateHomeTariff(value: String) {
        _uiState.update { it.copy(homeTariff = value) }
    }

    fun updateDcTariff(value: String) {
        _uiState.update { it.copy(dcTariff = value) }
    }

    /** Save tariffs to DB and calculate costs for new trips. */
    fun saveTariffs() {
        val state = _uiState.value
        viewModelScope.launch {
            settingsRepository.setString(SettingsRepository.KEY_HOME_TARIFF, state.homeTariff)
            settingsRepository.setString(SettingsRepository.KEY_DC_TARIFF, state.dcTariff)
            val tariff = settingsRepository.getTripCostTariff()
            historyImporter.calculateMissingCosts(tariff)
            _uiState.update { it.copy(tariffSaveStatus = appContext.getString(R.string.settings_saved)) }
            delay(2000)
            _uiState.update { it.copy(tariffSaveStatus = null) }
        }
    }

    /** Recalculate cost for ALL trips using current tariff. */
    fun recalculateAllCosts() {
        viewModelScope.launch {
            val tariff = settingsRepository.getTripCostTariff()
            val allTrips = tripRepository.getAllTrips().firstOrNull() ?: emptyList()
            var count = 0
            for (trip in allTrips) {
                val kwh = trip.kwhConsumed ?: continue
                tripRepository.updateTrip(trip.copy(cost = kwh * tariff))
                count++
            }
            _uiState.update { it.copy(recalcStatus = appContext.getString(R.string.settings_recalc_done, count)) }
            delay(3000)
            _uiState.update { it.copy(recalcStatus = null) }
        }
    }

    /** Save distance units preference (km or miles). */
    fun saveUnits(value: String) {
        _uiState.update { it.copy(units = value) }
        viewModelScope.launch {
            settingsRepository.setString(SettingsRepository.KEY_UNITS, value)
        }
    }

    /** Save trip cost tariff preference. */
    fun saveTripCostTariff(value: String) {
        _uiState.update { it.copy(tripCostTariff = value) }
        viewModelScope.launch {
            settingsRepository.setString(SettingsRepository.KEY_TRIP_COST_TARIFF, value)
        }
    }

    fun showRecalcConfirm() { _uiState.update { it.copy(showRecalcConfirm = true) } }
    fun hideRecalcConfirm() { _uiState.update { it.copy(showRecalcConfirm = false) } }
    fun confirmRecalc() {
        _uiState.update { it.copy(showRecalcConfirm = false) }
        recalculateAllCosts()
    }

    /** Save currency preference. */
    fun saveCurrency(code: String) {
        val currency = SettingsRepository.CURRENCIES.find { it.code == code }
            ?: SettingsRepository.CURRENCIES.first()
        _uiState.update { it.copy(currency = currency.code, currencySymbol = currency.symbol) }
        viewModelScope.launch {
            settingsRepository.setString(SettingsRepository.KEY_CURRENCY, code)
        }
    }

    /**
     * Export all trips and charges to CSV files in the Downloads directory.
     * Creates files with the wjx_<type>_<timestamp>.csv naming pattern.
     */
    fun exportCsv() {
        viewModelScope.launch {
            _uiState.update { it.copy(exportStatus = appContext.getString(R.string.settings_export_in_progress)) }

            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

                // Export trips
                val trips = tripRepository.getAllTrips().firstOrNull() ?: emptyList()
                val tripsFile = File(downloadsDir, "wjx_trips_$timestamp.csv")
                FileWriter(tripsFile).use { writer ->
                    writer.append("id,start_ts,end_ts,distance_km,kwh_consumed,kwh_per_100km,soc_start,soc_end,temp_avg_c,avg_speed_kmh,bat_temp_avg,bat_temp_max,bat_temp_min,cost,exterior_temp\n")
                    for (trip in trips) {
                        writer.append("${trip.id},${trip.startTs},${trip.endTs ?: ""},")
                        writer.append("${trip.distanceKm ?: ""},${trip.kwhConsumed ?: ""},")
                        writer.append("${trip.kwhPer100km ?: ""},${trip.socStart ?: ""},")
                        writer.append("${trip.socEnd ?: ""},${trip.tempAvgC ?: ""},")
                        writer.append("${trip.avgSpeedKmh ?: ""},${trip.batTempAvg ?: ""},")
                        writer.append("${trip.batTempMax ?: ""},${trip.batTempMin ?: ""},")
                        writer.append("${trip.cost ?: ""},${trip.exteriorTemp ?: ""}\n")
                    }
                }

                // Export charges
                val charges = chargeRepository.getAllCharges().firstOrNull() ?: emptyList()
                val chargesFile = File(downloadsDir, "wjx_charges_$timestamp.csv")
                FileWriter(chargesFile).use { writer ->
                    writer.append("id,start_ts,end_ts,soc_start,soc_end,kwh_charged,kwh_charged_soc,max_power_kw,type,cost,lat,lon,bat_temp_avg,bat_temp_max,bat_temp_min,avg_power_kw,status,cell_voltage_min,cell_voltage_max,voltage_12v,exterior_temp,merged_count\n")
                    for (charge in charges) {
                        writer.append("${charge.id},${charge.startTs},${charge.endTs ?: ""},")
                        writer.append("${charge.socStart ?: ""},${charge.socEnd ?: ""},")
                        writer.append("${charge.kwhCharged ?: ""},${charge.kwhChargedSoc ?: ""},")
                        writer.append("${charge.maxPowerKw ?: ""},${charge.type ?: ""},")
                        writer.append("${charge.cost ?: ""},${charge.lat ?: ""},")
                        writer.append("${charge.lon ?: ""},${charge.batTempAvg ?: ""},")
                        writer.append("${charge.batTempMax ?: ""},${charge.batTempMin ?: ""},")
                        writer.append("${charge.avgPowerKw ?: ""},${charge.status},")
                        writer.append("${charge.cellVoltageMin ?: ""},${charge.cellVoltageMax ?: ""},")
                        writer.append("${charge.voltage12v ?: ""},${charge.exteriorTemp ?: ""},")
                        writer.append("${charge.mergedCount}\n")
                    }
                }

                // Export GPS track points
                val tripPoints = tripPointDao.getAll()
                val pointsFile = File(downloadsDir, "wjx_trip_points_$timestamp.csv")
                FileWriter(pointsFile).use { writer ->
                    writer.append("id,trip_id,byd_id,byd_date,timestamp,lat,lon,speed_kmh\n")
                    for (p in tripPoints) {
                        writer.append("${p.id},${p.tripId},${p.bydId ?: ""},${p.bydDate ?: ""},${p.timestamp},")
                        writer.append("${p.lat},${p.lon},${p.speedKmh ?: ""}\n")
                    }
                }

                // Export idle drains (parked battery drain)
                val idleDrains = idleDrainDao.getAll()
                val drainsFile = File(downloadsDir, "wjx_idle_drains_$timestamp.csv")
                FileWriter(drainsFile).use { writer ->
                    writer.append("id,start_ts,end_ts,soc_start,soc_end,kwh_consumed\n")
                    for (d in idleDrains) {
                        writer.append("${d.id},${d.startTs},${d.endTs ?: ""},")
                        writer.append("${d.socStart ?: ""},${d.socEnd ?: ""},${d.kwhConsumed ?: ""}\n")
                    }
                }

                val tripCount = trips.size
                val chargeCount = charges.size
                _uiState.update {
                    it.copy(
                        exportStatus = appContext.getString(R.string.settings_export_done, tripCount, chargeCount, tripPoints.size, idleDrains.size) + "\n-> ${downloadsDir.absolutePath}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(exportStatus = appContext.getString(R.string.settings_error_with_message, e.message ?: "?"))
                }
            }
        }
    }

    /** Clear the export status message. */
    fun clearExportStatus() {
        _uiState.update { it.copy(exportStatus = null) }
    }

    /** Import trip history from the vehicle energy database. */
    fun importBydHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(importStatus = "正在导入…") }
            val result = historyImporter.runSync()
            if (result.isError) {
                _uiState.update {
                    it.copy(importStatus = "错误：${result.error}")
                }
            } else {
                val status = result.details
                    ?: "已从比亚迪导入 ${result.count} 条行程"
                _uiState.update { it.copy(importStatus = status) }
            }
        }
    }

    /** Run full diagnostics: vehicle storage, local database, permissions. */
    fun runDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(diagnosticLog = "正在诊断…") }

            val sb = StringBuilder()
            val sdf = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.US)

            sb.appendLine("=== 权限 ===")
            val perms = listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.RECORD_AUDIO,
            )
            for (perm in perms) {
                val granted = ContextCompat.checkSelfPermission(appContext, perm) ==
                    PackageManager.PERMISSION_GRANTED
                val name = formatPermissionName(perm)
                sb.appendLine("$name: ${if (granted) "✓" else "✗"}")
            }

            try {
                val bydReport = energyDataReader.diagnose()
                sb.appendLine()
                sb.append(bydReport)
            } catch (e: Exception) {
                sb.appendLine("\n比亚迪数据错误：${e.message}")
            }

            sb.appendLine("\n=== 本地数据库 ===")
            try {
                val trips = tripRepository.getAllTrips().first()
                val charges = chargeRepository.getAllCharges().first()
                val drainCount = idleDrainDao.getCount()
                val drainKwh = idleDrainDao.getTotalKwh()
                sb.appendLine("行程：${trips.size}")
                sb.appendLine("充电：${charges.size}")
                sb.appendLine("停车耗电：$drainCount（%.2f 千瓦时）".format(drainKwh))

                if (trips.isNotEmpty()) {
                    sb.appendLine("\n最近 5 条行程：")
                    trips.take(5).forEach { t ->
                        val startFmt = sdf.format(Date(t.startTs))
                        val endFmt = t.endTs?.let { sdf.format(Date(it)) } ?: "无"
                        sb.appendLine("#${t.id}: $startFmt – $endFmt")
                        sb.appendLine(
                            "  里程=${t.distanceKm ?: "-"}，能耗=${t.kwhConsumed ?: "-"}，" +
                                "电量=${t.socStart ?: "-"}→${t.socEnd ?: "-"}，" +
                                "速度=${t.avgSpeedKmh?.let { "%.0f".format(it) } ?: "-"}"
                        )
                        sb.appendLine("  原始时间：开始=${t.startTs}，结束=${t.endTs ?: "无"}")
                    }
                }
            } catch (e: Exception) {
                sb.appendLine("错误：${e.message}")
            }

            _uiState.update { it.copy(diagnosticLog = sb.toString()) }
        }
    }

    private fun readBootInfo(): String? {
        return try {
            val prefs = appContext.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            val ts = prefs.getLong(BootReceiver.KEY_LAST_BOOT_TS, 0L)
            if (ts == 0L) return null
            val method = prefs.getString(BootReceiver.KEY_LAST_BOOT_METHOD, "?") ?: "?"
            val sdf = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.US)
            "${sdf.format(Date(ts))}（${formatBootMethod(method)}）"
        } catch (_: Exception) { null }
    }

    private fun formatBootMethod(method: String): String = when {
        method == "WorkManager" -> "系统调度启动"
        method == "DirectFallback" -> "直接启动"
        method.startsWith("FAILED:") -> "失败：${method.substringAfter("FAILED:").trim()}"
        else -> method
    }

    private fun formatPermissionName(permission: String): String = when (permission) {
        Manifest.permission.READ_EXTERNAL_STORAGE -> "读取存储"
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "写入存储"
        Manifest.permission.ACCESS_FINE_LOCATION -> "精确定位"
        Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "后台定位"
        Manifest.permission.RECORD_AUDIO -> "麦克风"
        else -> permission.substringAfterLast(".")
    }

    private fun formatBool(value: Boolean): String = if (value) "是" else "否"

    private fun formatDataSource(name: String): String = when (name) {
        "ENERGYDATA" -> "比亚迪能耗数据库"
        else -> name
    }

    private fun readChainLog(): String? {
        return try {
            val prefs = appContext.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            val log = prefs.getString(BootReceiver.KEY_CHAIN_LOG, null)
            if (log.isNullOrBlank()) null else log
        } catch (_: Exception) { null }
    }

    fun saveMapTileSource(source: String) {
        _uiState.update { it.copy(mapTileSource = source) }
        viewModelScope.launch {
            settingsRepository.setMapTileSource(source)
        }
    }

    fun setTripGpsRecordingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(tripGpsRecordingEnabled = enabled) }
        viewModelScope.launch {
            settingsRepository.setTripGpsRecordingEnabled(enabled)
        }
    }

    fun saveConsumptionGood(value: String) {
        _uiState.update { it.copy(consumptionGood = value) }
        viewModelScope.launch {
            settingsRepository.setString(SettingsRepository.KEY_CONSUMPTION_GOOD, value)
        }
    }

    fun saveConsumptionBad(value: String) {
        _uiState.update { it.copy(consumptionBad = value) }
        viewModelScope.launch {
            settingsRepository.setString(SettingsRepository.KEY_CONSUMPTION_BAD, value)
        }
    }

    private var logProcess: Process? = null
    private var logFile: File? = null
    private var logAutoStopJob: Job? = null

    companion object {
        private const val LOG_MAX_DURATION_MS = 2 * 60 * 60 * 1000L // 2 hours auto-stop
        private const val LOG_MAX_SIZE_BYTES = WjxFileLogger.MAX_LOG_BYTES
        private const val LOG_MAX_SIZE_MB = LOG_MAX_SIZE_BYTES / 1024 / 1024
        private const val SERVICE_QR_PREFS = "service_qr_cache"
        private const val KEY_SERVICE_QR_JSON = "service_qr_json"
    }

    /**
     * Writes a diagnostic header to the recording file before piping logcat.
     * Captures app / device / setting context that issue reports (e.g. #19)
     * routinely lack: which battery capacity the user typed (raw + parsed),
     * whether autoservice is reachable, and whether the BYD energydata trip
     * source is readable.
     */
    private suspend fun appendDiagnosticHeader() = withContext(Dispatchers.IO) {
        // Build the header. Each piece is independently caught so a single
        // failing getter doesn't drop the whole header.
        val header = buildString {
            appendLine("=== 超级玩具箱诊断信息 ===")
            try {
                val pkg = appContext.packageName
                val pi = appContext.packageManager.getPackageInfo(pkg, 0)
                val versionName = pi.versionName ?: "?"
                val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                    pi.longVersionCode.toString()
                else
                    @Suppress("DEPRECATION") pi.versionCode.toString()
                appendLine("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("应用：$pkg $versionName（版本号=$versionCode）")
                appendLine("设备：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("系统：${android.os.Build.VERSION.RELEASE}（接口级别 ${android.os.Build.VERSION.SDK_INT}）")
                appendLine("系统指纹：${android.os.Build.FINGERPRINT}")
                appendLine("语言：系统=${Locale.getDefault().toLanguageTag()} 应用=${localePreferences.getLanguage() ?: "未设置"}")
            } catch (e: Exception) {
                appendLine("（获取应用和设备信息失败：${e.message}）")
            }

            appendLine("--- 设置 ---")
            try {
                val dataSource = formatDataSource(settingsRepository.getDataSource().name)
                val capacityRaw = settingsRepository.getString(SettingsRepository.KEY_BATTERY_CAPACITY, "")
                val capacityParsed = settingsRepository.getBatteryCapacity()
                appendLine("数据来源：$dataSource")
                appendLine("电池容量：原始=\"$capacityRaw\"，解析=$capacityParsed")
            } catch (e: Exception) {
                appendLine("（获取设置失败：${e.message}）")
            }

            appendLine("--- 充电补录 ---")
            try {
                val anchor = chargingStateStore.load()
                val anchorTs = if (anchor.ts > 0L)
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(anchor.ts))
                else "未设置"
                appendLine(
                    "锚点：电量=${anchor.socPercent}，里程=${anchor.mileageKm}，" +
                        "时间=$anchorTs，待补录=${formatBool(chargingStateStore.loadChargePending())}"
                )
                val journal = catchUpJournal.read()
                appendLine("补录日志：")
                if (journal.isBlank()) {
                    appendLine("  （空）")
                } else {
                    journal.lines().forEach { appendLine("  $it") }
                }
            } catch (e: Exception) {
                appendLine("（获取充电补录状态失败：${e.message}）")
            }

            appendLine("--- 车辆数据来源 ---")
            try {
                val energyDb = File("/storage/emulated/0/energydata")
                appendLine("能耗目录：存在=${formatBool(energyDb.exists())}，是目录=${formatBool(energyDb.isDirectory)}")
                if (energyDb.exists() && energyDb.isDirectory) {
                    val files = energyDb.listFiles()
                    if (files == null) {
                        appendLine("  读取文件列表：无结果（可能无权限）")
                    } else {
                        files.forEach { appendLine("  ${it.name}（${it.length()} 字节，修改时间=${it.lastModified()}）") }
                    }
                }
            } catch (e: Exception) {
                appendLine("（获取车辆数据来源失败：${e.message}）")
            }
            appendLine("===============================")
            appendLine()
        }

        WjxFileLogger.appendSection("诊断信息", header)
    }

    fun startLogRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!WjxDirectories.ensureBaseDirs()) {
                    _uiState.update { it.copy(logSaveStatus = appContext.getString(R.string.settings_log_error_no_fs_access)) }
                    return@launch
                }

                logFile = WjxDirectories.appLog

                appendDiagnosticHeader()

                WjxFileLogger.i("SettingsViewModel", "logcat recording start target=${WjxFileLogger.logFilePath}")
                Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()

                val pid = android.os.Process.myPid().toString()
                logProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "time", "--pid=$pid"))

                // Pipe every current-app logcat line into the same WJX/wjx.log file.
                Thread {
                    try {
                        logProcess?.inputStream?.bufferedReader()?.use { reader ->
                            val target = logFile ?: return@Thread
                            var line = reader.readLine()
                            while (line != null) {
                                if (target.length() > LOG_MAX_SIZE_BYTES) {
                                    WjxFileLogger.appendExternalLine("--- 日志已停止：文件大小达到 ${LOG_MAX_SIZE_MB} MB 上限 ---")
                                    break
                                }
                                WjxFileLogger.appendExternalLine(line)
                                line = reader.readLine()
                            }
                        }
                    } catch (e: Exception) {
                        WjxFileLogger.w("SettingsViewModel", "logcat pipe stopped: ${e.message}")
                    }
                }.start()

                // Auto-stop after 2 hours
                logAutoStopJob = viewModelScope.launch {
                    delay(LOG_MAX_DURATION_MS)
                    stopLogRecording()
                }

                _uiState.update {
                    it.copy(isRecordingLogs = true, logSaveStatus = appContext.getString(R.string.settings_log_recording_started, WjxFileLogger.logFilePath))
                }
            } catch (e: Exception) {
                WjxFileLogger.e("SettingsViewModel", "startLogRecording failed", e)
                _uiState.update { it.copy(logSaveStatus = appContext.getString(R.string.settings_error_with_message, e.message ?: "?")) }
            }
        }
    }

    fun stopLogRecording() {
        logAutoStopJob?.cancel()
        logAutoStopJob = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                logProcess?.destroy()
                logProcess = null

                val file = WjxDirectories.appLog
                WjxFileLogger.flush()
                val sizeKb = file.length() / 1024
                WjxFileLogger.i("SettingsViewModel", "logcat recording stop target=${file.absolutePath} sizeKb=$sizeKb")

                _uiState.update {
                    it.copy(
                        isRecordingLogs = false,
                        logSaveStatus = appContext.getString(R.string.settings_log_saved, file.absolutePath, sizeKb)
                    )
                }
            } catch (e: Exception) {
                WjxFileLogger.e("SettingsViewModel", "stopLogRecording failed", e)
                _uiState.update {
                    it.copy(isRecordingLogs = false, logSaveStatus = appContext.getString(R.string.settings_error_with_message, e.message ?: "?"))
                }
            }
        }
    }

    fun showUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = true, updateDialogState = UpdateState.Idle) }
    }

    fun hideUpdateDialog() {
        // Cancel any in-flight download so the progress callback stops re-emitting
        // Downloading (which reopened the dialog) and the finished download no longer
        // fires the system install prompt after the user closed it (issue #23).
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update { it.copy(showUpdateDialog = false, updateDialogState = UpdateState.Idle) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        UpdateChecker.setAutoCheckEnabled(appContext, enabled)
        _uiState.update { it.copy(autoCheckUpdates = enabled) }
    }

    /** Check for app updates from the configured update backend. */
    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(updateDialogState = UpdateState.Checking, updateStatus = appContext.getString(R.string.settings_update_check_in_progress)) }
            try {
                val update = updateChecker.checkForUpdate(appContext, forceCheck = true)
                if (update != null) {
                    _uiState.update {
                        it.copy(
                            updateDialogState = UpdateState.Available(
                                version = update.version,
                                notes = update.releaseNotes ?: ""
                            ),
                            updateStatus = appContext.getString(R.string.settings_update_available_short, update.version)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            updateDialogState = UpdateState.UpToDate,
                            updateStatus = appContext.getString(R.string.settings_update_up_to_date)
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        updateDialogState = UpdateState.Error(e.message ?: "未知错误"),
                        updateStatus = appContext.getString(R.string.settings_error_with_message, e.message ?: "?")
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Config backup / restore
    // -------------------------------------------------------------------------

    /**
     * Export the full app state (DB + prefs) to a zip file in Downloads.
     * Updates configStatus with a success path or an error message.
     */
    fun exportConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(configStatus = appContext.getString(R.string.settings_export_in_progress)) }
            try {
                val file = backupManager.export()
                _uiState.update {
                    it.copy(configStatus = appContext.getString(R.string.settings_config_export_done, file.absolutePath))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(configStatus = appContext.getString(R.string.settings_error_with_message, e.message ?: "?"))
                }
            }
        }
    }

    /**
     * Restore the full app state from a user-picked backup zip.
     * On success the process is immediately restarted so Room re-opens the replaced DB.
     * On failure configStatus is set to the error message.
     */
    fun restoreConfig(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(configStatus = appContext.getString(R.string.settings_export_in_progress)) }
            try {
                backupManager.restore(uri)
                restartApp()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(configStatus = appContext.getString(R.string.settings_error_with_message, e.message ?: "?"))
                }
            }
        }
    }

    /** Dismiss the config backup/restore status message. */
    fun clearConfigStatus() {
        _uiState.update { it.copy(configStatus = null) }
    }

    /**
     * Relaunch the app from scratch so Room re-opens the freshly restored DB file.
     * FLAG_ACTIVITY_CLEAR_TASK terminates all existing activities before the new launch.
     */
    private fun restartApp() {
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        if (intent != null) {
            appContext.startActivity(intent)
        }
        Runtime.getRuntime().exit(0)
    }

    fun downloadUpdate() {
        downloadJob = viewModelScope.launch {
            try {
                val update = updateChecker.checkForUpdate(appContext, forceCheck = true)
                if (update != null) {
                    _uiState.update {
                        it.copy(updateDialogState = UpdateState.Downloading(update.version, appContext.getString(R.string.update_downloading_start)))
                    }
                    updateChecker.downloadAndInstall(appContext, update) { progress ->
                        // Ignore late progress after the job was cancelled (Close pressed)
                        // so a closed dialog is never resurrected.
                        if (isActive) {
                            _uiState.update {
                                it.copy(updateDialogState = UpdateState.Downloading(update.version, progress))
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e // cooperative cancellation from hideUpdateDialog(); not an error
            } catch (e: Exception) {
                _uiState.update { it.copy(updateDialogState = UpdateState.Error(e.message ?: "下载失败")) }
            }
        }
    }

}
