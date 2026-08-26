package com.byd.wjx.audio

import android.content.Intent
import java.util.concurrent.atomic.AtomicReference

internal enum class RealtimeHailingPhase(val wireValue: String) {
    Idle("stopped"),
    Starting("starting"),
    Running("started"),
    Stopping("stopping"),
    Error("error");

    val isActive: Boolean
        get() = this == Starting || this == Running || this == Stopping

    val isRunning: Boolean
        get() = this == Running

    val isBusy: Boolean
        get() = this == Starting || this == Stopping

    companion object {
        fun fromWireValue(value: String?): RealtimeHailingPhase? =
            entries.firstOrNull { it.wireValue == value }
    }
}

internal data class RealtimeHailingState(
    val phase: RealtimeHailingPhase = RealtimeHailingPhase.Idle,
    val message: String = "",
    val channel: ExteriorChannel? = null,
) {
    val isActive: Boolean
        get() = phase.isActive

    val isRunning: Boolean
        get() = phase.isRunning

    val isBusy: Boolean
        get() = phase.isBusy

    fun isStartRequestFor(channel: ExteriorChannel): Boolean =
        phase == RealtimeHailingPhase.Starting && this.channel == channel
}

internal object RealtimeHailingStateContract {
    const val ACTION_STATE = "com.byd.wjx.action.REALTIME_HAILING_STATE"
    private const val EXTRA_PHASE = "state"
    private const val EXTRA_MESSAGE = "message"
    private const val EXTRA_CHANNEL = "channel"

    fun intent(packageName: String, state: RealtimeHailingState): Intent =
        Intent(ACTION_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_PHASE, state.phase.wireValue)
            .putExtra(EXTRA_MESSAGE, state.message)
            .putExtra(EXTRA_CHANNEL, state.channel?.name.orEmpty())

    fun fromIntent(intent: Intent?): RealtimeHailingState? {
        if (intent?.action != ACTION_STATE) return null
        val phase = RealtimeHailingPhase.fromWireValue(intent.getStringExtra(EXTRA_PHASE))
            ?: return null
        val channelName = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        return RealtimeHailingState(
            phase = phase,
            message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
            channel = channelName.takeIf(String::isNotBlank)?.let(ExteriorChannel::fromName),
        )
    }
}

internal object RealtimeHailingStateStore {
    private val state = AtomicReference(RealtimeHailingState())

    fun snapshot(): RealtimeHailingState = state.get()

    fun requestStart(channel: ExteriorChannel): Boolean {
        while (true) {
            val current = state.get()
            if (current.isActive) return false
            val starting = RealtimeHailingState(
                phase = RealtimeHailingPhase.Starting,
                message = "正在准备实时喊话",
                channel = channel,
            )
            if (state.compareAndSet(current, starting)) return true
        }
    }

    fun requestStop(): Boolean {
        while (true) {
            val current = state.get()
            if (!current.isActive) return false
            if (current.phase == RealtimeHailingPhase.Stopping) return false
            val stopping = current.copy(
                phase = RealtimeHailingPhase.Stopping,
                message = "正在停止实时喊话",
            )
            if (state.compareAndSet(current, stopping)) return true
        }
    }

    fun publish(next: RealtimeHailingState) {
        state.set(next)
    }
}
