package com.byd.wjx.activation

import android.content.Context
import android.content.SharedPreferences
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.WjxDirectories
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class OfflineLicenseMatch(
    val deviceCode: String,
)

internal class OfflineLicenseVerifier(
    private val packageName: String,
    private val publicKeyDer: String = OFFLINE_LICENSE_PUBLIC_KEY_DER,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun verify(license: JSONObject, deviceAliases: List<String>): OfflineLicenseMatch? {
        if (license.optInt("version", 0) != 1) return null
        if (!license.optBoolean("activated", false)) return null
        val licensedPackage = license.optString("packageName", "")
        if (licensedPackage.isNotBlank() && licensedPackage != packageName) return null
        val expiresAt = license.optLong("expiresAt", 0L)
        if (expiresAt > 0L && expiresAt <= currentTimeMillis()) return null
        if (!verifySignature(license, license.optString("signature", ""))) return null

        val aliases = deviceAliases
            .map(::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .toSet()
        val matched = deviceCodes(license).firstOrNull { it in aliases } ?: return null
        return OfflineLicenseMatch(matched)
    }

    fun deviceCodes(license: JSONObject): List<String> =
        license.optJSONArray("deviceCodes")
            ?.toStringList()
            ?.map(::normalizeDeviceCode)
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()

    internal fun canonicalForSigning(license: JSONObject): String {
        val deviceCodes = deviceCodes(license).sorted().joinToString(",")
        val features = license.optJSONArray("features")
            ?.toStringList()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.sorted()
            ?.joinToString(",")
            .orEmpty()
        return listOf(
            "version=${license.optInt("version", 1)}",
            "signatureAlg=${license.optString("signatureAlg", "RS256")}",
            "deviceCodes=$deviceCodes",
            "activated=${if (license.optBoolean("activated", false)) "1" else "0"}",
            "activatedAt=${license.optLong("activatedAt", 0L)}",
            "issuedAt=${license.optLong("issuedAt", 0L)}",
            "expiresAt=${license.optLong("expiresAt", 0L)}",
            "packageName=${license.optString("packageName", "")}",
            "licenseCodeMasked=${license.optString("licenseCodeMasked", "")}",
            "features=$features",
        ).joinToString("\n")
    }

    private fun verifySignature(license: JSONObject, signature: String): Boolean {
        if (signature.isBlank() || license.optString("signatureAlg", "") != "RS256") return false
        return runCatching {
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(decodeBase64(publicKeyDer)))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(canonicalForSigning(license).toByteArray(Charsets.UTF_8))
            verifier.verify(decodeBase64(signature))
        }.onFailure {
            WjxFileLogger.w(TAG, "offline license RS256 verify failed: ${it.message}", it)
        }.getOrDefault(false)
    }

    private fun decodeBase64(value: String): ByteArray {
        val compact = value.trim().replace("\n", "")
        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        return if ('-' in compact || '_' in compact) {
            Base64.getUrlDecoder().decode(padded)
        } else {
            Base64.getDecoder().decode(padded)
        }
    }

    private fun normalizeDeviceCode(value: String): String =
        value.uppercase(Locale.US).filter { it.isLetterOrDigit() }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { index -> optString(index, "").takeIf(String::isNotBlank) }

    companion object {
        private const val TAG = "OfflineLicenseVerifier"
        private const val OFFLINE_LICENSE_PUBLIC_KEY_DER =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxkUw/ZPkswbCgkV60bKska+/srSc216njAlFXq6eC5ws57doamyRvJvniWxv0aBf2vmgvTiKzdKCvZ2WmyIlTzl2W7GTvgeuJWcLURW9Np451/O/fcrTrI1bOB2FI2JhFnH2ZxwUS4yGU7kb+m+l6JgfAtaD/sBb4s++Ejz31+Kh5/ZLTmMMtSWi01B80NfR5gBriNhGR3ZL+l7kzp9zxA3XW89Jz2jkjedytZGyufgr8WLWxtEDpW2nanWA4Tqwu91A6iV95YrnUUEjh5EH54SA9X9HFAdAfFYWcfGxWkxD0lpQC9uTTCf+0lBTFt4EbJ6RYsiQHl0eXNLV48LGNQIDAQAB"
    }
}

internal class OfflineLicenseStore(
    context: Context,
    private val preferences: SharedPreferences,
    private val verifier: OfflineLicenseVerifier = OfflineLicenseVerifier(context.packageName),
) {
    private val privateFile = File(context.filesDir, OFFLINE_LICENSE_FILE_NAME)

    fun read(): JSONObject? {
        val raw = listOf(
            preferences.getString(KEY_OFFLINE_LICENSE_JSON, null),
            runCatching { privateFile.takeIf(File::isFile)?.readText() }.getOrNull(),
            runCatching { WjxDirectories.offlineLicense.takeIf(File::isFile)?.readText() }.getOrNull(),
        ).firstOrNull { !it.isNullOrBlank() } ?: return null
        return runCatching { JSONObject(raw) }
            .onFailure { WjxFileLogger.w(TAG, "offline license parse failed: ${it.message}", it) }
            .getOrNull()
    }

    fun verify(license: JSONObject, deviceAliases: List<String>): OfflineLicenseMatch? =
        verifier.verify(license, deviceAliases)

    fun deviceCodes(license: JSONObject): List<String> = verifier.deviceCodes(license)

    fun persist(
        license: JSONObject?,
        deviceAliases: List<String>,
        normalizedDevice: String,
    ) {
        if (license == null) {
            WjxFileLogger.w(TAG, "offline license missing from server response")
            return
        }
        val aliases = (deviceAliases + normalizedDevice)
            .map { it.uppercase(Locale.US).filter(Char::isLetterOrDigit) }
            .filter(String::isNotBlank)
            .distinct()
        if (verifier.verify(license, aliases) == null) {
            WjxFileLogger.w(TAG, "offline license rejected while persisting")
            return
        }

        val normalized = JSONObject(license.toString()).toString()
        runCatching {
            preferences.edit().putString(KEY_OFFLINE_LICENSE_JSON, normalized).apply()
            privateFile.writeText(normalized)
        }.onFailure {
            WjxFileLogger.w(TAG, "offline license private persist failed: ${it.message}", it)
        }
        runCatching {
            if (WjxDirectories.ensureBaseDirs()) {
                WjxDirectories.offlineLicense.writeText(normalized)
            }
        }.onFailure {
            WjxFileLogger.w(TAG, "offline license external persist failed: ${it.message}", it)
        }
        WjxFileLogger.i(TAG, "offline license persisted aliases=${aliases.size}")
    }

    fun remember(license: JSONObject) {
        preferences.edit().putString(KEY_OFFLINE_LICENSE_JSON, license.toString()).apply()
    }

    fun clear(reason: String) {
        preferences.edit().remove(KEY_OFFLINE_LICENSE_JSON).apply()
        runCatching { privateFile.delete() }
        runCatching { WjxDirectories.offlineLicense.delete() }
        WjxFileLogger.i(TAG, "offline license cache cleared reason=$reason")
    }

    companion object {
        private const val TAG = "OfflineLicenseStore"
        private const val KEY_OFFLINE_LICENSE_JSON = "offline_license_json"
        private const val OFFLINE_LICENSE_FILE_NAME = "wjx_license.dat"
    }
}
