package com.byd.wjx.helper

internal const val SENTRY_IDLE_BASELINE_INTERVAL_MS = 30_000L
internal const val SENTRY_WARN_ALARM = 2
internal const val SENTRY_FALLBACK_CONFIRM_WINDOW_MS = 15_000L
internal const val SENTRY_RISK_EXIT_CONFIRM_SAMPLES = 3
internal const val SENTRY_POST_LOCK_ARMING_MS = 15_000L
internal const val SENTRY_PLAYBACK_COOLDOWN_MS = 15_000L
internal const val LOCK_SOUND_RECOVERY_MAX_AGE_MS = 5_000L

internal fun confirmedLockSoundEvent(
    previousState: Int?,
    currentState: Int?,
    flashConfirmed: Boolean,
    powerBlocked: Boolean,
): LockEvent? {
    if (powerBlocked) return null
    return when {
        previousState == LOCK_SOUND_DOOR_UNLOCKED && currentState == LOCK_SOUND_DOOR_LOCKED ->
            LockEvent.Lock
        previousState == LOCK_SOUND_DOOR_LOCKED &&
            currentState == LOCK_SOUND_DOOR_UNLOCKED &&
            flashConfirmed -> LockEvent.Unlock
        else -> null
    }
}

internal fun shouldPlayColdStartUnlock(
    previousState: Int?,
    currentState: Int?,
    flash: Int?,
    powerBlocked: Boolean,
): Boolean = confirmedLockSoundEvent(
    previousState = previousState,
    currentState = currentState,
    flashConfirmed = flash == 9,
    powerBlocked = powerBlocked,
) == LockEvent.Unlock

internal fun shouldRecoverStartupLockDifference(
    previousState: Int?,
    currentState: Int?,
    flashConfirmed: Boolean,
    powerBlocked: Boolean,
    sampleAgeMs: Long,
): Boolean = sampleAgeMs in 0L..LOCK_SOUND_RECOVERY_MAX_AGE_MS ||
    confirmedLockSoundEvent(
        previousState = previousState,
        currentState = currentState,
        flashConfirmed = flashConfirmed,
        powerBlocked = powerBlocked,
    ) == LockEvent.Unlock

internal fun isSentryCooldownComplete(nowMs: Long, lastPlaybackAtMs: Long): Boolean =
    lastPlaybackAtMs <= 0L ||
        nowMs < lastPlaybackAtMs ||
        nowMs - lastPlaybackAtMs >= SENTRY_PLAYBACK_COOLDOWN_MS

internal class SentryVehicleAccessGate {
    private var accessObserved = false
    private var secure = false
    private var armingUntilMs = 0L

    fun reset() {
        accessObserved = false
        secure = false
        armingUntilMs = 0L
    }

    fun blockReason(snapshot: LockSoundVehicleAccessSnapshot, nowMs: Long): String? {
        val access = snapshot.accessDetected()
        val nextSecure = snapshot.secureForSentry()
        if (access) accessObserved = true
        if (nextSecure && !secure && accessObserved) {
            armingUntilMs = nowMs + SENTRY_POST_LOCK_ARMING_MS
            accessObserved = false
        }
        secure = nextSecure
        return when {
            access -> "vehicle_access"
            !nextSecure -> "vehicle_access_unconfirmed"
            nowMs < armingUntilMs -> "post_lock_arming"
            else -> null
        }
    }
}

internal fun shouldRunSentryIdleBaseline(
    nowMs: Long,
    lastBaselineAtMs: Long,
    wasGuarding: Boolean,
): Boolean = wasGuarding ||
    lastBaselineAtMs <= 0L ||
    nowMs - lastBaselineAtMs >= SENTRY_IDLE_BASELINE_INTERVAL_MS

internal fun shouldTriggerSentryFallback(
    nowMs: Long,
    lastHighRiskAtMs: Long,
    alarmTimestampChanged: Boolean,
    highRiskLogSeen: Boolean,
): Boolean {
    if (highRiskLogSeen) return true
    if (!alarmTimestampChanged || lastHighRiskAtMs <= 0L) return false
    return nowMs - lastHighRiskAtMs in 0L..SENTRY_FALLBACK_CONFIRM_WINDOW_MS
}

internal fun shouldAcceptExternalSentryTrigger(
    warningLevel: Int?,
    alarmTimestampChanged: Boolean,
    highRiskLogSeen: Boolean,
): Boolean = warningLevel == SENTRY_WARN_ALARM || alarmTimestampChanged || highRiskLogSeen

internal fun shouldTriggerSentryFlashFallback(
    previousFlash: Int?,
    currentFlash: Int?,
    locked: Boolean,
    powerBlocked: Boolean,
): Boolean = locked &&
    !powerBlocked &&
    previousFlash == 9 &&
    currentFlash != null &&
    currentFlash != 9
