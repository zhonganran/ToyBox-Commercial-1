package com.byd.wjx.helper

private const val TAG = "LockSound"
private const val TURN_FLASH_CONFIRM = 9
private const val LOCK_CONFIRM_MS = 400L
private const val LOCK_PENDING_EXPIRE_MS = 3_000L
private const val EVENT_SUPPRESS_MS = 5_000L
private const val POWER_SAMPLE_INTERVAL_MS = 300L
private const val LOCK_STATE_SAMPLE_PERSIST_MS = 5_000L
private const val LOCK_FLASH_WINDOW_MS = 5_000L
private const val COLD_UNLOCK_MAX_PREV_AGE_MS = 72L * 60L * 60L * 1000L

internal class LockSoundMonitorState private constructor(
    var stableLock: Int?,
    var stableChangedAt: Long,
    var pendingLock: Int?,
    var pendingSince: Long,
    var lastFlash: Int?,
    var flashAt: Long,
    var lastPersistAt: Long,
    var restoredSampleAt: Long,
    var startupSamplePending: Boolean,
    var pollingEnabled: Boolean,
) {
    var lastLockAt: Long = 0L
    var lastUnlockAt: Long = 0L
    var lastDriverDoorOpen: Boolean = false
    var lastPowerAt: Long = 0L
    var lastPower: LockSoundPowerSnapshot = LockSoundPowerSnapshot(null, null, null)

    fun disablePolling() {
        pollingEnabled = false
        stableLock = null
        pendingLock = null
        pendingSince = 0L
        lastFlash = null
        flashAt = 0L
        lastDriverDoorOpen = false
        lastPowerAt = 0L
        lastPower = LockSoundPowerSnapshot(null, null, null)
    }

    fun restorePolling(nowMs: Long, persisted: PersistedLockState?): PersistedLockState? {
        val restored = recentState(nowMs, persisted)
        pollingEnabled = true
        restoredSampleAt = restored?.sampledAtMs ?: 0L
        startupSamplePending = restored != null
        stableLock = restored?.state
        stableChangedAt = restored?.changedAtMs ?: 0L
        pendingLock = restored?.pendingState?.takeIf {
            restored.pendingSinceMs > 0L && nowMs >= restored.pendingSinceMs &&
                nowMs - restored.pendingSinceMs <= LOCK_PENDING_EXPIRE_MS
        }
        pendingSince = if (pendingLock != null) restored?.pendingSinceMs ?: 0L else 0L
        lastFlash = restored?.flashValue
        flashAt = restored?.flashAtMs?.takeIf {
            it > 0L && nowMs >= it && nowMs - it <= LOCK_FLASH_WINDOW_MS
        } ?: 0L
        lastDriverDoorOpen = false
        lastPowerAt = 0L
        lastPower = LockSoundPowerSnapshot(null, null, null)
        return restored
    }

    fun snapshot(nowMs: Long): PersistedLockState? {
        val state = stableLock ?: return null
        return PersistedLockState(
            state = state,
            changedAtMs = stableChangedAt,
            sampledAtMs = nowMs,
            flashValue = lastFlash,
            flashAtMs = flashAt,
            pendingState = pendingLock,
            pendingSinceMs = pendingSince,
        )
    }

    fun seedStableLock(value: Int) {
        stableLock = value
        stableChangedAt = 0L
        clearPending()
    }

    fun updateStableLock(value: Int, nowMs: Long, force: Boolean = false): Boolean {
        if (stableLock == value && !force) return false
        if (stableLock != value) stableChangedAt = if (stableLock == null) 0L else nowMs
        stableLock = value
        clearPending()
        return true
    }

    fun startPending(value: Int, nowMs: Long): Boolean {
        if (pendingLock == value) return false
        pendingLock = value
        pendingSince = nowMs
        return true
    }

    fun clearPending(): Boolean {
        if (pendingLock == null && pendingSince == 0L) return false
        pendingLock = null
        pendingSince = 0L
        return true
    }

    fun isPendingExpired(nowMs: Long): Boolean =
        pendingLock != null && nowMs - pendingSince > LOCK_PENDING_EXPIRE_MS

    fun observeFlash(value: Int?, nowMs: Long): Boolean {
        if (value == lastFlash) return false
        if (value == TURN_FLASH_CONFIRM) flashAt = nowMs
        lastFlash = value
        return true
    }

    fun isFlashConfirmed(value: Int?, nowMs: Long): Boolean =
        value == TURN_FLASH_CONFIRM || (flashAt > 0L && nowMs - flashAt <= LOCK_FLASH_WINDOW_MS)

    fun expireFlash(value: Int?, nowMs: Long): Boolean {
        if (flashAt <= 0L || value == TURN_FLASH_CONFIRM || nowMs - flashAt <= LOCK_FLASH_WINDOW_MS) return false
        flashAt = 0L
        return true
    }

    fun shouldSamplePower(nowMs: Long): Boolean =
        lastPowerAt <= 0L || nowMs < lastPowerAt || nowMs - lastPowerAt >= POWER_SAMPLE_INTERVAL_MS

    fun shouldPersistSample(nowMs: Long): Boolean =
        nowMs < lastPersistAt || nowMs - lastPersistAt >= LOCK_STATE_SAMPLE_PERSIST_MS

    companion object {
        fun restore(
            nowMs: Long,
            persisted: PersistedLockState?,
            pollingEnabled: Boolean,
        ): LockSoundMonitorState {
            val restored = recentState(nowMs, persisted)
            val pending = restored?.pendingState?.takeIf {
                restored.pendingSinceMs > 0L && nowMs >= restored.pendingSinceMs &&
                    nowMs - restored.pendingSinceMs <= LOCK_PENDING_EXPIRE_MS
            }
            val flashAt = restored?.flashAtMs?.takeIf {
                it > 0L && nowMs >= it && nowMs - it <= LOCK_FLASH_WINDOW_MS
            } ?: 0L
            return LockSoundMonitorState(
                stableLock = restored?.state,
                stableChangedAt = restored?.changedAtMs ?: 0L,
                pendingLock = pending,
                pendingSince = if (pending != null) restored?.pendingSinceMs ?: 0L else 0L,
                lastFlash = restored?.flashValue,
                flashAt = flashAt,
                lastPersistAt = restored?.sampledAtMs ?: 0L,
                restoredSampleAt = restored?.sampledAtMs ?: 0L,
                startupSamplePending = restored != null,
                pollingEnabled = pollingEnabled,
            )
        }

        private fun recentState(nowMs: Long, persisted: PersistedLockState?): PersistedLockState? =
            persisted?.takeIf {
                it.sampledAtMs > 0L && nowMs >= it.sampledAtMs &&
                    nowMs - it.sampledAtMs <= COLD_UNLOCK_MAX_PREV_AGE_MS
            }
    }
}

