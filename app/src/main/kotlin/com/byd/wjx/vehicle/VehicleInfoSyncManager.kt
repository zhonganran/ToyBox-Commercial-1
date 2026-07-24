package com.byd.wjx.vehicle

import android.content.Context
import com.byd.wjx.BuildConfig
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class VehicleInfoSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val activationManager: ActivationManager,
    private val identityReader: VehicleIdentityReader,
    private val vehicleStateRepository: VehicleStateRepository,
    private val okHttpClient: OkHttpClient,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val identityPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val started = AtomicBoolean(false)
    private val _vehicleInfo = MutableStateFlow(readCachedVehicleInfo())
    val vehicleInfo: StateFlow<VehicleInfo?> = _vehicleInfo.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { syncOnce() }
                    .onFailure { error ->
                        WjxFileLogger.w(TAG, "vehicle info heartbeat failed: ${error.message}", error)
                    }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    suspend fun syncOnce(): VehicleInfo = withContext(Dispatchers.IO) {
        _vehicleInfo.value = retainAndCache(identityReader.snapshot())
        activationManager.refreshIfNeeded()
        val info = retainAndCache(identityReader.snapshot())
        _vehicleInfo.value = info
        val state = vehicleStateRepository.state.value
        require(info.vin.isNotBlank()) { "尚未读取到车架号" }
        val deviceToken = activationManager.currentDeviceToken()
        require(deviceToken.isNotBlank()) { "设备凭证尚未生成，请刷新授权状态" }
        val payload = JSONObject().apply {
            put("deviceCode", info.vin)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("packageName", appContext.packageName)
            put("vehicle", JSONObject().apply {
                put("vinYear", info.vinYear ?: JSONObject.NULL)
                put("displayName", info.displayName)
                put("modelVariant", info.modelVariant)
                put("carSeries", info.carSeries)
                put("vehicleType", info.vehicleType)
                put("controllerVersion", info.controllerVersion)
                put("controllerSeries", info.controllerSeries)
            })
            put("runtime", JSONObject().apply {
                put("connected", state.connected)
                put("powerState", state.data?.powerState ?: JSONObject.NULL)
            })
        }
        val request = Request.Builder()
            .url(BuildConfig.VEHICLE_INFO_API_URL)
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $deviceToken")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || !json.optBoolean("success", false)) {
                error(json.optString("message", "HTTP ${response.code}"))
            }
        }
        WjxFileLogger.i(
            TAG,
            "vehicle info reported vin=${info.vin} year=${info.vinYear ?: "-"} " +
                "name=${info.displayName.ifBlank { "-" }}",
        )
        info
    }

    private fun retainAndCache(current: VehicleInfo): VehicleInfo {
        val previous = _vehicleInfo.value
        val merged = retainKnownVehicleInfo(previous, current)
        if (current.vin.isNotBlank() && previous?.vin?.isNotBlank() == true && current.vin != previous.vin) {
            identityPrefs.edit().clear().apply()
        }
        if (merged.vin.isNotBlank() && merged.displayName.isNotBlank()) {
            identityPrefs.edit()
                .putString(KEY_VIN, merged.vin)
                .putInt(KEY_VIN_YEAR, merged.vinYear ?: UNKNOWN_INT)
                .putString(KEY_DISPLAY_NAME, merged.displayName)
                .putString(KEY_MODEL_VARIANT, merged.modelVariant)
                .putString(KEY_CAR_SERIES, merged.carSeries)
                .putString(KEY_VEHICLE_TYPE, merged.vehicleType)
                .putString(KEY_CONTROLLER_VERSION, merged.controllerVersion)
                .putInt(KEY_CONTROLLER_SERIES, merged.controllerSeries)
                .apply()
        }
        return merged
    }

    private fun readCachedVehicleInfo(): VehicleInfo? {
        val vin = identityPrefs.getString(KEY_VIN, "").orEmpty()
        val displayName = identityPrefs.getString(KEY_DISPLAY_NAME, "").orEmpty()
        if (vin.isBlank() || displayName.isBlank()) return null
        return VehicleInfo(
            vin = vin,
            vinYear = identityPrefs.getInt(KEY_VIN_YEAR, UNKNOWN_INT).takeIf { it > 0 },
            displayName = displayName,
            modelVariant = identityPrefs.getString(KEY_MODEL_VARIANT, "").orEmpty(),
            carSeries = identityPrefs.getString(KEY_CAR_SERIES, "").orEmpty(),
            vehicleType = identityPrefs.getString(KEY_VEHICLE_TYPE, "").orEmpty(),
            controllerVersion = identityPrefs.getString(KEY_CONTROLLER_VERSION, "").orEmpty(),
            controllerSeries = identityPrefs.getInt(KEY_CONTROLLER_SERIES, 0),
        )
    }

    private companion object {
        const val TAG = "VehicleInfoSync"
        const val HEARTBEAT_INTERVAL_MS = 60_000L
        const val PREFS_NAME = "wjx_vehicle_info"
        const val KEY_VIN = "vin"
        const val KEY_VIN_YEAR = "vin_year"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_MODEL_VARIANT = "model_variant"
        const val KEY_CAR_SERIES = "car_series"
        const val KEY_VEHICLE_TYPE = "vehicle_type"
        const val KEY_CONTROLLER_VERSION = "controller_version"
        const val KEY_CONTROLLER_SERIES = "controller_series"
        const val UNKNOWN_INT = -1
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
