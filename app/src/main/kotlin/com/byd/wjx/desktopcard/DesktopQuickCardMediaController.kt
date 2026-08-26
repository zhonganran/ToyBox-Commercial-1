package com.byd.wjx.desktopcard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.vehicle.CarProfileDetector
import java.io.File

/** Owns desktop-card sound playback state and the playback broadcast lifecycle. */
class DesktopQuickCardMediaController(
    context: Context,
    private val repository: DesktopQuickCardRepository,
    private val vehicleStateRepository: VehicleStateRepository,
    private val carProfileDetector: CarProfileDetector,
    private val callbacks: Callbacks,
) {
    private val appContext = context.applicationContext
    private val exteriorSettings = ExteriorHailingSettings(appContext)
    private var receiverRegistered = false
    private var activeSoundPath: String? = null

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WjxOutsidePlayService.ACTION_PLAYBACK_STATE) return
            if (intent.getStringExtra(WjxOutsidePlayService.EXTRA_OWNER) != OWNER) return
            val path = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE_PATH).orEmpty()
            val playbackState = intent.getStringExtra(WjxOutsidePlayService.EXTRA_STATE)
            WjxFileLogger.i(TAG, "sound state=$playbackState path=$path")
            when (playbackState) {
                WjxOutsidePlayService.STATE_STARTED -> {
                    activeSoundPath = path
                    callbacks.onActiveSoundChanged(path)
                }

                WjxOutsidePlayService.STATE_STOPPED,
                WjxOutsidePlayService.STATE_COMPLETED,
                WjxOutsidePlayService.STATE_ERROR,
                -> if (activeSoundPath == path || path.isBlank()) {
                    activeSoundPath = null
                    callbacks.onActiveSoundChanged(null)
                }
            }
        }
    }

    fun start() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            playbackReceiver,
            IntentFilter(WjxOutsidePlayService.ACTION_PLAYBACK_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    fun reconcile(currentActivePath: String?, config: DesktopQuickCardConfig): Boolean {
        if (!shouldStopDesktopQuickCardSound(currentActivePath, config.soundPaths, config.enabled)) {
            return false
        }
        WjxFileLogger.i(TAG, "stopping removed/disabled sound path=$currentActivePath")
        WjxOutsidePlayService.stop(appContext, owner = OWNER)
        activeSoundPath = null
        callbacks.onActiveSoundChanged(null)
        return true
    }

    fun toggle(path: String, currentActivePath: String?): DesktopQuickCardSoundResult {
        val file = File(path)
        if (!file.isFile) {
            repository.refresh()
            WjxFileLogger.w(TAG, "sound missing path=$path")
            return DesktopQuickCardSoundResult.MISSING
        }
        val stopping = currentActivePath == path
        WjxFileLogger.i(TAG, "sound ${if (stopping) "stop" else "start"} path=$path")
        val data = vehicleStateRepository.fullSnapshot.value?.data
        WjxOutsidePlayService.toggle(
            context = appContext,
            file = file,
            channel = exteriorSettings.effectiveChannel(carProfileDetector.detect()),
            loop = false,
            owner = OWNER,
            reverseActive = data?.gear == REVERSE_GEAR,
        )
        return DesktopQuickCardSoundResult.REQUESTED
    }

    fun stop(currentActivePath: String?) {
        if (currentActivePath != null) {
            WjxOutsidePlayService.stop(appContext, owner = OWNER)
        }
        activeSoundPath = null
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(playbackReceiver) }
            receiverRegistered = false
        }
    }

    interface Callbacks {
        fun onActiveSoundChanged(path: String?)
    }

    companion object {
        private const val TAG = "DesktopQuickCardMedia"
        private const val OWNER = "desktop_quick_card"
        private const val REVERSE_GEAR = 2
    }
}

enum class DesktopQuickCardSoundResult {
    REQUESTED,
    MISSING,
}
