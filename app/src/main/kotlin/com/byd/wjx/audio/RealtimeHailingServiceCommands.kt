package com.byd.wjx.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.byd.wjx.log.WjxFileLogger

internal sealed interface RealtimeHailingServiceCommand {
    data class Start(val channel: ExteriorChannel) : RealtimeHailingServiceCommand

    data class Stop(
        val notifyIfIdle: Boolean,
        val finalPhase: RealtimeHailingPhase = RealtimeHailingPhase.Idle,
        val message: String = "实时喊话已停止",
    ) : RealtimeHailingServiceCommand

    data object UpdateTuning : RealtimeHailingServiceCommand
}

internal object RealtimeHailingServiceCommandContract {
    private const val ACTION_START = "com.byd.wjx.action.START_REALTIME_HAILING"
    private const val ACTION_STOP = "com.byd.wjx.action.STOP_REALTIME_HAILING"
    private const val ACTION_UPDATE_TUNING = "com.byd.wjx.action.UPDATE_REALTIME_HAILING_TUNING"
    private const val EXTRA_CHANNEL = "channel"
    private const val EXTRA_NOTIFY_IDLE = "notify_idle"

    fun fromIntent(intent: Intent?): RealtimeHailingServiceCommand? = when (intent?.action) {
        ACTION_START -> RealtimeHailingServiceCommand.Start(
            ExteriorChannel.fromName(intent.getStringExtra(EXTRA_CHANNEL)),
        )
        ACTION_STOP -> RealtimeHailingServiceCommand.Stop(
            notifyIfIdle = intent.getBooleanExtra(EXTRA_NOTIFY_IDLE, false),
        )
        ACTION_UPDATE_TUNING -> RealtimeHailingServiceCommand.UpdateTuning
        else -> null
    }

    fun startIntent(context: Context, channel: ExteriorChannel): Intent =
        Intent(context, WjxRealtimeHailingService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_CHANNEL, channel.name)

    fun stopIntent(context: Context, notifyIfIdle: Boolean): Intent =
        Intent(context, WjxRealtimeHailingService::class.java)
            .setAction(ACTION_STOP)
            .putExtra(EXTRA_NOTIFY_IDLE, notifyIfIdle)

    fun updateTuningIntent(context: Context): Intent =
        Intent(context, WjxRealtimeHailingService::class.java)
            .setAction(ACTION_UPDATE_TUNING)
}

internal object RealtimeHailingServiceGateway {
    fun state(): RealtimeHailingState = RealtimeHailingStateStore.snapshot()

    fun start(context: Context, channel: ExteriorChannel): Boolean {
        if (!RealtimeHailingStateStore.requestStart(channel)) return false
        try {
            ContextCompat.startForegroundService(
                context.applicationContext,
                RealtimeHailingServiceCommandContract.startIntent(context.applicationContext, channel),
            )
        } catch (error: RuntimeException) {
            RealtimeHailingStateStore.publish(
                RealtimeHailingState(
                    phase = RealtimeHailingPhase.Error,
                    message = "实时喊话服务启动失败",
                    channel = channel,
                ),
            )
            throw error
        }
        return true
    }

    fun stop(context: Context, notifyIfIdle: Boolean): Boolean {
        val previous = state()
        if (!RealtimeHailingStateStore.requestStop()) return false
        try {
            ContextCompat.startForegroundService(
                context.applicationContext,
                RealtimeHailingServiceCommandContract.stopIntent(
                    context.applicationContext,
                    notifyIfIdle,
                ),
            )
        } catch (error: RuntimeException) {
            RealtimeHailingStateStore.publish(
                RealtimeHailingState(
                    phase = RealtimeHailingPhase.Error,
                    message = "实时喊话服务停止失败",
                    channel = previous.channel,
                ),
            )
            throw error
        }
        return true
    }

    fun toggle(context: Context, channel: ExteriorChannel): Boolean = runCatching {
        if (state().isActive) {
            stop(context, notifyIfIdle = true)
        } else {
            start(context, channel)
        }
    }.onFailure { error ->
        WjxFileLogger.e(TAG, "realtime hailing toggle command failed: ${error.message}", error)
    }.getOrDefault(false)

    fun updateTuning(context: Context) {
        if (!state().isRunning) return
        runCatching {
            ContextCompat.startForegroundService(
                context.applicationContext,
                RealtimeHailingServiceCommandContract.updateTuningIntent(context.applicationContext),
            )
        }.onFailure { error ->
            WjxFileLogger.e(TAG, "realtime hailing tuning command failed: ${error.message}", error)
        }
    }

    private const val TAG = "RealtimeHailingGateway"
}
