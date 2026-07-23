package com.byd.wjx.vehicle

import android.annotation.SuppressLint
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.electronicmirror.ElectronicMirrorCameraSource
import com.byd.wjx.electronicmirror.VehicleCameraPropertyResolver
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

data class VehicleHardwareIdentity(
    val displayName: String,
    val modelVariant: String,
    val vehicle40dCode: String,
    val carSeries: String,
    val vehicleType: String,
    val controllerVersion: String,
    val controllerCompat: String,
    val controllerSeries: Int,
    val cameraSourceFingerprint: String,
    val compatibilityKey: String,
) {
    val isUsable: Boolean
        get() = compatibilityKey.isNotBlank() &&
            controllerSeries in setOf(23, 34, 57) &&
            cameraSourceFingerprint.length == 64 &&
            listOf(displayName, modelVariant, vehicle40dCode, carSeries, vehicleType)
                .any(String::isNotBlank)
}

data class VehicleIdentitySnapshot(
    val vin: String,
    val hardware: VehicleHardwareIdentity,
)

data class VehicleMirrorProfile(
    val profileId: String,
    val version: Int,
    val compatibilityKey: String,
    val cameraId: Int,
    val surfaceIndex: Int,
    val leftSource: ElectronicMirrorCameraSource,
    val rightSource: ElectronicMirrorCameraSource,
    val rearSource: ElectronicMirrorCameraSource,
    val updatedAt: String,
)

data class VehicleProfileResolveResult(
    val status: String,
    val message: String,
    val salesModel: String,
    val profile: VehicleMirrorProfile?,
)

@Singleton
class VehicleIdentityReader @Inject constructor(
    private val activationManager: ActivationManager,
) {
    fun snapshot(): VehicleIdentitySnapshot = VehicleIdentitySnapshot(
        vin = activationManager.currentSnapshot().deviceCode.trim(),
        hardware = VehicleIdentityResolver.read(),
    )
}

object VehicleIdentityResolver {
    private val compatibilityFields = listOf(
        "display_name",
        "model_variant",
        "vehicle_40d_code",
        "car_series",
        "vehicle_type",
        "controller_series",
        "controller_compat",
        "camera_source_fingerprint",
    )

    fun read(
        propertyReader: (String) -> String = VehicleSystemProperties::read,
    ): VehicleHardwareIdentity {
        val defaultName = propertyReader("persist.sys.byd.default_name").trim()
        val displayName = defaultName.ifBlank {
            propertyReader("persist.sys.byd.bluetooth_name").trim()
        }
        val modelVariant = propertyReader("persist.sys.model_variant.model").trim()
        val vehicle40dCode = propertyReader("persist.sys.vehicle_40d_code").trim()
        val carSeries = propertyReader("ro.build.car.series").trim()
        val vehicleType = propertyReader("ro.vehicle.type").trim()
        val controllerVersion = sequenceOf(
            "apps.setting.product.outswver",
            "ro.build.version.incremental",
            "ro.vendor.build.version.incremental",
            "ro.build.display.id",
        ).map(propertyReader)
            .map(String::trim)
            .firstOrNull { CarProfileDetector.parseControllerSeries(it) != null }
            .orEmpty()
        val controllerSeries = CarProfileDetector.parseControllerSeries(controllerVersion) ?: 0
        val controllerCompat = controllerCompatibility(controllerVersion)
        val cameraSort = VehicleCameraPropertyResolver.resolveForTag(
            VehicleCameraPropertyResolver.PANORAMA_CAMERA_TAG,
            propertyReader,
        )?.value.orEmpty()
        val cameraSourceFingerprint = cameraSort
            .takeIf(String::isNotBlank)
            ?.let(::sha256)
            .orEmpty()
        val values = listOf(
            displayName,
            modelVariant,
            vehicle40dCode,
            carSeries,
            vehicleType,
            controllerSeries.takeIf { it > 0 }?.toString().orEmpty(),
            controllerCompat,
            cameraSourceFingerprint,
        )
        val compatibilityKey = if (values.any { normalizeCompatibilityValue(it).isNotBlank() }) {
            compatibilityKey(values)
        } else {
            ""
        }
        return VehicleHardwareIdentity(
            displayName = displayName,
            modelVariant = modelVariant,
            vehicle40dCode = vehicle40dCode,
            carSeries = carSeries,
            vehicleType = vehicleType,
            controllerVersion = controllerVersion,
            controllerCompat = controllerCompat,
            controllerSeries = controllerSeries,
            cameraSourceFingerprint = cameraSourceFingerprint,
            compatibilityKey = compatibilityKey,
        )
    }

    fun currentCompatibilityKey(): String = read().compatibilityKey

    fun compatibilityKey(values: List<String>): String {
        require(values.size == compatibilityFields.size)
        val canonical = compatibilityFields.zip(values).joinToString("\n") { (name, value) ->
            "$name=${normalizeCompatibilityValue(value)}"
        }
        return sha256(canonical)
    }

    fun controllerCompatibility(raw: String): String = raw.trim()
        .split('.', '-', '_')
        .map(String::trim)
        .filter(String::isNotBlank)
        .take(3)
        .joinToString(".")

    private fun normalizeCompatibilityValue(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

object VehicleProfileJson {
    fun parse(root: JSONObject): VehicleProfileResolveResult {
        val profileObject = root.optJSONObject("profile")
        val profile = profileObject?.let(::parseProfile)
        return VehicleProfileResolveResult(
            status = root.optString("profileStatus", "unknown"),
            message = root.optString("message", ""),
            salesModel = root.optString("salesModel", ""),
            profile = profile,
        )
    }

    private fun parseProfile(json: JSONObject): VehicleMirrorProfile {
        val cameraId = json.getInt("cameraId")
        require(cameraId in 0..3) { "服务器摄像头 ID 超出 0～3" }
        val surfaceIndex = json.optInt("surfaceIndex", 0)
        require(surfaceIndex in 0..15) { "服务器 Surface Index 超出 0～15" }
        val left = parseSource(json.getString("leftSource"))
        val right = parseSource(json.getString("rightSource"))
        val rear = parseSource(json.getString("rearSource"))
        require(setOf(left, right, rear).size == 3) { "服务器画面方向映射存在重复" }
        return VehicleMirrorProfile(
            profileId = json.getString("profileId").trim().also {
                require(it.isNotBlank()) { "服务器配置编号为空" }
            },
            version = json.getInt("version").also {
                require(it > 0) { "服务器配置版本无效" }
            },
            compatibilityKey = json.getString("compatibilityKey").trim().lowercase(Locale.ROOT),
            cameraId = cameraId,
            surfaceIndex = surfaceIndex,
            leftSource = left,
            rightSource = right,
            rearSource = rear,
            updatedAt = json.optString("updatedAt", ""),
        )
    }

    private fun parseSource(value: String): ElectronicMirrorCameraSource =
        ElectronicMirrorCameraSource.entries.firstOrNull { it.name == value.trim().uppercase(Locale.ROOT) }
            ?: error("服务器画面来源无效：$value")
}

object VehicleSystemProperties {
    @SuppressLint("PrivateApi")
    fun read(name: String): String {
        val direct = runCatching {
            val type = Class.forName("android.os.SystemProperties")
            type.getMethod("get", String::class.java).invoke(null, name) as? String
        }.getOrNull().orEmpty()
        if (direct.isNotBlank()) return direct

        return runCatching {
            val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
            val line = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
            process.waitFor()
            line.orEmpty()
        }.getOrDefault("")
    }
}
