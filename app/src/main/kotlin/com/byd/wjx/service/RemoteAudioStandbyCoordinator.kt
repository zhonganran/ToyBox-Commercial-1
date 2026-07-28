package com.byd.wjx.service

import android.content.Context
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.data.repository.VehicleDataFreshness
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.helper.RemoteAccState
import com.byd.wjx.helper.parseRemoteAccState
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.vehicle.VehicleSystemProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class RemoteStandbyDecision(
    val enabled: Boolean,
    val reason: String,
)

internal fun decideRemoteStandby(
    parked: Boolean,
    socPercent: Int?,
    voltage12v: Double?,
    safetyBlocked: Boolean = false,
    authorized: Boolean = true,
): RemoteStandbyDecision = when {
    !authorized -> RemoteStandbyDecision(false, "authorization_inactive")
    safetyBlocked -> RemoteStandbyDecision(false, "safety_block_latched")
    socPercent != null && socPercent <= 10 -> RemoteStandbyDecision(false, "low_soc")
    voltage12v != null && voltage12v <= 11.6 -> RemoteStandbyDecision(false, "low_12v")
    parked -> RemoteStandbyDecision(true, "parked_remote_audio")
    else -> RemoteStandbyDecision(true, "armed_for_acc_off")
}

internal object RemoteAudioStandbyState {
    private const val PREFS = "wjx_remote_audio_standby"
    private const val KEY_PARKED = "parked"
    private const val KEY_HAS_POWER_STATE = "has_power_state"
    private const val KEY_SAFETY_BLOCKED = "safety_blocked"
    private const val KEY_POWER_EVENT_AT = "power_event_at"

    fun recordPowerEvent(context: Context, action: String?) {
        val parked = when (action) {
            "com.byd.action.ACC_OFF" -> true
            "com.byd.action.ACC_ON", "com.byd.action.IGN_ON" -> false
            else -> return
        }
        prefs(context).edit()
            .putBoolean(KEY_PARKED, parked)
            .putBoolean(KEY_HAS_POWER_STATE, true)
            .putLong(KEY_POWER_EVENT_AT, System.currentTimeMillis())
            .also { if (!parked) it.putBoolean(KEY_SAFETY_BLOCKED, false) }
            .apply()
    }

    fun recordPowerState(context: Context, parked: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_PARKED, parked)
            .putBoolean(KEY_HAS_POWER_STATE, true)
            .also { if (!parked) it.putBoolean(KEY_SAFETY_BLOCKED, false) }
            .apply()
    }

    fun parked(context: Context): Boolean? = prefs(context).let {
        if (it.getBoolean(KEY_HAS_POWER_STATE, false)) it.getBoolean(KEY_PARKED, false) else null
    }

    fun recentPowerEvent(context: Context, nowMs: Long): Boolean? = prefs(context).let {
        val eventAt = it.getLong(KEY_POWER_EVENT_AT, 0L)
        if (!it.getBoolean(KEY_HAS_POWER_STATE, false) || eventAt <= 0L || nowMs - eventAt > EVENT_PRIORITY_MS) {
            null
        } else {
            it.getBoolean(KEY_PARKED, false)
        }
    }

    fun safetyBlocked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SAFETY_BLOCKED, false)

    fun latchSafetyBlock(context: Context) {
        prefs(context).edit().putBoolean(KEY_SAFETY_BLOCKED, true).apply()
    }

    private fun prefs(context: Context) = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val EVENT_PRIORITY_MS = 2 * 60_000L
}

@Singleton
class RemoteAudioStandbyCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val activationManager: ActivationManager,
    private val helperClient: HelperClient,
    private val vehicleStateRepository: VehicleStateRepository,
) {
    private val appContext = context.applicationContext
    @Volatile private var lastRequestedEnabled: Boolean? = null
    @Volatile private var lastRequestAtMs = 0L

    suspend fun refresh(reason: String) {
        val state = vehicleStateRepository.state.value
        val freshData = state.data.takeIf { state.freshness == VehicleDataFreshness.FRESH }
        val now = System.currentTimeMillis()
        val eventParked = RemoteAudioStandbyState.recentPowerEvent(appContext, now)
        val propertyParked = when (
            parseRemoteAccState(VehicleSystemProperties.read(ACC_STATUS_PROPERTY))
        ) {
            RemoteAccState.ON -> false
            RemoteAccState.OFF -> true
            RemoteAccState.UNKNOWN -> null
        }
        val dataParked = freshData?.powerState?.let { it == 0 }
        val signalledParked = eventParked ?: propertyParked ?: dataParked
        if (eventParked == null && signalledParked != null) {
            RemoteAudioStandbyState.recordPowerState(appContext, signalledParked)
        }
        val parked = signalledParked ?: RemoteAudioStandbyState.parked(appContext) ?: false
        var safetyBlocked = RemoteAudioStandbyState.safetyBlocked(appContext)
        val lowSoc = freshData?.soc?.let { it <= 10 } == true
        val low12v = freshData?.voltage12v?.let { it <= 11.6 } == true
        if (parked && (lowSoc || low12v)) {
            RemoteAudioStandbyState.latchSafetyBlock(appContext)
            safetyBlocked = true
        }
        val decision = decideRemoteStandby(
            parked = parked,
            socPercent = freshData?.soc,
            voltage12v = freshData?.voltage12v,
            safetyBlocked = safetyBlocked,
            authorized = activationManager.currentSnapshot().let { it.activated || it.trialActive },
        )
        if (lastRequestedEnabled == decision.enabled && now - lastRequestAtMs < REASSERT_INTERVAL_MS) return

        val reply = helperClient.setRemoteStandbyPower(decision.enabled)
        lastRequestedEnabled = decision.enabled.takeIf { reply?.ok == true }
        lastRequestAtMs = now
        WjxFileLogger.i(
            TAG,
            "refresh source=$reason desired=${decision.enabled} decision=${decision.reason} " +
                "parked=$parked soc=${freshData?.soc} voltage12v=${freshData?.voltage12v} " +
                "replyOk=${reply?.ok} mcu=${reply?.mcuStatus} detail=${reply?.summary.orEmpty()}",
        )
    }

    private companion object {
        const val TAG = "RemoteAudioStandby"
        const val ACC_STATUS_PROPERTY = "sys.accanim.status"
        const val REASSERT_INTERVAL_MS = 5 * 60_000L
    }
}
