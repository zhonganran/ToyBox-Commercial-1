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
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.data.settings.ServiceQrCache
import com.byd.wjx.service.UpdateChecker
import com.byd.wjx.R
import com.byd.wjx.audio.AutoParkingSoundSettings
import com.byd.wjx.audio.LockSoundConfig
import com.byd.wjx.audio.LockSoundConfigSync
import com.byd.wjx.audio.LockSoundEvent
import com.byd.wjx.audio.LockSoundSettings
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.SoundLibrary
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
import com.byd.wjx.service.BootDiagnosticsStore
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

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
    private val backupManager: BackupManager,
    private val activationManager: ActivationManager,
    private val soundLibrary: SoundLibrary,
    private val lockSoundConfigSync: LockSoundConfigSync,
    private val autoParkingSoundSettings: AutoParkingSoundSettings,
    private val okHttpClient: OkHttpClient,
    private val serviceQrCache: ServiceQrCache,
    private val logRecorder: SettingsLogRecorder,
    private val carProfileDetector: CarProfileDetector = CarProfileDetector(),
) : ViewModel() {
    private val lockSoundSettings = LockSoundSettings(appContext)

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
        viewModelScope.launch {
            logRecorder.state.collect { recordingState ->
                _uiState.update {
                    it.copy(
                        isRecordingLogs = recordingState.isRecording,
                        logSaveStatus = recordingState.status ?: it.logSaveStatus,
                    )
                }
            }
        }
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
        val json = serviceQrCache.readJson().orEmpty()
        return parseServiceQrCodes(json)
    }

    private fun cacheServiceQrCodes(json: String) {
        serviceQrCache.writeJson(json)
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
            val fuelTankCapacity = settingsRepository.getString(
                SettingsRepository.KEY_FUEL_TANK_CAPACITY,
                SettingsRepository.DEFAULT_FUEL_TANK_CAPACITY
            )
            val themeMode = settingsRepository.getThemeMode()
            val appWallpaper = settingsRepository.getAppWallpaper()
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
                    fuelTankCapacity = fuelTankCapacity,
                    themeMode = themeMode,
                    appWallpaper = appWallpaper,
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

    /** Save the vehicle-specific fuel-tank capacity used for remaining-litre estimates. */
    fun saveFuelTankCapacity(value: String) {
        _uiState.update { it.copy(fuelTankCapacity = value) }
        viewModelScope.launch {
            settingsRepository.setString(SettingsRepository.KEY_FUEL_TANK_CAPACITY, value)
        }
    }

    fun setThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAppWallpaper(wallpaper: String) {
        _uiState.update { it.copy(appWallpaper = wallpaper) }
        viewModelScope.launch {
            settingsRepository.setAppWallpaper(wallpaper)
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
            val snapshot = BootDiagnosticsStore.read(appContext) ?: return null
            val ts = snapshot.lastBootTimestamp
            if (ts == 0L) return null
            val sdf = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.US)
            "${sdf.format(Date(ts))}（${formatBootMethod(snapshot.lastBootMethod)}）"
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

    private fun readChainLog(): String? {
        return BootDiagnosticsStore.read(appContext)?.chainLog
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

    fun startLogRecording() {
        logRecorder.start()
    }

    fun stopLogRecording() {
        logRecorder.stop()
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

    override fun onCleared() {
        logRecorder.close()
        super.onCleared()
    }

}