internal class WjxLockSoundMonitor(
    private val signals: WjxLockSoundSignalReader,
    private val playback: WjxLockSoundPlaybackEngine,
    private val configProvider: () -> LockSoundRuntimeConfig,
    private val stateStore: LockSoundStateStore,
    initialPersistedState: PersistedLockState?,
    private val onStatePersisted: (PersistedLockState?) -> Unit,
    private val playEvent: (kind: Int, reason: String) -> Boolean,
) {
    private val state = LockSoundMonitorState.restore(
        nowMs = System.currentTimeMillis(),
        persisted = initialPersistedState,
        pollingEnabled = configProvider().lock.enabled || configProvider().unlock.enabled,
    )

    fun run() {
        if (state.pendingLock != null || state.flashAt > 0L) {
            helperLog(
                TAG,
                "restored lock state=${state.stableLock} changedAt=${state.stableChangedAt} " +
                    "pending=${state.pendingLock}/${state.pendingSince} flash=${state.lastFlash}/${state.flashAt}",
            )
        }
        while (true) {
            try {
                pollOnce()
            } catch (t: Throwable) {
                helperLog(TAG, "lockLoop error ${throwableSummary(t)}")
                sleepQuietly(1_000L)
            }
        }
    }

    private fun pollOnce() {
        val config = configProvider()
        if (!config.lock.enabled && !config.unlock.enabled) {
            state.disablePolling()
            sleepQuietly(1_000L)
            return
        }
        if (!state.pollingEnabled) {
            val now = System.currentTimeMillis()
            val restored = state.restorePolling(now, stateStore.read())
            onStatePersisted(restored)
        }

        val now = System.currentTimeMillis()
        val lockState = signals.readLockState()
        val flash = signals.readTurnSignalFlash()
        val power = if (state.shouldSamplePower(now)) {
            signals.readPowerSnapshot().also {
                state.lastPower = it
                state.lastPowerAt = now
            }
        } else {
            state.lastPower
        }
        val powerBlocked = power.blocksPlayback()
        val driverDoorOpen = signals.readDriverDoorOpen() == true
        val driverDoorOpenEdge = driverDoorOpen && !state.lastDriverDoorOpen
        if (playback.hasActivePlayback() && driverDoorOpen) {
            playback.keepActiveOutsideForOpenDoor(openedEdge = driverDoorOpenEdge, nowMs = now)
        }
        state.lastDriverDoorOpen = driverDoorOpen
        if (lockState != null && state.stableLock == null) {
            seedStableLock(lockState, now, "seed")
        }

        if (state.observeFlash(flash, now)) {
            if (flash == TURN_FLASH_CONFIRM) {
                helperLog(TAG, "flash9 start lock=${lockState ?: state.stableLock} power=${power.summary()}")
            }
            persistState(now, "flash_changed")
        }
        val inFlashWindow = state.isFlashConfirmed(flash, now)

        if (state.startupSamplePending && lockState != null && state.stableLock != null) {
            state.startupSamplePending = false
            val sampleAge = now - state.restoredSampleAt
            val recoverDifference = shouldRecoverStartupLockDifference(
                previousState = state.stableLock,
                currentState = lockState,
                flashConfirmed = inFlashWindow,
                powerBlocked = powerBlocked,
                sampleAgeMs = sampleAge,
            )
            if (lockState != state.stableLock && !recoverDifference) {
                helperLog(TAG, "seed stale startup state old=${state.stableLock} new=$lockState sampleAge=$sampleAge")
                seedStableLock(lockState, now, "stale_startup_seed")
            }
        }

        if (lockState != null && state.stableLock != null && lockState != state.stableLock) {
            handleTransition(config, lockState, flash, power, powerBlocked, inFlashWindow, now)
        } else if (state.clearPending()) {
            persistState(now, "pending_cleared")
        }

        if (state.expireFlash(flash, now)) persistState(now, "flash_expired")
        if (state.shouldPersistSample(now)) persistState(now, "sample")
        sleepQuietly(if (playback.hasActivePlayback() || flash == TURN_FLASH_CONFIRM) 50L else 150L)
    }

    private fun handleTransition(
        config: LockSoundRuntimeConfig,
        lockState: Int,
        flash: Int?,
        power: LockSoundPowerSnapshot,
        powerBlocked: Boolean,
        inFlashWindow: Boolean,
        now: Long,
    ) {
        if (powerBlocked) {
            helperLog(
                TAG,
                "suppress lock transition powered/unknown old=${state.stableLock} new=$lockState " +
                    "flash=$flash power=${power.summary()}",
            )
            state.flashAt = 0L
            updateStableLock(lockState, now, "power_blocked")
            state.clearPending()
            return
        }
        if (state.startPending(lockState, now)) persistState(now, "pending_transition")
        val event = confirmedLockSoundEvent(
            previousState = state.stableLock,
            currentState = lockState,
            flashConfirmed = inFlashWindow,
            powerBlocked = false,
        )
        if (event != null && now - state.pendingSince >= LOCK_CONFIRM_MS) {
            val enabled = config.eventConfig(event).enabled
            val detail =
                "confirmed old=${state.stableLock} new=$lockState flash=$flash " +
                    "flashConfirmed=$inFlashWindow power=${power.summary()}"
            if (enabled && shouldPlayLockEdge(event, now, detail)) {
                if (event == LockEvent.Lock) state.lastLockAt = now else state.lastUnlockAt = now
                playEvent(event.kind, detail)
            } else if (!enabled) {
                helperLog(TAG, "suppress ${event.key}: disabled old=${state.stableLock} new=$lockState")
            }
            state.flashAt = 0L
            updateStableLock(
                lockState,
                now,
                if (event == LockEvent.Lock && !inFlashWindow) "confirmed_lock_edge" else "confirmed_flash",
            )
            state.clearPending()
        } else if (state.isPendingExpired(now)) {
            helperLog(
                TAG,
                "suppress unlock transition without flash9 old=${state.stableLock} new=$lockState " +
                    "flash=$flash power=${power.summary()}",
            )
            state.flashAt = 0L
            updateStableLock(lockState, now, "suppressed_without_flash9")
            state.clearPending()
        }
    }

    private fun shouldPlayLockEdge(event: LockEvent, now: Long, detail: String): Boolean {
        val lastAt = if (event == LockEvent.Lock) state.lastLockAt else state.lastUnlockAt
        if (now - lastAt >= EVENT_SUPPRESS_MS) return true
        helperLog(TAG, "suppress ${event.key} duplicate $detail")
        return false
    }

    private fun updateStableLock(value: Int, now: Long, reason: String, force: Boolean = false) {
        if (state.updateStableLock(value, now, force)) persistState(now, reason)
    }

    private fun seedStableLock(value: Int, now: Long, reason: String) {
        state.seedStableLock(value)
        persistState(now, reason)
    }

    private fun persistState(now: Long, reason: String) {
        val snapshot = state.snapshot(now) ?: return
        onStatePersisted(snapshot)
        runCatching { stateStore.write(snapshot) }
            .onFailure {
                helperLog(TAG, "lock state cache write failed reason=$reason ${throwableSummary(it)}")
            }
        state.lastPersistAt = now
    }

    private fun sleepQuietly(ms: Long) {
        runCatching { Thread.sleep(ms) }
    }
}
