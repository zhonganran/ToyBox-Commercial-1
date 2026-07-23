package com.byd.wjx.vehicle

import android.content.Context
import com.byd.wjx.BuildConfig
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.di.ApplicationScope
import com.byd.wjx.electronicmirror.ElectronicMirrorCoordinator
import com.byd.wjx.electronicmirror.ElectronicMirrorPreferences
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class VehicleProfileSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val activationManager: ActivationManager,
    private val identityReader: VehicleIdentityReader,
    private val vehicleStateRepository: VehicleStateRepository,
    private val mirrorPreferences: ElectronicMirrorPreferences,
    private val mirrorCoordinator: ElectronicMirrorCoordinator,
    private val okHttpClient: OkHttpClient,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { syncOnce() }
                    .onFailure { error ->
                        mirrorPreferences.recordProfileSync("network_error", error.message.orEmpty())
                        WjxFileLogger.w(TAG, "vehicle profile sync failed: ${error.message}", error)
                    }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    suspend fun syncOnce(): VehicleProfileResolveResult = withContext(Dispatchers.IO) {
        activationManager.refreshIfNeeded()
        val identity = identityReader.snapshot()
        WjxFileLogger.i(
            TAG,
            "identity usable=${identity.hardware.isUsable} vin=${identity.vin} " +
                "name=${identity.hardware.displayName.ifBlank { "-" }} " +
                "variant=${identity.hardware.modelVariant.ifBlank { "-" }} " +
                "code40d=${identity.hardware.vehicle40dCode.ifBlank { "-" }} " +
                "series=${identity.hardware.carSeries.ifBlank { "-" }} " +
                "type=${identity.hardware.vehicleType.ifBlank { "-" }} " +
                "controller=${identity.hardware.controllerSeries}/${identity.hardware.controllerCompat.ifBlank { "-" }} " +
                "cameraFingerprint=${identity.hardware.cameraSourceFingerprint.ifBlank { "-" }} " +
                "compatibility=${identity.hardware.compatibilityKey.ifBlank { "-" }}",
        )
        require(identity.vin.isNotBlank()) { "尚未读取到车架号" }
        require(identity.hardware.isUsable) { "车型兼容指纹尚未就绪" }

        val state = vehicleStateRepository.state.value
        val currentConfig = mirrorPreferences.snapshot()
        val remoteProfileActive = !currentConfig.manualProfileOverride &&
            currentConfig.remoteProfileId.isNotBlank()
        val payload = JSONObject().apply {
            put("deviceCode", identity.vin)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("packageName", appContext.packageName)
            put("vehicle", JSONObject().apply {
                put("displayName", identity.hardware.displayName)
                put("modelVariant", identity.hardware.modelVariant)
                put("vehicle40dCode", identity.hardware.vehicle40dCode)
                put("carSeries", identity.hardware.carSeries)
                put("vehicleType", identity.hardware.vehicleType)
                put("controllerVersion", identity.hardware.controllerVersion)
                put("controllerCompat", identity.hardware.controllerCompat)
                put("controllerSeries", identity.hardware.controllerSeries)
                put("cameraSourceFingerprint", identity.hardware.cameraSourceFingerprint)
                put("compatibilityKey", identity.hardware.compatibilityKey)
            })
            put("runtime", JSONObject().apply {
                put("connected", state.connected)
                put("powerState", state.data?.powerState ?: JSONObject.NULL)
                put("runtimeState", runtimeState(state.connected, state.data?.powerState))
                put("manualOverride", currentConfig.manualProfileOverride)
                put("currentProfileId", if (remoteProfileActive) currentConfig.remoteProfileId else "")
                put("currentProfileVersion", if (remoteProfileActive) currentConfig.remoteProfileVersion else 0)
                put("cameraId", currentConfig.cameraIdOverride)
                put("surfaceIndex", currentConfig.panoramaSurfaceIndex)
                put("leftSource", currentConfig.leftSource.name)
                put("rightSource", currentConfig.rightSource.name)
                put("rearSource", currentConfig.rearSource.name)
            })
        }
        val request = Request.Builder()
            .url(BuildConfig.VEHICLE_PROFILE_API_URL)
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || !json.optBoolean("success", false)) {
                error(json.optString("message", "HTTP ${response.code}"))
            }
            val result = VehicleProfileJson.parse(json)
            val profile = result.profile
            if (profile != null) {
                require(profile.compatibilityKey == identity.hardware.compatibilityKey) {
                    "服务器配置与本车兼容指纹不一致"
                }
                val changed = mirrorPreferences.installRemoteProfile(profile)
                if (changed) mirrorCoordinator.applyPreferences()
                WjxFileLogger.i(
                    TAG,
                    "verified profile resolved id=${profile.profileId} version=${profile.version} " +
                    "camera=${profile.cameraId} manual=${currentConfig.manualProfileOverride}",
                )
            } else if (result.status == "pending" || result.status == "unauthorized") {
                val changed = mirrorPreferences.clearRemoteProfile()
                if (changed && !currentConfig.manualProfileOverride) {
                    mirrorCoordinator.applyPreferences()
                }
            }
            mirrorPreferences.recordProfileSync(result.status, result.message)
            result
        }
    }

    private fun runtimeState(connected: Boolean, powerState: Int?): String = when {
        powerState == 0 -> "sleep"
        connected -> "active"
        else -> "disconnected"
    }

    private companion object {
        const val TAG = "VehicleProfileSync"
        const val HEARTBEAT_INTERVAL_MS = 60_000L
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
