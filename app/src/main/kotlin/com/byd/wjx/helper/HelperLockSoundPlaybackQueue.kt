package com.byd.wjx.helper

import com.byd.wjx.audio.ExteriorChannel
import java.util.ArrayDeque

internal data class LockSoundPlaybackRequest(
    val event: LockEvent,
    val eventConfig: EventConfig,
    val preferredChannel: ExteriorChannel,
    val reason: String,
)

internal enum class LockSoundQueueDecision {
    StartNow,
    Queued,
    Merged,
    Full,
}

internal class LockSoundPlaybackQueue(
    private val maxPendingRequests: Int = 8,
) {
    private val pending = ArrayDeque<LockSoundPlaybackRequest>()

    val size: Int
        get() = pending.size

    fun submit(request: LockSoundPlaybackRequest, activeEvent: LockEvent?): LockSoundQueueDecision {
        if (activeEvent == request.event || pending.any { it.event == request.event }) {
            return LockSoundQueueDecision.Merged
        }
        if (activeEvent == null) return LockSoundQueueDecision.StartNow
        if (pending.size >= maxPendingRequests) return LockSoundQueueDecision.Full
        pending.addLast(request)
        return LockSoundQueueDecision.Queued
    }

    fun remove(event: LockEvent): Boolean = pending.removeAll { it.event == event }

    fun poll(): LockSoundPlaybackRequest? = if (pending.isEmpty()) null else pending.removeFirst()
}
