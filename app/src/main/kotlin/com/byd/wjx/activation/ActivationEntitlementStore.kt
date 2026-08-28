package com.byd.wjx.activation

import android.content.SharedPreferences
import com.byd.wjx.log.WjxFileLogger
import java.util.Locale
import org.json.JSONObject

internal data class AppliedServerEntitlement(
    val activated: Boolean,
    val boundActivationCode: String,
    val serverTrialEndsAt: Long,
)

internal data class ActivationRefreshBackoff(
    val failureCount: Int,
    val retryAfterMs: Long,
)

internal class ActivationEntitlementStore(
    private val preferences: SharedPreferences,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun ensureStarted(): Long = ensureTrialStarted(currentTimeMillis())

    fun shouldRefresh(
        forceAfterMs: Long,
        refreshForMissingDevice: Boolean = false,
    ): Boolean {
        val now = currentTimeMillis()
        if (forceAfterMs <= 0L) return true
        val nextAttemptAt = preferences.getLong(KEY_NEXT_REFRESH_ATTEMPT_AT, 0L)
        if (nextAttemptAt > now) return false
        val lastCheckedAt = preferences.getLong(KEY_LAST_CHECKED_AT, 0L)
        return refreshForMissingDevice ||
            lastCheckedAt <= 0L ||
            now - lastCheckedAt >= forceAfterMs
    }

    fun recordRefreshFailure(): ActivationRefreshBackoff {
        val now = currentTimeMillis()
        val failureCount = (preferences.getInt(KEY_REFRESH_FAILURE_COUNT, 0) + 1)
            .coerceAtMost(MAX_REFRESH_FAILURE_COUNT)
        val retryAfterMs = refreshFailureDelayMs(failureCount)
        preferences.edit()
            .putInt(KEY_REFRESH_FAILURE_COUNT, failureCount)
            .putLong(KEY_NEXT_REFRESH_ATTEMPT_AT, now + retryAfterMs)
            .apply()
        return ActivationRefreshBackoff(failureCount, retryAfterMs)
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
        preferences.edit()
            .putBoolean(KEY_ACTIVATED, activated)
            .putString(KEY_ACTIVATED_DEVICE_CODE, if (activated) normalizedDevice else "")
            .putString(KEY_BOUND_ACTIVATION_CODE, boundActivationCode)
            .putLong(KEY_ACTIVATED_AT, activatedAt)
            .putLong(KEY_SERVER_TRIAL_ENDS_AT, serverTrialEndsAt)
            .putLong(KEY_LAST_CHECKED_AT, now)
            .putInt(KEY_REFRESH_FAILURE_COUNT, 0)
            .putLong(KEY_NEXT_REFRESH_ATTEMPT_AT, 0L)
            .apply()
        return AppliedServerEntitlement(
            activated = activated,
            boundActivationCode = boundActivationCode,
            serverTrialEndsAt = serverTrialEndsAt,
        )
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
        private const val KEY_REFRESH_FAILURE_COUNT = "refresh_failure_count"
        private const val KEY_NEXT_REFRESH_ATTEMPT_AT = "next_refresh_attempt_at"
        private const val MAX_REFRESH_FAILURE_COUNT = 4

        internal fun refreshFailureDelayMs(failureCount: Int): Long = when (failureCount) {
            1 -> 60_000L
            2 -> 2L * 60_000L
            3 -> 5L * 60_000L
            else -> 10L * 60_000L
        }
    }
}
