package com.byd.wjx.vehicle

import android.content.Context
import com.byd.wjx.BuildConfig
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.data.repository.VehicleDataFreshness
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.ui.settings.SettingsLogUploader
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class VehicleInfoSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val activationManager: ActivationManager,
    private val identityReader: VehicleIdentityReader,
    private val vehicleStateRepository: VehicleStateRepository,
    private val tripRepository: TripRepository,
    private val chargeRepository: ChargeRepository,
    private val logUploader: SettingsLogUploader,
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
                val data = state.data.takeIf { state.freshness == VehicleDataFreshness.FRESH }
                put("connected", state.connected)
                putOptional("powerState", data?.powerState)
                putOptional("soc", data?.soc)
                putOptional("electricRangeKm", data?.elecDrivingRangeKm)
                putOptional("fuelRangeKm", data?.fuelDrivingRangeKm)
                putOptional("gear", data?.gear)
                putOptional("lockState", aggregateLockState(data?.lockFL, data?.lockFR, data?.lockRL, data?.lockRR))
                putOptional("insideTemp", data?.insideTemp)
                putOptional("outsideTemp", data?.exteriorTemp)
                putOptional("mileageKm", data?.mileage)
                putOptional("voltage12v", data?.voltage12v)
                putOptional("chargingStatus", data?.chargingStatus)
            })
        }
        val request = Request.Builder()
            .url(BuildConfig.VEHICLE_INFO_API_URL)
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $deviceToken")
            .build()
        val heartbeatJson = okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || !json.optBoolean("success", false)) {
                error(json.optString("message", "HTTP ${response.code}"))
            }
            json
        }
        runCatching { handleRemoteLogRequest(heartbeatJson) }
            .onFailure { error ->
                WjxFileLogger.w(TAG, "remote log upload failed: ${error.message}", error)
            }
        runCatching { syncHistoryIfDue(info.vin, deviceToken) }
            .onFailure { error ->
                WjxFileLogger.w(TAG, "vehicle history sync failed: ${error.message}", error)
            }
        WjxFileLogger.i(
            TAG,
            "vehicle info reported vin=${info.vin} year=${info.vinYear ?: "-"} " +
                "name=${info.displayName.ifBlank { "-" }}",
        )
        info
    }

    private suspend fun handleRemoteLogRequest(heartbeat: JSONObject) {
        val request = heartbeat.optJSONObject("logUploadRequest") ?: return
        val requestId = request.optLong("id", 0L)
        if (requestId <= 0L || identityPrefs.getLong(KEY_COMPLETED_LOG_REQUEST_ID, 0L) == requestId) return
        val result = logUploader.upload()
        identityPrefs.edit().putLong(KEY_COMPLETED_LOG_REQUEST_ID, requestId).apply()
        WjxFileLogger.i(TAG, "remote diagnostic log uploaded request=$requestId log=${result.id}")
    }

    private suspend fun syncHistoryIfDue(vin: String, deviceToken: String) {
        val now = System.currentTimeMillis()
        val lastAttempt = identityPrefs.getLong(KEY_HISTORY_SYNC_ATTEMPT_AT, 0L)
        if (now - lastAttempt < HISTORY_SYNC_INTERVAL_MS) return
        identityPrefs.edit().putLong(KEY_HISTORY_SYNC_ATTEMPT_AT, now).apply()

        val allTrips = tripRepository.getAllTrips().first()
        val tripCursor = identityPrefs.getLong(KEY_TRIP_SYNC_CURSOR, 0L)
        val backfillTrips = allTrips
            .asSequence()
            .filter { it.id > tripCursor }
            .sortedBy { it.id }
            .take(MAX_BACKFILL_RECORDS)
            .toList()
        val selectedTrips = (backfillTrips + allTrips.take(RECENT_RECORDS))
            .distinctBy { it.id }
            .take(MAX_SYNC_RECORDS)

        val allCharges = chargeRepository.getAllCharges().first()
        val chargeCursor = identityPrefs.getLong(KEY_CHARGE_SYNC_CURSOR, 0L)
        val backfillCharges = allCharges
            .asSequence()
            .filter { it.id > chargeCursor }
            .sortedBy { it.id }
            .take(MAX_BACKFILL_RECORDS)
            .toList()
        val selectedCharges = (backfillCharges + allCharges.take(RECENT_RECORDS))
            .distinctBy { it.id }
            .take(MAX_SYNC_RECORDS)

        val pointsByTrip = tripRepository.getTripPointsByTripIds(
            selectedTrips.map(TripEntity::id).filter { it > 0L },
        )
        val pointLimitPerTrip = if (selectedTrips.isEmpty()) {
            0
        } else {
            (MAX_SYNC_POINTS / selectedTrips.size).coerceIn(MIN_POINTS_PER_TRIP, MAX_POINTS_PER_TRIP)
        }
        val selectedPoints = selectedTrips
            .asSequence()
            .flatMap { trip -> downsamplePoints(pointsByTrip[trip.id].orEmpty(), pointLimitPerTrip).asSequence() }
            .take(MAX_SYNC_POINTS)
            .toList()

        val payload = JSONObject().apply {
            put("deviceCode", vin)
            put("trips", JSONArray().apply { selectedTrips.forEach { put(it.toSyncJson()) } })
            put("tripPoints", JSONArray().apply { selectedPoints.forEach { put(it.toSyncJson()) } })
            put("charges", JSONArray().apply { selectedCharges.forEach { put(it.toSyncJson()) } })
        }
        val request = Request.Builder()
            .url(BuildConfig.VEHICLE_HISTORY_SYNC_API_URL)
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $deviceToken")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || !json.optBoolean("success", false)) {
                error(json.optString("message", "HTTP ${response.code}"))
            }
        }
        val editor = identityPrefs.edit().putLong(KEY_HISTORY_SYNCED_AT, now)
        backfillTrips.maxOfOrNull(TripEntity::id)?.let { editor.putLong(KEY_TRIP_SYNC_CURSOR, it) }
        backfillCharges.maxOfOrNull(ChargeEntity::id)?.let { editor.putLong(KEY_CHARGE_SYNC_CURSOR, it) }
        editor.apply()
        WjxFileLogger.i(
            TAG,
            "history synced trips=${selectedTrips.size} points=${selectedPoints.size} charges=${selectedCharges.size}",
        )
    }

    private fun TripEntity.toSyncJson(): JSONObject = JSONObject().apply {
        put("sourceId", id.toString())
        put("startTs", startTs)
        putOptional("endTs", endTs)
        putOptional("distanceKm", distanceKm)
        putOptional("kwhConsumed", kwhConsumed)
        putOptional("kwhPer100km", kwhPer100km)
        putOptional("avgSpeedKmh", avgSpeedKmh)
        putOptional("socStart", socStart)
        putOptional("socEnd", socEnd)
        putOptional("cost", cost)
    }

    private fun TripPointEntity.toSyncJson(): JSONObject = JSONObject().apply {
        put("sourceId", id.toString())
        put("tripSourceId", tripId.toString())
        put("timestamp", timestamp)
        put("lat", lat)
        put("lon", lon)
        putOptional("speedKmh", speedKmh)
    }

    private fun ChargeEntity.toSyncJson(): JSONObject = JSONObject().apply {
        put("sourceId", id.toString())
        put("startTs", startTs)
        putOptional("endTs", endTs)
        putOptional("socStart", socStart)
        putOptional("socEnd", socEnd)
        putOptional("kwhCharged", kwhCharged)
        putOptional("type", type)
        putOptional("cost", cost)
        putOptional("lat", lat)
        putOptional("lon", lon)
        put("status", status)
    }

    private fun downsamplePoints(points: List<TripPointEntity>, limit: Int): List<TripPointEntity> {
        if (limit <= 0 || points.isEmpty()) return emptyList()
        if (points.size <= limit) return points
        if (limit == 1) return listOf(points.first())
        return List(limit) { index ->
            points[index * (points.lastIndex) / (limit - 1)]
        }.distinctBy(TripPointEntity::id)
    }

    private fun aggregateLockState(vararg values: Int?): Int? {
        val known = values.filterNotNull()
        return when {
            known.isEmpty() -> null
            known.all { it == 2 } -> 2
            known.any { it == 1 } -> 1
            else -> null
        }
    }

    private fun JSONObject.putOptional(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
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
        const val KEY_HISTORY_SYNC_ATTEMPT_AT = "history_sync_attempt_at"
        const val KEY_HISTORY_SYNCED_AT = "history_synced_at"
        const val KEY_TRIP_SYNC_CURSOR = "trip_sync_cursor"
        const val KEY_CHARGE_SYNC_CURSOR = "charge_sync_cursor"
        const val KEY_COMPLETED_LOG_REQUEST_ID = "completed_log_request_id"
        const val UNKNOWN_INT = -1
        const val HISTORY_SYNC_INTERVAL_MS = 5 * 60_000L
        const val MAX_BACKFILL_RECORDS = 200
        const val RECENT_RECORDS = 50
        const val MAX_SYNC_RECORDS = 250
        const val MAX_SYNC_POINTS = 2_000
        const val MIN_POINTS_PER_TRIP = 2
        const val MAX_POINTS_PER_TRIP = 80
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
