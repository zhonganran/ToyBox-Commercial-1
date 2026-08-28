package com.byd.wjx.activation

import android.content.Context
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.security.EndpointPrivacy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
    val checkoutMode: String,
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

@Singleton
class ActivationManager @Inject constructor(
    @ApplicationContext context: Context,
    okHttpClient: OkHttpClient,
    helperBootstrap: HelperBootstrap,
    helperClient: HelperClient,
) {
    private val appContext = context.applicationContext
    private val statusRefreshMutex = Mutex()
    private var statusRefreshInFlight: CompletableDeferred<ActivationSnapshot>? = null
    private val preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val entitlementStore by lazy { ActivationEntitlementStore(preferences) }
    private val deviceIdentity by lazy {
        ActivationDeviceIdentityProvider(preferences, helperBootstrap, helperClient)
    }
    private val apiClient by lazy {
        ActivationApiClient(
            context = appContext,
            okHttpClient = okHttpClient,
        )
    }

    fun ensureStarted() {
        entitlementStore.ensureStarted()
        WjxFileLogger.i(TAG, "trial ensureStarted")
    }

    suspend fun refreshIfNeeded(forceAfterMs: Long = STATUS_REFRESH_INTERVAL_MS): ActivationSnapshot =
        withContext(Dispatchers.IO) {
            val missingDevice = storedVinCode().isBlank() || storedDeviceAliases().isEmpty()
            if (entitlementStore.shouldRefresh(forceAfterMs, refreshForMissingDevice = missingDevice)) {
                refreshSnapshotSingleFlight()
            } else {
                currentSnapshot()
            }
        }

    fun currentSnapshot(): ActivationSnapshot {
        val deviceCode = storedVinCode()
        return entitlementStore.currentSnapshot(deviceCode, storedDeviceAliases())
    }

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
        entitlementStore.ensureStarted()
        val candidates = refreshDeviceCodeCandidates()
        val deviceCode = candidates.firstOrNull().orEmpty()
        WjxFileLogger.i(TAG, "refreshSnapshot candidates=${candidates.size} primaryLength=${deviceCode.length}")
        if (deviceCode.isNotBlank()) {
            runCatching { apiClient.requestStatus(deviceCode) }
                .onSuccess {
                    if (!applyServerStatus(it, deviceCode)) {
                        val backoff = entitlementStore.recordRefreshFailure()
                        WjxFileLogger.w(
                            TAG,
                            "status response rejected retryIn=${backoff.retryAfterMs}ms " +
                                "failure=${backoff.failureCount}",
                        )
                    }
                }
                .onFailure {
                    val backoff = entitlementStore.recordRefreshFailure()
                    WjxFileLogger.w(
                        TAG,
                        "status request failed: ${EndpointPrivacy.userMessage(it, "网络不可用")} " +
                            "retryIn=${backoff.retryAfterMs}ms failure=${backoff.failureCount}",
                    )
                }
        } else {
            WjxFileLogger.w(TAG, "refreshSnapshot no stable device code")
        }
        return entitlementStore.currentSnapshot(
            deviceCode.ifBlank(::storedVinCode),
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
        val response = runCatching {
            apiClient.requestActivation(deviceCode, code)
        }.getOrElse { error ->
            WjxFileLogger.w(TAG, "activate request failed: ${error.message}", error)
            return@withContext ActivationResult(
                success = false,
                message = "连接激活服务器失败：${EndpointPrivacy.userMessage(error, "网络不可用")}",
                snapshot = currentSnapshot(),
            )
        }

        val ok = response.optBoolean("success", false) && response.optBoolean("activated", false)
        val message = EndpointPrivacy.redact(
            response.optString("message", if (ok) "激活成功，已获得永久授权" else "激活失败"),
        )
        if (ok) {
            applyServerStatus(response, deviceCode, requestedActivationCode = code)
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
        apiClient.createPayment(deviceCode, normalizedType)
    }

    suspend fun checkPayment(order: ActivationPaymentOrder): ActivationPaymentStatus = withContext(Dispatchers.IO) {
        val candidates = storedDeviceAliases()
        val deviceCode = candidates.firstOrNull().orEmpty()
        require(deviceCode.isNotBlank()) { "车架号不可用，请刷新后重试" }
        val result = apiClient.checkPayment(deviceCode, order)
        if (result.paid && result.response.optBoolean("activated", false)) {
            applyServerStatus(result.response, deviceCode)
        }
        ActivationPaymentStatus(result.status, result.paid, result.message, currentSnapshot())
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

    private suspend fun refreshDeviceCodeCandidates(forceHardware: Boolean = false): List<String> =
        deviceIdentity.refreshCandidates(forceHardware)

    private fun applyServerStatus(
        json: JSONObject,
        deviceCode: String,
        requestedActivationCode: String = "",
    ): Boolean {
        val normalizedDevice = normalizeDeviceCode(deviceCode)
        val serverDeviceCode = normalizeDeviceCode(json.optString("deviceCode", ""))
        if (normalizedDevice.isBlank() || serverDeviceCode != normalizedDevice) {
            WjxFileLogger.w(TAG, "server status rejected: VIN mismatch")
            return false
        }
        val entitlement = entitlementStore.applyServerStatus(
            json = json,
            normalizedDevice = normalizedDevice,
            requestedActivationCode = requestedActivationCode,
        )
        deviceIdentity.rememberCandidates(listOf(normalizedDevice))
        WjxFileLogger.i(
            TAG,
            "server status applied activated=${entitlement.activated} " +
                "boundCode=${entitlement.boundActivationCode.isNotBlank()} " +
                "trialEndsAt=${entitlement.serverTrialEndsAt}",
        )
        return true
    }

    private fun storedVinCode(): String =
        deviceIdentity.storedVinCode()

    private fun storedDeviceAliases(extra: List<String> = emptyList()): List<String> {
        val primary = deviceIdentity.sanitizeCandidates(extra).firstOrNull() ?: storedVinCode()
        return listOf(normalizeDeviceCode(primary)).filter(String::isNotBlank)
    }

    private fun normalizeDeviceCode(value: String): String =
        ActivationDeviceIdentityProvider.normalizeDeviceCode(value)

    private fun normalizeActivationCode(value: String): String =
        value.uppercase(Locale.US).filter { it.isLetterOrDigit() || it == '-' }.trim()

    companion object {
        const val FREE_AUTOMATION_RULE_LIMIT = 3
        const val STATUS_REFRESH_INTERVAL_MS = 10L * 60L * 1000L
        private const val TAG = "ActivationManager"
        private const val PREF_NAME = "wjx_activation"
    }
}
