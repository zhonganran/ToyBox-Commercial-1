package com.byd.wjx.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoParkingSoundController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activationManager: ActivationManager,
    private val profileDetector: CarProfileDetector = CarProfileDetector(),
) {
    private val settings = AutoParkingSoundSettings(context)
    private val hailingSettings = ExteriorHailingSettings(context)
    private var wasActive: Boolean? = null
    @Volatile private var loopPlaybackStarted = false
    private var lastLoggedStatus: Int? = null
    private var missingStatusSinceMs: Long? = null
    private var lastPlayAttemptAtMs: Long = 0L
    private var lastMissingLogAtMs: Long = 0L
    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WjxOutsidePlayService.ACTION_PLAYBACK_STATE) return
            val owner = intent.getStringExtra(WjxOutsidePlayService.EXTRA_OWNER).orEmpty()
            if (owner != OWNER_AUTO_PARKING) return
            val state = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE).orEmpty()
            val path = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE_PATH).orEmpty()
            when (state) {
                WjxOutsidePlayService.STATE_STARTED -> {
                    loopPlaybackStarted = true
                }
                WjxOutsidePlayService.STATE_STOPPED,
                WjxOutsidePlayService.STATE_COMPLETED,
                WjxOutsidePlayService.STATE_ERROR -> {
                    loopPlaybackStarted = false
                    WjxFileLogger.i(TAG, "outside playback state=$state reset loop path=$path")
                }
            }
        }
    }

    init {
        validatedConfiguredSound("startup")
        registerPlaybackReceiver()
    }

    suspend fun onVehicleData(data: DiParsData) {
        val parkingStatus = data.adasApaParkingStatus
        if (parkingStatus != null && parkingStatus != lastLoggedStatus) {
            lastLoggedStatus = parkingStatus
            WjxFileLogger.i(
                TAG,
                "status changed adasApaParkingStatus=$parkingStatus label=${AutoParkingStatus.label(parkingStatus)} active=${AutoParkingStatus.isSoundActive(parkingStatus)}",
            )
        }
        if (!settings.enabled) {
            stopLoopIfNeeded("settings_disabled")
            parkingStatus?.let { wasActive = AutoParkingStatus.isSoundActive(it) }
            return
        }
        if (!autoParkingPremiumAllowed()) {
            stopLoopIfNeeded("activation_locked")
            parkingStatus?.let { wasActive = AutoParkingStatus.isSoundActive(it) }
            return
        }
        if (parkingStatus == null) {
            handleMissingStatus()
            maybeLogMissing()
            return
        }
        missingStatusSinceMs = null
        handleAutoPark(parkingStatus)
    }

    private suspend fun handleAutoPark(parkingStatus: Int) {
        val active = AutoParkingStatus.isSoundActive(parkingStatus)
        val label = AutoParkingStatus.label(parkingStatus)
        val previous = wasActive
        wasActive = active

        if (!active) {
            if (previous == true) stopLoopIfNeeded("parking_finished status=$parkingStatus label=$label")
            return
        }
        if (loopPlaybackStarted) return

        val now = System.currentTimeMillis()
        if (now - lastPlayAttemptAtMs < MIN_RETRY_GAP_MS) {
            WjxFileLogger.i(TAG, "skip: cooldown adasApaParkingStatus=$parkingStatus label=$label")
            return
        }
        lastPlayAttemptAtMs = now

        val file = validatedConfiguredSound("parking_trigger")
        if (file == null) {
            WjxFileLogger.w(TAG, "skip: vehicle sound unavailable adasApaParkingStatus=$parkingStatus label=$label")
            return
        }

        val profile = profileDetector.detect()
        val channel = hailingSettings.effectiveChannel(profile)
        WjxFileLogger.i(
            TAG,
            "trigger outside loop: adasApaParkingStatus=$parkingStatus label=$label file=${file.name} " +
                "controller=${profile.seriesLabel} raw=${profile.raw.ifBlank { "empty" }} " +
                "mode=${hailingSettings.channelMode} channel=$channel",
        )
        loopPlaybackStarted = true
        runCatching {
            WjxOutsidePlayService.play(
                context = context,
                file = file,
                channel = channel,
                loop = true,
                owner = OWNER_AUTO_PARKING,
            )
        }.onFailure {
            loopPlaybackStarted = false
            WjxFileLogger.w(TAG, "start outside loop failed: ${it.message}", it)
        }
    }

    private fun maybeLogMissing() {
        val now = System.currentTimeMillis()
        if (now - lastMissingLogAtMs >= MISSING_LOG_INTERVAL_MS) {
            lastMissingLogAtMs = now
            WjxFileLogger.w(TAG, "AdasApaParkingStatus unavailable from native data")
        }
    }

    private suspend fun handleMissingStatus() {
        if (wasActive != true && !loopPlaybackStarted) return
        val now = System.currentTimeMillis()
        val since = missingStatusSinceMs ?: now.also { missingStatusSinceMs = it }
        if (now - since >= MISSING_STATUS_STOP_TIMEOUT_MS) {
            wasActive = false
            missingStatusSinceMs = null
            stopLoopIfNeeded("parking_status_missing")
        }
    }

    private suspend fun stopLoopIfNeeded(reason: String) {
        if (!loopPlaybackStarted) return
        loopPlaybackStarted = false
        WjxFileLogger.i(TAG, "stop outside loop: $reason")
        runCatching { WjxOutsidePlayService.stop(context, owner = OWNER_AUTO_PARKING) }
            .onFailure { WjxFileLogger.w(TAG, "stop outside loop failed: ${it.message}", it) }
    }

    private suspend fun autoParkingPremiumAllowed(): Boolean {
        if (activationManager.canUse(PremiumFeature.AUTO_PARKING_SOUND)) return true
        val refreshed = runCatching {
            activationManager.refreshIfNeeded(forceAfterMs = ACTIVATION_RESTORE_REFRESH_MS)
        }.onFailure {
            WjxFileLogger.w(TAG, "activation restore refresh failed: ${it.message}", it)
        }.getOrNull()
        return refreshed?.let { it.activated || it.trialActive } == true
    }

    private fun validatedConfiguredSound(reason: String): File? {
        val path = settings.soundPath
        if (!SoundLibrary.isVehicleSoundPath(path)) {
            if (!settings.enabled && path.isBlank()) return null
            settings.enabled = false
            settings.soundPath = ""
            WjxFileLogger.w(TAG, "cleared invalid vehicle sound reason=$reason path=$path")
            return null
        }
        return File(path).takeIf { it.isFile }
    }

    private fun registerPlaybackReceiver() {
        runCatching {
            ContextCompat.registerReceiver(
                context,
                playbackReceiver,
                IntentFilter(WjxOutsidePlayService.ACTION_PLAYBACK_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure {
            WjxFileLogger.w(TAG, "register playback receiver failed: ${it.message}", it)
        }
    }

    private companion object {
        const val TAG = "AutoParkingSound"
        const val OWNER_AUTO_PARKING = "auto_parking"
        const val MIN_RETRY_GAP_MS = 1_000L
        const val MISSING_STATUS_STOP_TIMEOUT_MS = 5_000L
        const val MISSING_LOG_INTERVAL_MS = 30_000L
        const val ACTIVATION_RESTORE_REFRESH_MS = 30_000L
    }
}

internal object AutoParkingStatus {
    fun isSoundActive(status: Int): Boolean = status == PARKING_ACTIVE

    fun label(status: Int): String = when (status) {
        0 -> "OFF/未开启"
        1 -> "SEARCHING/搜索车位"
        2 -> "PARKING/泊车准备态"
        3 -> "PARKOUT_READY/待泊出或暂停"
        4 -> "PARKING_ACTIVE/泊车执行中"
        5 -> "PARKING_COMPLETE/完成"
        6 -> "PARKING_COMPLETE/完成"
        7 -> "SELECTING/选车位"
        else -> "UNKNOWN/$status"
    }

    private const val PARKING_ACTIVE = 4
}
