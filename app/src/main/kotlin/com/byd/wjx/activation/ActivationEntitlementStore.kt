package com.byd.wjx.activation

import android.content.SharedPreferences
import com.byd.wjx.log.WjxFileLogger
import java.util.Locale
import org.json.JSONObject

internal data class AppliedServerEntitlement(
    val activated: Boolean,
    val boundActivationCode: String,
    val hasDeviceToken: Boolean,
    val serverTrialEndsAt: Long,
)

internal class ActivationEntitlementStore(
    private val preferences: SharedPreferences,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun ensureStarted(): Long = ensureTrialStarted(currentTimeMillis())

    fun currentDeviceToken(): String =
        preferences.getString(KEY_DEVICE_API_TOKEN, "").orEmpty()

    fun shouldRefresh(forceAfterMs: Long): Boolean {
        val now = currentTimeMillis()
        val lastCheckedAt = preferences.getLong(KEY_LAST_CHECKED_AT, 0L)
        val missingCredential = currentDeviceToken().isBlank() &&
            !preferences.getBoolean(KEY_DEVICE_CREDENTIAL_REQUIRES_ACTIVATION, false)
        return missingCredential ||
            forceAfterMs <= 0L ||
            lastCheckedAt <= 0L ||
            now - lastCheckedAt >= forceAfterMs
    }

    fun currentSnapshot(
        deviceCode: String,
        deviceAliases: List<String>,
    ): ActivationSnapshot {
        val now = currentTimeMillis()
        val firstStartedAt = ensureTrialStarted(now)
        val serverTrialEndsAt = preferences.getLong(KEY_SERVER_TRIAL_ENDS_AT, 0L)
        val trialEndsAt = if (serverTrialEndsAt > 0L) {
            serverTrialEndsAt
        } else {
            firstStartedAt + ActivationSnapshot.TRIAL_DURATION_MS
        }
        val remaining = (trialEndsAt - now).coerceAtLeast(0L)
        val normalizedAliases = (deviceAliases + deviceCode)
            .map(ActivationDeviceIdentityProvider::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .distinct()
        val activatedDevice = preferences.getString(KEY_ACTIVATED_DEVICE_CODE, "").orEmpty()
        val activated = preferences.getBoolean(KEY_ACTIVATED, false) &&
            normalizedAliases.isNotEmpty() &&
            activatedDevice in normalizedAliases

        return ActivationSnapshot(
            deviceCode = deviceCode,
            boundActivationCode = preferences.getString(KEY_BOUND_ACTIVATION_CODE, "").orEmpty(),
            activated = activated,
            trialActive = !activated && normalizedAliases.isNotEmpty() && remaining > 0L,
            trialExpired = !activated && (normalizedAliases.isEmpty() || remaining <= 0L),
            firstStartedAt = firstStartedAt,
            trialEndsAt = trialEndsAt,
            remainingTrialMillis = remaining,
            activatedAt = preferences.getLong(KEY_ACTIVATED_AT, 0L),
            lastCheckedAt = preferences.getLong(KEY_LAST_CHECKED_AT, 0L),
        )
    }

    fun applyServerStatus(
        json: JSONObject,
        normalizedDevice: String,
        requestedActivationCode: String = "",
    ): AppliedServerEntitlement {
        val activated = json.optBoolean("activated", false) && normalizedDevice.isNotBlank()
        val now = currentTimeMillis()
        val serverTrialEndsAt = activationJsonMillis(json, "trialEndsAt")
        val activatedAt = activationJsonMillis(json, "activatedAt").takeIf { it > 0L }
            ?: if (activated) now else 0L
        val existingBoundCode = preferences.getString(KEY_BOUND_ACTIVATION_CODE, "").orEmpty()
        val boundActivationCode = if (activated) {
            extractBoundActivationCode(json, requestedActivationCode).ifBlank { existingBoundCode }
        } else {
            ""
        }
        val deviceToken = json.optString("deviceToken", "").trim()
        val editor = preferences.edit()
            .putBoolean(KEY_ACTIVATED, activated)
            .putString(KEY_ACTIVATED_DEVICE_CODE, if (activated) normalizedDevice else "")
            .putString(KEY_BOUND_ACTIVATION_CODE, boundActivationCode)
            .putLong(KEY_ACTIVATED_AT, activatedAt)
            .putLong(KEY_SERVER_TRIAL_ENDS_AT, serverTrialEndsAt)
            .putLong(KEY_LAST_CHECKED_AT, now)
        if (deviceToken.isNotBlank()) {
            editor
                .putString(KEY_DEVICE_API_TOKEN, deviceToken)
                .putBoolean(KEY_DEVICE_CREDENTIAL_REQUIRES_ACTIVATION, false)
        } else if (json.optBoolean("deviceCredentialRequired", false)) {
            editor
                .remove(KEY_DEVICE_API_TOKEN)
                .putBoolean(KEY_DEVICE_CREDENTIAL_REQUIRES_ACTIVATION, true)
        } else if (!activated) {
            editor
                .remove(KEY_DEVICE_API_TOKEN)
                .putBoolean(KEY_DEVICE_CREDENTIAL_REQUIRES_ACTIVATION, false)
        }
        editor.apply()
        return AppliedServerEntitlement(
            activated = activated,
            boundActivationCode = boundActivationCode,
            hasDeviceToken = deviceToken.isNotBlank(),
            serverTrialEndsAt = serverTrialEndsAt,
        )
    }

    fun restoreOfflineLicense(deviceCode: String, license: JSONObject) {
        val now = currentTimeMillis()
        preferences.edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putString(KEY_ACTIVATED_DEVICE_CODE, deviceCode)
            .putString(KEY_BOUND_ACTIVATION_CODE, license.optString("licenseCodeMasked", ""))
            .putLong(KEY_ACTIVATED_AT, license.optLong("activatedAt", now))
            .putLong(KEY_LAST_CHECKED_AT, now)
            .apply()
    }

    private fun ensureTrialStarted(now: Long): Long {
        val existing = preferences.getLong(KEY_FIRST_STARTED_AT, 0L)
        if (existing > 0L) return existing
        preferences.edit().putLong(KEY_FIRST_STARTED_AT, now).apply()
        WjxFileLogger.i(TAG, "trial started at=$now")
        return now
    }

    private fun extractBoundActivationCode(json: JSONObject, fallbackCode: String): String {
        val serverCode = listOf(
            "boundActivationCode",
            "activationCode",
            "activationCodeMasked",
            "maskedActivationCode",
        ).firstNotNullOfOrNull { key ->
            json.optString(key, "").takeIf(String::isNotBlank)
        }.orEmpty()
        return maskActivationCode(serverCode.ifBlank { fallbackCode })
    }

    private fun maskActivationCode(value: String): String {
        val code = value.trim().uppercase(Locale.US)
        if (code.isBlank()) return ""
        if ('*' in code || '\u2022' in code) return code
        val compact = code.filter(Char::isLetterOrDigit)
        if (compact.length <= 8) return compact
        return "${compact.take(4)}-****-****-${compact.takeLast(4)}"
    }

    companion object {
        private const val TAG = "ActivationEntitlementStore"
        private const val KEY_FIRST_STARTED_AT = "first_started_at"
        private const val KEY_ACTIVATED = "activated"
        private const val KEY_ACTIVATED_DEVICE_CODE = "activated_device_code"
        private const val KEY_BOUND_ACTIVATION_CODE = "bound_activation_code"
        private const val KEY_ACTIVATED_AT = "activated_at"
        private const val KEY_SERVER_TRIAL_ENDS_AT = "server_trial_ends_at"
        private const val KEY_LAST_CHECKED_AT = "last_checked_at"
        private const val KEY_DEVICE_API_TOKEN = "device_api_token"
        private const val KEY_DEVICE_CREDENTIAL_REQUIRES_ACTIVATION =
            "device_credential_requires_activation"
    }
}
