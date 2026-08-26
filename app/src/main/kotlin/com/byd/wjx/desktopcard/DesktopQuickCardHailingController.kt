package com.byd.wjx.desktopcard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.audio.RealtimeHailingPhase
import com.byd.wjx.audio.RealtimeHailingStateContract
import com.byd.wjx.audio.WjxOutsidePlayService
import com.byd.wjx.audio.WjxRealtimeHailingService
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Owns realtime-hailing permission checks, service commands, broadcasts, and cleanup. */
class DesktopQuickCardHailingController @Inject constructor(
    @ApplicationContext context: Context,
    private val carProfileDetector: CarProfileDetector,
) {
    private val appContext = context.applicationContext
    private val exteriorSettings = ExteriorHailingSettings(appContext)
    private var callbacks: Callbacks? = null
    private var receiverRegistered = false
    private var startedByCard = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = RealtimeHailingStateContract.fromIntent(intent) ?: return
            WjxFileLogger.i(TAG, "hailing phase=${state.phase} message=${state.message}")
            when (state.phase) {
                RealtimeHailingPhase.Starting -> callbacks?.onStateChanged(
                    DesktopQuickCardHailingState(running = false, busy = true, clearActiveSound = true),
                )
                RealtimeHailingPhase.Running -> callbacks?.onStateChanged(
                    DesktopQuickCardHailingState(running = true, busy = false, clearActiveSound = true),
                )
                RealtimeHailingPhase.Stopping -> callbacks?.onStateChanged(
                    DesktopQuickCardHailingState(running = false, busy = true),
                )
                RealtimeHailingPhase.Idle,
                RealtimeHailingPhase.Error -> {
                    startedByCard = false
                    callbacks?.onStateChanged(DesktopQuickCardHailingState(running = false, busy = false))
                    if (state.phase == RealtimeHailingPhase.Error) {
                        callbacks?.onError(state.message.ifBlank { "实时喊话启动失败" })
                    }
                }
            }
        }
    }

    fun start(callbacks: Callbacks): DesktopQuickCardHailingState {
        this.callbacks = callbacks
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(RealtimeHailingStateContract.ACTION_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        val state = WjxRealtimeHailingService.state()
        return DesktopQuickCardHailingState(running = state.isRunning, busy = state.isBusy)
    }

    fun toggle(current: DesktopQuickCardHailingState) {
        if (current.busy) return
        if (current.running || WjxRealtimeHailingService.state().isActive) {
            WjxFileLogger.i(TAG, "realtime hailing stop requested")
            callbacks?.onStateChanged(current.copy(busy = true))
            requestStop(notifyIfIdle = true, showFailure = true)
            return
        }
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            WjxFileLogger.w(TAG, "realtime hailing blocked: RECORD_AUDIO denied")
            callbacks?.onError("请先在应用设置中允许麦克风权限")
            return
        }

        val channel = exteriorSettings.effectiveChannel(carProfileDetector.detect())
        WjxFileLogger.i(TAG, "realtime hailing start requested channel=$channel")
        WjxOutsidePlayService.stop(appContext)
        callbacks?.onStateChanged(
            DesktopQuickCardHailingState(running = false, busy = true, clearActiveSound = true),
        )
        startedByCard = true
        runCatching { WjxRealtimeHailingService.start(appContext, channel) }
            .onSuccess { requested ->
                startedByCard = requested
                val state = WjxRealtimeHailingService.state()
                callbacks?.onStateChanged(
                    DesktopQuickCardHailingState(
                        running = state.isRunning,
                        busy = state.isBusy,
                        clearActiveSound = true,
                    ),
                )
            }
            .onFailure { error ->
                startedByCard = false
                callbacks?.onStateChanged(DesktopQuickCardHailingState(running = false, busy = false))
                WjxFileLogger.e(TAG, "realtime hailing start failed: ${error.message}", error)
                callbacks?.onError("实时喊话启动失败")
            }
    }

    fun reconcile(config: DesktopQuickCardConfig) {
        if (!shouldStopDesktopQuickCardHailing(startedByCard, config)) return
        WjxFileLogger.i(TAG, "stopping removed/disabled realtime hailing")
        val state = WjxRealtimeHailingService.state()
        callbacks?.onStateChanged(
            DesktopQuickCardHailingState(
                running = state.isRunning,
                busy = state.isBusy,
            ),
        )
        if (requestStop(notifyIfIdle = true, showFailure = true)) startedByCard = false
    }

    fun stop() {
        if (startedByCard && WjxRealtimeHailingService.state().isActive) {
            runCatching { WjxRealtimeHailingService.stop(appContext, notifyIfIdle = false) }
                .onFailure { error ->
                    WjxFileLogger.e(TAG, "realtime hailing cleanup failed: ${error.message}", error)
                }
        }
        startedByCard = false
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        callbacks = null
    }

    private fun requestStop(notifyIfIdle: Boolean, showFailure: Boolean): Boolean {
        return runCatching {
            WjxRealtimeHailingService.stop(appContext, notifyIfIdle)
        }.fold(
            onSuccess = { requested ->
                val state = WjxRealtimeHailingService.state()
                if (!requested) {
                    callbacks?.onStateChanged(
                        DesktopQuickCardHailingState(
                            running = state.isRunning,
                            busy = state.isBusy,
                        ),
                    )
                }
                requested || !state.isActive
            },
            onFailure = { error ->
                val state = WjxRealtimeHailingService.state()
                callbacks?.onStateChanged(
                    DesktopQuickCardHailingState(
                        running = state.isRunning,
                        busy = state.isBusy,
                    ),
                )
                WjxFileLogger.e(TAG, "realtime hailing stop failed: ${error.message}", error)
                if (showFailure) callbacks?.onError("实时喊话停止失败，请重试")
                false
            },
        )
    }

    interface Callbacks {
        fun onStateChanged(state: DesktopQuickCardHailingState)
        fun onError(message: String)
    }

    private companion object {
        const val TAG = "DesktopQuickCardHailing"
    }
}

data class DesktopQuickCardHailingState(
    val running: Boolean,
    val busy: Boolean,
    val clearActiveSound: Boolean = false,
)

internal fun shouldStopDesktopQuickCardHailing(
    startedByCard: Boolean,
    config: DesktopQuickCardConfig,
): Boolean = startedByCard &&
    (!config.enabled || DESKTOP_QUICK_CARD_REALTIME_HAILING !in config.functionCommands)
