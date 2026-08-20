package com.byd.wjx.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.byd.wjx.BuildConfig
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.ActivationPaymentOrder
import com.byd.wjx.activation.ActivationSnapshot
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.backup.BackupManager
import com.byd.wjx.data.local.HistoryImporter
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.data.settings.ServiceQrCache
import com.byd.wjx.data.vehicle.HeadlightController
import com.byd.wjx.data.vehicle.HeadlightMode
import com.byd.wjx.service.UpdateChecker
import com.byd.wjx.R
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.byd.wjx.service.BootDiagnosticsStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val tripRepository: TripRepository,
    private val updateChecker: UpdateChecker,
    private val historyImporter: HistoryImporter,
    private val backupManager: BackupManager,
    private val activationManager: ActivationManager,
    private val soundLibrary: SoundLibrary,
    private val lockSoundConfigSync: LockSoundConfigSync,
    private val okHttpClient: OkHttpClient,
    private val serviceQrCache: ServiceQrCache,
    private val logUploader: SettingsLogUploader,
    private val headlightController: HeadlightController,
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
    private var activationPaymentJob: Job? = null
    private var availableUpdate: UpdateChecker.UpdateInfo? = null

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
            if (enabled && !soundLibrary.isManagedSound(current.path(event))) {
                val first = soundLibrary.listSounds().firstOrNull()
                if (first == null) {
                    refreshLockSoundState("WJX 音效库为空，请先下载或导入音效")
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
            if (!soundLibrary.isManagedSound(source.absolutePath)) {
                refreshLockSoundState("请从 WJX 音效库中选择")
                return@launch
            }
            lockSoundSettings.setSoundPath(event, source.absolutePath)
            lockSoundSettings.setEnabled(event, true)
            refreshLockSoundState("${event.title}已选择：${source.nameWithoutExtension}")
            syncLockSoundConfig("settings:${event.configKey}:path")
        }
    }

    fun importEventSound(uri: Uri) {
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
                }.orEmpty().ifBlank { "音效" }
                val extension = displayName.substringAfterLast('.', "").lowercase()
                require(extension in SoundLibrary.SUPPORTED_EXT) {
                    "仅支持 MP3、WAV、AAC、M4A、OGG、FLAC"
                }
                val baseName = displayName.substringBeforeLast('.', displayName)
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: error("无法读取所选音效")
                input.use { soundLibrary.saveSound(baseName, extension, it) }
            }.onSuccess { saved ->
                refreshLockSoundState("已导入到 /sdcard/WJX：${saved.name}")
            }.onFailure { error ->
                refreshLockSoundState("导入失败：${error.message ?: "文件不可用"}")
            }
        }
    }

    fun deleteEventSound(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!soundLibrary.deleteSound(path)) {
                refreshLockSoundState("删除失败：音效文件不存在")
                return@launch
            }
            lockSoundSettings.clearIfPath(path)
            refreshLockSoundState("音效已删除")
            syncLockSoundConfig("event_sound_delete")
        }
    }

    fun syncLockSoundNow(reason: String) {
        viewModelScope.launch {
            refreshLockSoundState(status = null)
            syncLockSoundConfig(reason)
        }
    }

    fun refreshHeadlightControl() {
        if (_uiState.value.headlightLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(headlightLoading = true, headlightMessage = "正在检测车辆灯光服务") }
            val snapshot = headlightController.refresh()
            _uiState.update {
                it.copy(
                    headlightAvailability = snapshot.availability,
                    headlightMode = snapshot.mode,
                    headlightLoading = false,
                    headlightMessage = snapshot.message,
                )
            }
        }
    }

    fun setHeadlightMode(mode: HeadlightMode) {
        if (_uiState.value.headlightLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(headlightLoading = true, headlightMessage = "正在切换为${mode.label}") }
            val result = headlightController.setMode(mode)
            _uiState.update {
                it.copy(
                    headlightAvailability = result.snapshot.availability,
                    headlightMode = result.snapshot.mode,
                    headlightLoading = false,
                    headlightMessage = result.snapshot.message,
                )
            }
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
        val choices = soundLibrary.listSounds().map {
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

    fun startActivationPayment(paymentType: String) {
        activationPaymentJob?.cancel()
        activationPaymentJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activationPaymentLoading = true,
                    activationPaymentMessage = "正在创建安全支付订单",
                    activationPaymentOrder = null,
                )
            }
            val order = runCatching { activationManager.createPayment(paymentType) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            activationPaymentLoading = false,
                            activationPaymentMessage = error.message ?: "创建支付订单失败",
                        )
                    }
                    activationPaymentJob = null
                    return@launch
                }
            _uiState.update {
                it.copy(
                    activationPaymentOrder = order,
                    activationPaymentLoading = false,
                    activationPaymentMessage = "请扫码支付，到账后会自动激活",
                )
            }
            pollActivationPayment(order)
            activationPaymentJob = null
        }
    }

    private suspend fun pollActivationPayment(order: ActivationPaymentOrder) {
        while (currentCoroutineContext().isActive) {
            delay(ACTIVATION_PAYMENT_POLL_MS)
            val result = try {
                activationManager.checkPayment(order)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(activationPaymentMessage = error.message ?: "查询支付状态失败，正在重试")
                }
                continue
            }
            _uiState.update {
                it.copy(
                    activation = result.snapshot,
                    activationPaymentLoading = false,
                    activationPaymentMessage = result.message,
                )
            }
            if (result.paid) {
                syncLockSoundConfig("payment_activation_success")
                return
            }
            if (result.status == "expired" || result.status == "failed") return
        }
    }

    fun dismissActivationPayment() {
        activationPaymentJob?.cancel()
        activationPaymentJob = null
        _uiState.update {
            it.copy(
                activationPaymentOrder = null,
                activationPaymentLoading = false,
                activationPaymentMessage = null,
            )
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
            val fuelPricePerLiter = settingsRepository.getString(
                SettingsRepository.KEY_FUEL_PRICE_PER_LITER,
                SettingsRepository.DEFAULT_FUEL_PRICE_PER_LITER,
            )
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
                    fuelPricePerLiter = fuelPricePerLiter,
                    tripCostTariff = tripCostTariff,
                    consumptionGood = consumptionGood,
                    consumptionBad = consumptionBad,
                    lastBootInfo = bootInfo,
                    chainLog = chainLog,
                    tripGpsRecordingEnabled = tripGpsRecordingEnabled,
                    carProfile = carProfile,
                )
            }
        }
    }

    fun updateBatteryCapacity(value: String) {
        _uiState.update { it.copy(batteryCapacity = value, capacitySaveStatus = null) }
    }

    fun updateFuelTankCapacity(value: String) {
        _uiState.update { it.copy(fuelTankCapacity = value, capacitySaveStatus = null) }
    }

    /** Atomically save capacities used for remaining-energy, trip-cost and charge statistics. */
    fun saveEnergyCapacities() {
        val state = _uiState.value
        val batteryCapacity = state.batteryCapacity.normalizedPositiveNumber()
        val fuelTankCapacity = state.fuelTankCapacity.replace(',', '.').trim()
        val fuelTankCapacityValid = fuelTankCapacity.isEmpty() ||
            fuelTankCapacity.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true
        if (batteryCapacity == null || !fuelTankCapacityValid) {
            _uiState.update {
                it.copy(capacitySaveStatus = appContext.getString(R.string.settings_capacity_invalid))
            }
            return
        }
        viewModelScope.launch {
            settingsRepository.setStrings(
                mapOf(
                    SettingsRepository.KEY_BATTERY_CAPACITY to batteryCapacity,
                    SettingsRepository.KEY_FUEL_TANK_CAPACITY to fuelTankCapacity,
                ),
            )
            _uiState.update { it.copy(capacitySaveStatus = appContext.getString(R.string.settings_saved)) }
            delay(2_000L)
            _uiState.update { it.copy(capacitySaveStatus = null) }
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

    fun updateFuelPricePerLiter(value: String) {
        _uiState.update { it.copy(fuelPricePerLiter = value) }
    }

    /** Save tariffs to DB and calculate costs for new trips. */
    fun saveTariffs() {
        val state = _uiState.value
        viewModelScope.launch {
            settingsRepository.setStrings(
                mapOf(
                    SettingsRepository.KEY_HOME_TARIFF to state.homeTariff,
                    SettingsRepository.KEY_DC_TARIFF to state.dcTariff,
                    SettingsRepository.KEY_FUEL_PRICE_PER_LITER to state.fuelPricePerLiter,
                ),
            )
            val tariff = settingsRepository.getTripCostTariff()
            historyImporter.calculateMissingCosts(tariff, settingsRepository.getFuelPricePerLiter())
            _uiState.update { it.copy(tariffSaveStatus = appContext.getString(R.string.settings_saved)) }
            delay(2000)
            _uiState.update { it.copy(tariffSaveStatus = null) }
        }
    }

    private fun String.normalizedPositiveNumber(): String? =
        replace(',', '.').trim().takeIf { normalized ->
            normalized.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true
        }

    /** Recalculate cost for ALL trips using current tariff. */
    fun recalculateAllCosts() {
        viewModelScope.launch {
            val tariff = settingsRepository.getTripCostTariff()
            val fuelPrice = settingsRepository.getFuelPricePerLiter()
            val allTrips = tripRepository.getAllTrips().firstOrNull() ?: emptyList()
            var count = 0
            for (trip in allTrips) {
                val electricCost = trip.kwhConsumed?.let { it * tariff }
                val fuelCost = trip.fuelConsumedL?.let { it * fuelPrice }
                if (electricCost == null && fuelCost == null) continue
                tripRepository.updateTrip(trip.copy(cost = electricCost, fuelCost = fuelCost))
                count++
            }
            _uiState.update { it.copy(recalcStatus = appContext.getString(R.string.settings_recalc_done, count)) }
            delay(3000)
            _uiState.update { it.copy(recalcStatus = null) }
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

    private fun readChainLog(): String? {
        return BootDiagnosticsStore.read(appContext)?.chainLog
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

    fun uploadLogs() {
        if (_uiState.value.isUploadingLogs) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploadingLogs = true,
                    logUploadStatus = "正在上传诊断日志…",
                )
            }
            runCatching { logUploader.upload() }
                .onSuccess { result ->
                    val idLabel = result.id.takeIf { it > 0L }?.let { "，编号 #$it" }.orEmpty()
                    _uiState.update {
                        it.copy(
                            isUploadingLogs = false,
                            logUploadStatus = "日志上传成功$idLabel",
                        )
                    }
                }
                .onFailure { error ->
                    WjxFileLogger.w("SettingsViewModel", "diagnostic log upload failed", error)
                    _uiState.update {
                        it.copy(
                            isUploadingLogs = false,
                            logUploadStatus = "日志上传失败：${error.message ?: "网络不可用"}",
                        )
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
        availableUpdate = null
        _uiState.update { it.copy(showUpdateDialog = false, updateDialogState = UpdateState.Idle) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        UpdateChecker.setAutoCheckEnabled(appContext, enabled)
        _uiState.update { it.copy(autoCheckUpdates = enabled) }
    }

    /** Check for app updates from the configured update backend. */
    fun checkForUpdate() {
        viewModelScope.launch {
            availableUpdate = null
            _uiState.update { it.copy(updateDialogState = UpdateState.Checking, updateStatus = appContext.getString(R.string.settings_update_check_in_progress)) }
            try {
                val update = updateChecker.checkForUpdate(appContext, forceCheck = true)
                if (update != null) {
                    availableUpdate = update
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
                availableUpdate = null
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
        if (downloadJob?.isActive == true) return
        val update = availableUpdate ?: return
        downloadJob = viewModelScope.launch {
            try {
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
            } catch (e: CancellationException) {
                throw e // cooperative cancellation from hideUpdateDialog(); not an error
            } catch (e: Exception) {
                _uiState.update { it.copy(updateDialogState = UpdateState.Error(e.message ?: "下载失败")) }
            } finally {
                downloadJob = null
            }
        }
    }

    private companion object {
        const val ACTIVATION_PAYMENT_POLL_MS = 2_500L
    }

}
