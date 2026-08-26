package com.byd.wjx.activation

import android.content.SharedPreferences
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ActivationDeviceIdentityProvider(
    private val preferences: SharedPreferences,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val readMutex = Mutex()
    private var lastReadAt: Long = 0L
    private var lastReadCandidates: List<String> = emptyList()

    suspend fun refreshCandidates(forceHardware: Boolean = false): List<String> {
        val candidates = sanitizeCandidates(readCandidates(forceHardware))
        if (candidates.isNotEmpty()) rememberCandidates(candidates)
        return candidates
    }

    fun sanitizeCandidates(values: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        return buildList {
            values.forEach { raw ->
                val value = extractVin(raw).orEmpty()
                val normalized = normalizeDeviceCode(value)
                if (normalized.isNotBlank() && seen.add(normalized)) add(value)
            }
        }
    }

    fun rememberCandidates(candidates: List<String>) {
        val sanitized = sanitizeCandidates(candidates)
        if (sanitized.isEmpty()) return
        writeIdentity(sanitized.first(), sanitized)
    }

    fun rememberVerifiedDevice(primaryDeviceCode: String, aliases: List<String>) {
        val primary = extractVin(primaryDeviceCode) ?: return
        val sanitized = sanitizeCandidates(listOf(primary) + aliases)
        if (sanitized.isEmpty()) return
        writeIdentity(primary, sanitized)
    }

    fun storedVinCode(): String =
        extractVin(preferences.getString(KEY_DEVICE_CODE, "").orEmpty()).orEmpty()

    fun storedAliases(extra: List<String> = emptyList()): List<String> =
        (preferences.getString(KEY_DEVICE_CODE_ALIASES, "").orEmpty().split(",") + extra + storedVinCode())
            .mapNotNull(::extractVin)
            .map(::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .distinct()

    private suspend fun readCandidates(forceHardware: Boolean): List<String> = readMutex.withLock {
        val now = currentTimeMillis()
        val cacheWindow = if (lastReadCandidates.isNotEmpty()) {
            VIN_SUCCESS_CACHE_MS
        } else {
            VIN_EMPTY_RETRY_DELAY_MS
        }
        if (!forceHardware && lastReadAt > 0L && now - lastReadAt in 0 until cacheWindow) {
            WjxFileLogger.i(
                TAG,
                "VIN candidates reused from short cache valid=${lastReadCandidates.size} age=${now - lastReadAt}ms",
            )
            return@withLock lastReadCandidates
        }

        val candidates = mutableListOf<String>()
        readHelperVinRaw()?.let { raw ->
            logRawDeviceCodeSource("helper_bodywork", raw)
            candidates += extractVinCandidates(raw)
        }
        sanitizeCandidates(candidates).takeIf(List<String>::isNotEmpty)?.let {
            WjxFileLogger.i(TAG, "VIN candidates resolved by helper valid=${it.size}")
            lastReadAt = now
            lastReadCandidates = it
            return@withLock it
        }

        lastReadAt = now
        lastReadCandidates = emptyList()
        WjxFileLogger.i(TAG, "VIN candidates resolved by helper valid=0; no fallback source used")
        emptyList()
    }

    private suspend fun readHelperVinRaw(): String? {
        val helperReady = runCatching { helperBootstrap.ensureCurrent() }
            .onFailure { WjxFileLogger.w(TAG, "helper VIN ensureCurrent failed: ${it.message}", it) }
            .getOrDefault(false)
        WjxFileLogger.i(TAG, "helper VIN ensureCurrent=$helperReady")
        if (!helperReady) return null
        return runCatching { helperClient.readVehicleVinRaw() }
            .onFailure { WjxFileLogger.w(TAG, "helper VIN read failed: ${it.message}", it) }
            .getOrNull()
    }

    private fun writeIdentity(primaryDeviceCode: String, candidates: List<String>) {
        val aliases = candidates
            .map(::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .distinct()
        preferences.edit()
            .putString(KEY_DEVICE_CODE, primaryDeviceCode)
            .putString(KEY_DEVICE_CODE_ALIASES, aliases.joinToString(","))
            .apply()
    }

    private fun logRawDeviceCodeSource(source: String, raw: String) {
        WjxFileLogger.i(
            TAG,
            "deviceCode rawSource=$source rawLength=${raw.length} hasVin=${extractVin(raw) != null} " +
                "preview=${redactVin(raw).take(48).replace('\n', '|')}",
        )
    }

    private fun redactVin(value: String): String =
        VIN_TOKEN.replace(value) { match ->
            val vin = match.value.uppercase(Locale.US)
            if (vin.startsWith("L")) "${vin.take(3)}***********${vin.takeLast(3)}" else vin
        }

    private fun extractVinCandidates(raw: String): List<String> =
        VIN_TOKEN.findAll(raw)
            .map { it.value.uppercase(Locale.US) }
            .filter { it.startsWith("L") }
            .distinct()
            .toList()

    companion object {
        private const val TAG = "ActivationDeviceIdentity"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_DEVICE_CODE_ALIASES = "device_code_aliases"
        private const val VIN_SUCCESS_CACHE_MS = 60_000L
        private const val VIN_EMPTY_RETRY_DELAY_MS = 3_000L
        private val VIN_TOKEN = Regex("""[A-HJ-NPR-Z0-9]{17}""", RegexOption.IGNORE_CASE)

        internal fun extractVin(value: String?): String? {
            val token = value?.trim().orEmpty()
            if (token.length == 17 &&
                token.all(Char::isLetterOrDigit) &&
                token.startsWith("L", ignoreCase = true)
            ) {
                return token.uppercase(Locale.US)
            }
            return VIN_TOKEN.find(token)
                ?.value
                ?.uppercase(Locale.US)
                ?.takeIf { it.startsWith("L") }
        }

        internal fun normalizeDeviceCode(value: String): String =
            value.uppercase(Locale.US).filter { it.isLetterOrDigit() }
    }
}
