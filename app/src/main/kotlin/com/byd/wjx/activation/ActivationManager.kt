package com.byd.wjx.activation

import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.byd.wjx.BuildConfig
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.WjxDirectories
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class PremiumFeature {
    VEHICLE_EVENT_SOUNDS,
    AUTOMATION_UNLIMITED,
    CLUSTER_PROJECTION,
    KEY_SHORTCUT_ACTIONS,
    DESKTOP_LYRICS,
    LED_LINKAGE,
}

data class ActivationSnapshot(
    val deviceCode: String = "",
    val boundActivationCode: String = "",
    val activated: Boolean = false,
    val trialActive: Boolean = true,
    val trialExpired: Boolean = false,
    val firstStartedAt: Long = 0L,
    val trialEndsAt: Long = 0L,
    val remainingTrialMillis: Long = TRIAL_DURATION_MS,
    val activatedAt: Long = 0L,
    val lastCheckedAt: Long = 0L,
) {
    val statusLabel: String
        get() = when {
            activated -> "永久授权"
            deviceCode.isBlank() -> "未读取车架号"
            trialActive -> "试用中"
            else -> "试用已结束"
        }

    val remainingTrialDays: Int
        get() = if (activated || !trialActive) 0
        else ((remainingTrialMillis + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(1)

    companion object {
        const val TRIAL_DAYS = 7
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val TRIAL_DURATION_MS = TRIAL_DAYS * DAY_MS
    }
}

data class ActivationResult(
    val success: Boolean,
    val message: String,
    val snapshot: ActivationSnapshot,
)

data class ActivationPaymentOrder(
    val orderNo: String,
    val paymentType: String,
    val paymentName: String,
    val payType: String,
    val payInfo: String,
    val payUrl: String,
    val amount: String,
    val pollToken: String,
    val expiresAt: Long,
)

data class ActivationPaymentStatus(
    val status: String,
    val paid: Boolean,
    val message: String,
    val snapshot: ActivationSnapshot,
)

private data class OfflineLicenseMatch(
    val deviceCode: String,
)

@Singleton
class ActivationManager @Inject constructor(
    @ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
) {
    private val appContext = context.applicationContext
    private val vinReadMutex = Mutex()
    private val statusRefreshMutex = Mutex()
    private var statusRefreshInFlight: CompletableDeferred<ActivationSnapshot>? = null
    private var lastVinReadAt: Long = 0L
    private var lastVinReadCandidates: List<String> = emptyList()
    private val prefs
        get() = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun ensureStarted() {
        ensureTrialStarted(System.currentTimeMillis())
        WjxFileLogger.i(TAG, "trial ensureStarted")
    }

    suspend fun refreshIfNeeded(forceAfterMs: Long = STATUS_REFRESH_INTERVAL_MS): ActivationSnapshot =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val lastChecked = prefs.getLong(KEY_LAST_CHECKED_AT, 0L)
            val missingDevice = storedVinCode().isBlank() || storedDeviceAliases().isEmpty()
            val missingCredential = currentDeviceToken().isBlank() &&
                !prefs.getBoolean(KEY_DEVICE_CREDENTIAL_REQUIRES_ACTIVATION, false)
            if (missingDevice || missingCredential || forceAfterMs <= 0L || lastChecked <= 0L || now - lastChecked >= forceAfterMs) {
                refreshSnapshotSingleFlight()
            } else {
                currentSnapshot()
            }
        }

    fun currentSnapshot(): ActivationSnapshot {
        val now = System.currentTimeMillis()
        val firstStarted = ensureTrialStarted(now)
        val deviceCode = storedVinCode()
        return buildSnapshot(now, firstStarted, deviceCode, storedDeviceAliases())
    }

    fun currentDeviceToken(): String = prefs.getString(KEY_DEVICE_API_TOKEN, "").orEmpty()

    suspend fun refreshSnapshot(): ActivationSnapshot = withContext(Dispatchers.IO) {
        refreshSnapshotSingleFlight()
    }

    private suspend fun refreshSnapshotSingleFlight(): ActivationSnapshot {
        var ownsRefresh = false
        val inFlight = statusRefreshMutex.withLock {
            statusRefreshInFlight ?: CompletableDeferred<ActivationSnapshot>().also {
                statusRefreshInFlight = it
                ownsRefresh = true
            }
        }
        if (!ownsRefresh) return inFlight.await()

        return try {
            refreshSnapshotInternal().also(inFlight::complete)
        } catch (error: Throwable) {
            inFlight.completeExceptionally(error)
            throw error
        } finally {
            statusRefreshMutex.withLock {
                if (statusRefreshInFlight === inFlight) statusRefreshInFlight = null
            }
        }
    }

    private suspend fun refreshSnapshotInternal(): ActivationSnapshot {
        val now = System.currentTimeMillis()
        val firstStarted = ensureTrialStarted(now)
        val candidates = refreshDeviceCodeCandidates()
        val deviceCode = candidates.firstOrNull().orEmpty()
        WjxFileLogger.i(TAG, "refreshSnapshot candidates=${candidates.size} primaryLength=${deviceCode.length}")
        if (deviceCode.isNotBlank()) {
            runCatching { requestStatus(deviceCode, candidates) }
                .onSuccess { applyServerStatus(it, deviceCode, candidates) }
                .onFailure {
                    WjxFileLogger.w(TAG, "status request failed: ${it.message}", it)
                    restoreOfflineLicense(candidates, "status_request_failed")
                }
        } else {
            WjxFileLogger.w(TAG, "refreshSnapshot no stable device code")
        }
        return buildSnapshot(
            System.currentTimeMillis(),
            firstStarted,
            deviceCode,
            storedDeviceAliases(candidates),
        )
    }

    suspend fun activate(rawCode: String): ActivationResult = withContext(Dispatchers.IO) {
        val code = normalizeActivationCode(rawCode)
        if (code.isBlank()) {
            WjxFileLogger.w(TAG, "activate blocked: empty activation code")
            return@withContext ActivationResult(false, "请输入激活码", refreshSnapshot())
        }

        val candidates = refreshDeviceCodeCandidates(forceHardware = true)
        val deviceCode = candidates.firstOrNull().orEmpty()
        if (candidates.none { normalizeDeviceCode(it).isNotBlank() }) {
            WjxFileLogger.w(TAG, "activate blocked: no vehicle vin")
            return@withContext ActivationResult(false, "未读取到稳定车架号，请启动车辆并连接车机底层后刷新", currentSnapshot())
        }

        WjxFileLogger.i(TAG, "activate request deviceLength=${deviceCode.length} candidates=${candidates.size} codeLength=${code.length}")
        val response = runCatching { requestActivate(deviceCode, candidates, code) }.getOrElse { error ->
            WjxFileLogger.w(TAG, "activate request failed: ${error.message}", error)
            return@withContext ActivationResult(
                success = false,
                message = "连接激活服务器失败：${error.message ?: "网络不可用"}",
                snapshot = currentSnapshot(),
            )
        }

        val ok = response.optBoolean("success", false) && response.optBoolean("activated", false)
        val message = response.optString("message", if (ok) "激活成功，已获得永久授权" else "激活失败")
        if (ok) {
            applyServerStatus(response, deviceCode, candidates, requestedActivationCode = code)
        }
        WjxFileLogger.i(TAG, "activate result success=$ok message=$message")
        ActivationResult(ok, message, currentSnapshot())
    }

    suspend fun createPayment(paymentType: String): ActivationPaymentOrder = withContext(Dispatchers.IO) {
        val normalizedType = paymentType.trim().lowercase(Locale.US)
        require(normalizedType == "alipay" || normalizedType == "wxpay") { "不支持的支付方式" }
        val current = refreshSnapshot()
        require(!current.activated) { "当前车辆已经永久激活" }
        val candidates = refreshDeviceCodeCandidates(forceHardware = true)
        val deviceCode = candidates.firstOrNull().orEmpty()
        require(deviceCode.isNotBlank()) { "未读取到稳定车架号，请启动车辆并刷新后重试" }
        require(currentDeviceToken().isNotBlank()) { "设备身份尚未建立，请刷新授权状态后重试" }
        val response = postJson(
            "payment/create",
            basePayload(deviceCode, candidates).apply {
                put("paymentType", normalizedType)
            },
        )
        val order = ActivationPaymentOrder(
            orderNo = response.optString("orderNo"),
            paymentType = response.optString("paymentType", normalizedType),
            paymentName = response.optString(
                "paymentName",
                if (normalizedType == "alipay") "支付宝" else "微信支付",
            ),
            payType = response.optString("payType"),
            payInfo = response.optString("payInfo"),
            payUrl = response.optString("payUrl", response.optString("payInfo")),
            amount = response.optString("amount", "15.90"),
            pollToken = response.optString("pollToken"),
            expiresAt = jsonMillis(response, "expiresAt"),
        )
        require(order.orderNo.isNotBlank() && order.pollToken.isNotBlank() && order.payUrl.isNotBlank()) {
            "支付服务器返回的订单信息不完整"
        }
        WjxFileLogger.i(TAG, "payment order created type=$normalizedType payType=${order.payType}")
        order
    }

    suspend fun checkPayment(order: ActivationPaymentOrder): ActivationPaymentStatus = withContext(Dispatchers.IO) {
        val candidates = storedDeviceAliases()
        val deviceCode = candidates.firstOrNull().orEmpty()
        require(deviceCode.isNotBlank()) { "车架号不可用，请刷新后重试" }
        val response = postJson(
            "payment/status",
            basePayload(deviceCode, candidates).apply {
                put("orderNo", order.orderNo)
                put("pollToken", order.pollToken)
            },
        )
        val status = response.optString("status", "pending")
        val paid = response.optBoolean("paid", false)
        if (paid && response.optBoolean("activated", false)) {
            applyServerStatus(response, deviceCode, candidates)
        }
        val message = response.optString(
            "message",
            when (status) {
                "paid" -> "支付成功，永久授权已自动激活"
                "expired" -> "支付二维码已过期，请重新创建订单"
                "failed" -> "支付订单失败，请重新尝试"
                else -> "等待扫码支付"
            },
        )
        WjxFileLogger.i(TAG, "payment status status=$status paid=$paid")
        ActivationPaymentStatus(status, paid, message, currentSnapshot())
    }

    fun canUse(feature: PremiumFeature): Boolean {
        val snapshot = currentSnapshot()
        return when (feature) {
            PremiumFeature.VEHICLE_EVENT_SOUNDS,
            PremiumFeature.AUTOMATION_UNLIMITED,
            PremiumFeature.CLUSTER_PROJECTION,
            PremiumFeature.KEY_SHORTCUT_ACTIONS,
            PremiumFeature.DESKTOP_LYRICS,
            PremiumFeature.LED_LINKAGE -> snapshot.activated || snapshot.trialActive
        }
    }

    fun canCreateAutomation(currentRuleCount: Int): Boolean {
        return canUse(PremiumFeature.AUTOMATION_UNLIMITED) ||
            currentRuleCount < FREE_AUTOMATION_RULE_LIMIT
    }

    private fun ensureTrialStarted(now: Long): Long {
        val existing = prefs.getLong(KEY_FIRST_STARTED_AT, 0L)
        if (existing > 0L) return existing
        prefs.edit().putLong(KEY_FIRST_STARTED_AT, now).apply()
        WjxFileLogger.i(TAG, "trial started at=$now")
        return now
    }

    private fun buildSnapshot(
        now: Long,
        firstStarted: Long,
        latestDeviceCode: String,
        latestDeviceAliases: List<String>,
    ): ActivationSnapshot {
        val serverTrialEndsAt = prefs.getLong(KEY_SERVER_TRIAL_ENDS_AT, 0L)
        val trialEndsAt = if (serverTrialEndsAt > 0L) {
            serverTrialEndsAt
        } else {
            firstStarted + ActivationSnapshot.TRIAL_DURATION_MS
        }
        val remaining = (trialEndsAt - now).coerceAtLeast(0L)
        val rawTrialActive = remaining > 0L
        val deviceCode = latestDeviceCode.ifBlank {
            storedVinCode()
        }
        val normalizedDevice = normalizeDeviceCode(deviceCode)
        val normalizedAliases = (latestDeviceAliases + deviceCode)
            .map(::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .distinct()
        val hasStableDevice = normalizedAliases.isNotEmpty()
        val activatedDevice = prefs.getString(KEY_ACTIVATED_DEVICE_CODE, "").orEmpty()
        val lastCheckedAt = prefs.getLong(KEY_LAST_CHECKED_AT, 0L)
        val activated = prefs.getBoolean(KEY_ACTIVATED, false) &&
            hasStableDevice &&
            activatedDevice in normalizedAliases

        return ActivationSnapshot(
            deviceCode = deviceCode,
            boundActivationCode = prefs.getString(KEY_BOUND_ACTIVATION_CODE, "").orEmpty(),
            activated = activated,
            trialActive = !activated && hasStableDevice && rawTrialActive,
            trialExpired = !activated && (!hasStableDevice || !rawTrialActive),
            firstStartedAt = firstStarted,
            trialEndsAt = trialEndsAt,
            remainingTrialMillis = remaining,
            activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L),
            lastCheckedAt = lastCheckedAt,
        )
    }

    private suspend fun refreshDeviceCodeCandidates(forceHardware: Boolean = false): List<String> {
        val latest = readDeviceCodeCandidates(forceHardware)
        val candidates = sanitizeDeviceCandidates(latest)
        if (candidates.isNotEmpty()) {
            saveDeviceCandidates(candidates)
        }
        return candidates
    }

    private suspend fun readDeviceCodeCandidates(forceHardware: Boolean = false): List<String> = vinReadMutex.withLock {
        val now = System.currentTimeMillis()
        val cacheWindow = if (lastVinReadCandidates.isNotEmpty()) {
            VIN_SUCCESS_CACHE_MS
        } else {
            VIN_EMPTY_RETRY_DELAY_MS
        }
        if (!forceHardware && lastVinReadAt > 0L && now - lastVinReadAt in 0 until cacheWindow) {
            WjxFileLogger.i(
                TAG,
                "VIN candidates reused from short cache valid=${lastVinReadCandidates.size} age=${now - lastVinReadAt}ms"
            )
            return@withLock lastVinReadCandidates
        }

        val candidates = mutableListOf<String>()
        readHelperVinRaw()?.let { raw ->
            logRawDeviceCodeSource("helper_bodywork", raw)
            candidates += extractVinCandidates(raw)
        }
        sanitizeDeviceCandidates(candidates).takeIf { it.isNotEmpty() }?.let {
            WjxFileLogger.i(TAG, "VIN candidates resolved by helper valid=${it.size}")
            lastVinReadAt = now
            lastVinReadCandidates = it
            return@withLock it
        }

        val sanitized = emptyList<String>()
        lastVinReadAt = now
        lastVinReadCandidates = sanitized
        WjxFileLogger.i(TAG, "VIN candidates resolved by helper valid=0; no fallback source used")
        return@withLock sanitized
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

    private fun extractVin(value: String?): String? {
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

    private fun logRawDeviceCodeSource(source: String, raw: String) {
        WjxFileLogger.i(
            TAG,
            "deviceCode rawSource=$source rawLength=${raw.length} hasVin=${extractVin(raw) != null} " +
                "preview=${redactVin(raw).take(48).replace('\n', '|')}"
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

    private fun requestStatus(deviceCode: String, deviceCandidates: List<String>): JSONObject {
        val body = basePayload(deviceCode, deviceCandidates)
        return postJson("status", body)
    }

    private fun requestActivate(deviceCode: String, deviceCandidates: List<String>, activationCode: String): JSONObject {
        val body = basePayload(deviceCode, deviceCandidates).apply {
            put("activationCode", activationCode)
        }
        return postJson("activate", body)
    }

    private fun basePayload(deviceCode: String, deviceCandidates: List<String>): JSONObject = JSONObject().apply {
        val candidates = sanitizeDeviceCandidates(deviceCandidates + deviceCode)
        put("deviceCode", normalizeDeviceCode(deviceCode))
        put("rawDeviceCode", deviceCode)
        put("deviceCodes", JSONArray().apply {
            candidates.forEach { put(normalizeDeviceCode(it)) }
        })
        put("rawDeviceCodes", JSONArray().apply {
            candidates.forEach { put(it) }
        })
        put("appVersion", BuildConfig.VERSION_NAME)
        put("packageName", appContext.packageName)
        put("credentialVersion", DEVICE_CREDENTIAL_VERSION)
        credentialRecoveryProof().takeIf(String::isNotBlank)?.let {
            put("credentialRecoveryProof", it)
        }
        readOfflineLicense()?.let { put("deviceProof", it) }
    }

    private fun credentialRecoveryProof(): String {
        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty().trim()
        if (androidId.isBlank()) return ""
        val source = "wjx-device-credential-v1|${appContext.packageName}|$androidId"
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun postJson(path: String, payload: JSONObject): JSONObject {
        val apiBase = BuildConfig.ACTIVATION_API_URL.trim().trimEnd('/')
        val requestBuilder = Request.Builder()
            .url("$apiBase/$path")
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
        currentDeviceToken().takeIf(String::isNotBlank)?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }
        val request = requestBuilder.build()
        WjxFileLogger.i(TAG, "activation api request path=$path url=$apiBase/$path deviceCodes=${payload.optJSONArray("deviceCodes")?.length() ?: 0}")
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val message = json.optString("message", "HTTP ${response.code}")
                WjxFileLogger.w(TAG, "activation api failed path=$path code=${response.code} message=$message")
                throw IllegalStateException(message)
            }
            WjxFileLogger.i(TAG, "activation api success path=$path code=${response.code} activated=${json.optBoolean("activated", false)}")
            return json
        }
    }

    private fun applyServerStatus(
        json: JSONObject,
        deviceCode: String,
        deviceCandidates: List<String>,
        requestedActivationCode: String = "",
    ) {
        val serverDeviceCode = json.optString("deviceCode", "")
        val candidates = sanitizeDeviceCandidates(deviceCandidates + deviceCode + serverDeviceCode)
        val normalizedDevice = candidates.firstOrNull()?.let(::normalizeDeviceCode).orEmpty()
        val activated = json.optBoolean("activated", false) && normalizedDevice.isNotBlank()
        val now = System.currentTimeMillis()
        val serverTrialEndsAt = jsonMillis(json, "trialEndsAt")
        val activatedAt = jsonMillis(json, "activatedAt").takeIf { it > 0L }
            ?: if (activated) now else 0L
        val aliases = candidates
            .map(::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .distinct()
        val existingBoundCode = prefs.getString(KEY_BOUND_ACTIVATION_CODE, "").orEmpty()
        val boundActivationCode = if (activated) {
            extractBoundActivationCode(json, requestedActivationCode).ifBlank { existingBoundCode }
        } else {
            ""
        }
        val deviceToken = json.optString("deviceToken", "").trim()
        val editor = prefs.edit()
            .putString(KEY_DEVICE_CODE, candidates.firstOrNull().orEmpty())
            .putString(KEY_DEVICE_CODE_ALIASES, aliases.joinToString(","))
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
        }
        editor.apply()
        if (activated) {
            persistOfflineLicense(json.optJSONObject(KEY_OFFLINE_LICENSE), aliases, normalizedDevice)
        } else {
            clearOfflineLicenseCache("server_not_activated")
        }
        WjxFileLogger.i(
            TAG,
            "server status applied activated=$activated aliases=${aliases.size} " +
                "boundCode=${boundActivationCode.isNotBlank()} credential=${deviceToken.isNotBlank()} " +
                "trialEndsAt=$serverTrialEndsAt",
        )
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

    private fun persistOfflineLicense(
        license: JSONObject?,
        normalizedAliases: List<String>,
        normalizedDevice: String,
    ) {
        if (license == null) {
            WjxFileLogger.w(TAG, "offline license missing from server response")
            return
        }
        val aliases = (normalizedAliases + normalizedDevice)
            .map(::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .distinct()
        if (verifyOfflineLicense(license, aliases) == null) {
            WjxFileLogger.w(TAG, "offline license rejected while persisting")
            return
        }
        val normalized = JSONObject(license.toString()).toString()
        runCatching {
            prefs.edit().putString(KEY_OFFLINE_LICENSE_JSON, normalized).apply()
            offlineLicensePrivateFile().writeText(normalized)
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

    private fun restoreOfflineLicense(deviceCandidates: List<String>, reason: String): Boolean {
        val aliases = (deviceCandidates + storedVinCode())
            .map(::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .distinct()
        if (aliases.isEmpty()) {
            WjxFileLogger.w(TAG, "offline license restore skipped: no VIN reason=$reason")
            return false
        }
        val license = readOfflineLicense() ?: run {
            WjxFileLogger.w(TAG, "offline license restore skipped: no cache reason=$reason")
            return false
        }
        val match = verifyOfflineLicense(license, aliases) ?: run {
            WjxFileLogger.w(TAG, "offline license restore rejected reason=$reason")
            return false
        }
        val now = System.currentTimeMillis()
        val tokenAliases = offlineLicenseDeviceCodes(license)
        val mergedAliases = (aliases + tokenAliases)
            .map(::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .distinct()
        prefs.edit()
            .putString(KEY_DEVICE_CODE, match.deviceCode)
            .putString(KEY_DEVICE_CODE_ALIASES, mergedAliases.joinToString(","))
            .putBoolean(KEY_ACTIVATED, true)
            .putString(KEY_ACTIVATED_DEVICE_CODE, match.deviceCode)
            .putString(KEY_BOUND_ACTIVATION_CODE, license.optString("licenseCodeMasked", ""))
            .putLong(KEY_ACTIVATED_AT, license.optLong("activatedAt", now))
            .putLong(KEY_LAST_CHECKED_AT, now)
            .putString(KEY_OFFLINE_LICENSE_JSON, license.toString())
            .apply()
        WjxFileLogger.i(TAG, "offline license restored reason=$reason aliases=${mergedAliases.size}")
        return true
    }

    private fun readOfflineLicense(): JSONObject? {
        val raw = listOf(
            prefs.getString(KEY_OFFLINE_LICENSE_JSON, null),
            runCatching { offlineLicensePrivateFile().takeIf(File::isFile)?.readText() }.getOrNull(),
            runCatching { WjxDirectories.offlineLicense.takeIf(File::isFile)?.readText() }.getOrNull(),
        ).firstOrNull { !it.isNullOrBlank() } ?: return null
        return runCatching { JSONObject(raw) }
            .onFailure { WjxFileLogger.w(TAG, "offline license parse failed: ${it.message}", it) }
            .getOrNull()
    }

    private fun verifyOfflineLicense(license: JSONObject, normalizedAliases: List<String>): OfflineLicenseMatch? {
        if (license.optInt("version", 0) != 1) return null
        if (!license.optBoolean("activated", false)) return null
        val packageName = license.optString("packageName", "")
        if (packageName.isNotBlank() && packageName != appContext.packageName) return null
        val expiresAt = license.optLong("expiresAt", 0L)
        if (expiresAt > 0L && expiresAt <= System.currentTimeMillis()) return null
        val signature = license.optString("signature", "")
        if (!verifyOfflineLicenseSignature(license, signature)) return null
        val tokenCodes = offlineLicenseDeviceCodes(license)
        val aliasSet = normalizedAliases.map(::normalizeDeviceCode).filter(String::isNotBlank).toSet()
        val matched = tokenCodes.firstOrNull { it in aliasSet } ?: return null
        return OfflineLicenseMatch(matched)
    }

    private fun verifyOfflineLicenseSignature(license: JSONObject, signature: String): Boolean {
        if (signature.isBlank()) return false
        return when (license.optString("signatureAlg", "")) {
            "RS256" -> verifyOfflineLicenseRs256(license, signature)
            else -> false
        }
    }

    private fun verifyOfflineLicenseRs256(license: JSONObject, signature: String): Boolean =
        runCatching {
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(decodeBase64UrlOrStandard(OFFLINE_LICENSE_PUBLIC_KEY_DER)))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(offlineLicenseCanonical(license, includeSignatureAlg = true).toByteArray(Charsets.UTF_8))
            verifier.verify(decodeBase64UrlOrStandard(signature))
        }.onFailure {
            WjxFileLogger.w(TAG, "offline license RS256 verify failed: ${it.message}", it)
        }.getOrDefault(false)

    private fun offlineLicenseCanonical(license: JSONObject, includeSignatureAlg: Boolean): String {
        val deviceCodes = offlineLicenseDeviceCodes(license).sorted().joinToString(",")
        val features = license.optJSONArray("features")
            ?.toStringList()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.sorted()
            ?.joinToString(",")
            .orEmpty()
        val fields = mutableListOf(
            "version=${license.optInt("version", 1)}",
        )
        if (includeSignatureAlg) {
            fields += "signatureAlg=${license.optString("signatureAlg", "RS256")}"
        }
        fields += listOf(
            "deviceCodes=$deviceCodes",
            "activated=${if (license.optBoolean("activated", false)) "1" else "0"}",
            "activatedAt=${license.optLong("activatedAt", 0L)}",
            "issuedAt=${license.optLong("issuedAt", 0L)}",
            "expiresAt=${license.optLong("expiresAt", 0L)}",
            "packageName=${license.optString("packageName", "")}",
            "licenseCodeMasked=${license.optString("licenseCodeMasked", "")}",
            "features=$features",
        )
        return fields.joinToString("\n")
    }

    private fun offlineLicenseDeviceCodes(license: JSONObject): List<String> =
        license.optJSONArray("deviceCodes")
            ?.toStringList()
            ?.map(::normalizeDeviceCode)
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { index -> optString(index, "").takeIf(String::isNotBlank) }

    private fun decodeBase64UrlOrStandard(value: String): ByteArray {
        val compact = value.trim().replace("\n", "")
        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        val flags = if ('-' in compact || '_' in compact) {
            Base64.URL_SAFE or Base64.NO_WRAP
        } else {
            Base64.DEFAULT
        }
        return Base64.decode(padded, flags)
    }

    private fun clearOfflineLicenseCache(reason: String) {
        prefs.edit().remove(KEY_OFFLINE_LICENSE_JSON).apply()
        runCatching { offlineLicensePrivateFile().delete() }
        runCatching { WjxDirectories.offlineLicense.delete() }
        WjxFileLogger.i(TAG, "offline license cache cleared reason=$reason")
    }

    private fun offlineLicensePrivateFile(): File =
        File(appContext.filesDir, OFFLINE_LICENSE_FILE_NAME)

    private fun sanitizeDeviceCandidates(values: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        val result = mutableListOf<String>()
        values.forEach { raw ->
            val value = extractVin(raw).orEmpty()
            val normalized = normalizeDeviceCode(value)
            if (normalized.isNotBlank() && seen.add(normalized)) {
                result += value
            }
        }
        return result
    }

    private fun saveDeviceCandidates(candidates: List<String>) {
        if (candidates.isEmpty()) return
        prefs.edit()
            .putString(KEY_DEVICE_CODE, candidates.first())
            .putString(
                KEY_DEVICE_CODE_ALIASES,
                candidates.map(::normalizeDeviceCode).filter(String::isNotBlank).distinct().joinToString(",")
            )
            .apply()
    }

    private fun storedVinCode(): String =
        extractVin(prefs.getString(KEY_DEVICE_CODE, "").orEmpty()).orEmpty()

    private fun storedDeviceAliases(extra: List<String> = emptyList()): List<String> =
        (prefs.getString(KEY_DEVICE_CODE_ALIASES, "").orEmpty().split(",") + extra + storedVinCode())
            .mapNotNull(::extractVin)
            .map(::normalizeDeviceCode)
            .filter(String::isNotBlank)
            .distinct()

    private fun jsonMillis(json: JSONObject, key: String): Long {
        if (!json.has(key) || json.isNull(key)) return 0L
        val numeric = json.optLong(key, 0L)
        if (numeric > 0L) return numeric
        val text = json.optString(key, "")
        if (text.isBlank()) return 0L
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
    }

    private fun normalizeDeviceCode(value: String): String =
        value.uppercase(Locale.US).filter { it.isLetterOrDigit() }

    private fun normalizeActivationCode(value: String): String =
        value.uppercase(Locale.US).filter { it.isLetterOrDigit() || it == '-' }.trim()

    private fun maskActivationCode(value: String): String {
        val code = value.trim().uppercase(Locale.US)
        if (code.isBlank()) return ""
        if ('*' in code || '•' in code) return code
        val compact = code.filter(Char::isLetterOrDigit)
        if (compact.length <= 8) return compact
        return "${compact.take(4)}-****-****-${compact.takeLast(4)}"
    }

    companion object {
        const val FREE_AUTOMATION_RULE_LIMIT = 3
        const val STATUS_REFRESH_INTERVAL_MS = 10L * 60L * 1000L
        private const val TAG = "ActivationManager"
        private const val PREF_NAME = "wjx_activation"
        private const val KEY_FIRST_STARTED_AT = "first_started_at"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_DEVICE_CODE_ALIASES = "device_code_aliases"
        private const val KEY_ACTIVATED = "activated"
        private const val KEY_ACTIVATED_DEVICE_CODE = "activated_device_code"
        private const val KEY_BOUND_ACTIVATION_CODE = "bound_activation_code"
        private const val KEY_ACTIVATED_AT = "activated_at"
        private const val KEY_SERVER_TRIAL_ENDS_AT = "server_trial_ends_at"
        private const val KEY_LAST_CHECKED_AT = "last_checked_at"
        private const val KEY_DEVICE_API_TOKEN = "device_api_token"
        private const val KEY_DEVICE_CREDENTIAL_REQUIRES_ACTIVATION =
            "device_credential_requires_activation"
        private const val KEY_OFFLINE_LICENSE_JSON = "offline_license_json"
        private const val KEY_OFFLINE_LICENSE = "offlineLicense"
        private const val OFFLINE_LICENSE_FILE_NAME = "wjx_license.dat"
        private const val DEVICE_CREDENTIAL_VERSION = 1
        private const val OFFLINE_LICENSE_PUBLIC_KEY_DER =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxkUw/ZPkswbCgkV60bKska+/srSc216njAlFXq6eC5ws57doamyRvJvniWxv0aBf2vmgvTiKzdKCvZ2WmyIlTzl2W7GTvgeuJWcLURW9Np451/O/fcrTrI1bOB2FI2JhFnH2ZxwUS4yGU7kb+m+l6JgfAtaD/sBb4s++Ejz31+Kh5/ZLTmMMtSWi01B80NfR5gBriNhGR3ZL+l7kzp9zxA3XW89Jz2jkjedytZGyufgr8WLWxtEDpW2nanWA4Tqwu91A6iV95YrnUUEjh5EH54SA9X9HFAdAfFYWcfGxWkxD0lpQC9uTTCf+0lBTFt4EbJ6RYsiQHl0eXNLV48LGNQIDAQAB"
        private const val VIN_SUCCESS_CACHE_MS = 60_000L
        private const val VIN_EMPTY_RETRY_DELAY_MS = 3_000L
        private val VIN_TOKEN = Regex("""[A-HJ-NPR-Z0-9]{17}""", RegexOption.IGNORE_CASE)
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
