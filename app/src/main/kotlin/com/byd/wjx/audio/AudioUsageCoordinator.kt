package com.byd.wjx.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

enum class AudioOwner {
    WakeWordListener,
    XiaozhiConversation,
    RealtimeHailing,
    ExteriorPlayback,
}

/**
 * Process-wide ownership signal for microphone and exterior-audio users.
 *
 * Owners publish before they begin opening a resource and release only after the
 * underlying recorder or BYD exterior route is closed. Competing features can then
 * request a stop and await the real release instead of relying on a fixed sleep.
 */
@Singleton
class AudioUsageCoordinator @Inject constructor() {
    private val _activeOwners = MutableStateFlow<Set<AudioOwner>>(emptySet())
    val activeOwners: StateFlow<Set<AudioOwner>> = _activeOwners.asStateFlow()

    fun setActive(owner: AudioOwner, active: Boolean) {
        _activeOwners.update { current ->
            if (active) current + owner else current - owner
        }
    }

    fun isActive(owner: AudioOwner): Boolean = owner in _activeOwners.value

    fun isAnyActive(excluding: Set<AudioOwner> = emptySet()): Boolean =
        _activeOwners.value.any { it !in excluding }

    suspend fun awaitInactive(
        owners: Set<AudioOwner>,
        timeoutMs: Long,
    ): Boolean {
        if (_activeOwners.value.none { it in owners }) return true
        return withTimeoutOrNull(timeoutMs) {
            activeOwners.first { active -> active.none { it in owners } }
            true
        } ?: false
    }
}
