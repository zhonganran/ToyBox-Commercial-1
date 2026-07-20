package com.byd.wjx.helper

import android.content.Context
import android.os.IBinder
import com.byd.wjx.audio.ExteriorChannel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

internal class WjxLockSoundRuntime(
    svc: IBinder,
    autoIface: String,
    systemContext: Context?,
) {
    private val started = AtomicBoolean(false)
    private val sentryReservationLock = Any()
    private val sentryAccessLock = Any()
    private val sentryAccessGate = SentryVehicleAccessGate()
    @Volatile private var config: RuntimeConfig = readRuntimeConfig()
    @Volatile private var persistedLockState: PersistedLockState? = readPersistedLockState()
    private val signals = WjxLockSoundSignalReader(
        svc = svc,
        autoIface = autoIface,
        systemContext = systemContext,
    )
    private val playback = WjxLockSoundPlaybackEngine(
        svc = svc,
        autoIface = autoIface,
        systemContext = systemContext,
        isDriverDoorOpen = { signals.readDriverDoorOpen() == true },
    )
    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (config.anyEnabled()) {
            WjxLockSoundWatchdog.enable()
        }
        Thread({ lockLoop() }, "WJX-LockSoundPoll").apply { isDaemon = true; start() }
        Thread({ sentryLoop() }, "WJX-SentrySoundPoll").apply { isDaemon = true; start() }
        helperLog(TAG, "runtime started config=${config.summary()}")
    }

    fun syncConfig(json: String): Boolean {
        if (json.isBlank()) return false
        val incoming = JSONObject(json)
        val channel = parseChannel(incoming.optString("channel", ExteriorChannel.Channel1.name))
        val next = RuntimeConfig(
            channel = channel,
            unlock = copyEventConfig(incoming.optJSONObject(LOCK_SOUND_KEY_UNLOCK), LOCK_SOUND_UNLOCK_PATH, LOCK_SOUND_UNLOCK_PCM_PATH, LOCK_SOUND_KEY_UNLOCK),
            lock = copyEventConfig(incoming.optJSONObject(LOCK_SOUND_KEY_LOCK), LOCK_SOUND_LOCK_PATH, LOCK_SOUND_LOCK_PCM_PATH, LOCK_SOUND_KEY_LOCK),
            sentry = copyEventConfig(incoming.optJSONObject(LOCK_SOUND_KEY_SENTRY), LOCK_SOUND_SENTRY_PATH, LOCK_SOUND_SENTRY_PCM_PATH, LOCK_SOUND_KEY_SENTRY),
        )
        writeRuntimeConfig(next)
        config = next
        if (next.anyEnabled()) {
            WjxLockSoundWatchdog.enable()
        } else {
            WjxLockSoundWatchdog.disable()
        }
        helperLog(TAG, "config synced ${next.summary()}")
        return true
    }

    fun playEvent(kind: Int, reason: String): Boolean {
        val event = LockEvent.fromKind(kind) ?: return false
        if (event == LockEvent.Sentry) return playRealSentryEvent(reason, blocking = false)
        Thread({ playEventBlocking(event, reason) }, "WJX-LockSoundPlay-${event.key}").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun playEventForBinder(kind: Int, reason: String): Boolean {
        val event = LockEvent.fromKind(kind) ?: return false
        return playEventBlocking(event, reason)
    }

    fun playSentryEventForBinder(reason: String): Boolean =
        playRealSentryEvent(reason, blocking = true)

    fun stopEventForBinder(kind: Int, reason: String): Boolean {
        val event = LockEvent.fromKind(kind) ?: return false
        return playback.stop(event, reason)
    }

    private fun playRealSentryEvent(reason: String, blocking: Boolean): Boolean {
        val sentryConfig = config.sentry
        if (!sentryConfig.enabled || !File(sentryConfig.path).isFile) {
            helperLog(TAG, "suppress sentry disabled reason=$reason")
            return false
        }
        if (!signals.isSentryGuarding()) {
            helperLog(TAG, "suppress sentry not_guarding reason=$reason")
            return false
        }
        val now = System.currentTimeMillis()
        val access = signals.readVehicleAccessSnapshot()
        val accessBlockReason = sentryAccessBlockReason(access, now)
        if (accessBlockReason != null) {
            helperLog(TAG, "suppress sentry $accessBlockReason access=${access.summary()} reason=$reason")
            return false
        }
        if (!reserveSentryPlayback(now, reason)) return false
        if (blocking) return playEventBlocking(LockEvent.Sentry, reason)
        Thread({ playEventBlocking(LockEvent.Sentry, reason) }, "WJX-LockSoundPlay-sentry").apply {
            isDaemon = true
            start()
        }
        return true
    }

    private fun sentryAccessBlockReason(access: LockSoundVehicleAccessSnapshot, now: Long): String? =
        synchronized(sentryAccessLock) {
            sentryAccessGate.blockReason(access, now)?.let { return@synchronized it }
            val lockState = persistedLockState
            if (
                lockState?.state == LOCK_SOUND_DOOR_LOCKED &&
                lockState.changedAtMs > 0L &&
                now >= lockState.changedAtMs &&
                now - lockState.changedAtMs < SENTRY_POST_LOCK_ARMING_MS
            ) {
                return@synchronized "post_lock_arming"
            }
            null
        }

    private fun reserveSentryPlayback(now: Long, reason: String): Boolean = synchronized(sentryReservationLock) {
        val previous = readSentryReservationAt()
        if (!isSentryCooldownComplete(now, previous)) {
            helperLog(TAG, "suppress sentry cooldown remaining=${SENTRY_PLAYBACK_COOLDOWN_MS - (now - previous)}ms reason=$reason")
            return@synchronized false
        }
        val reserved = runCatching {
            writeTextAtomically(
                SENTRY_RESERVATION_PATH,
                JSONObject().put("reservedAtMs", now).put("reason", reason.take(200)).toString(),
            )
            true
        }.getOrElse {
            helperLog(TAG, "sentry reservation write failed ${throwableSummary(it)}")
            false
        }
        if (reserved) helperLog(TAG, "reserve sentry cooldown reason=$reason")
        reserved
    }

    private fun lockLoop() {
        val startupNow = System.currentTimeMillis()
        val restored = persistedLockState?.takeIf {
            it.sampledAtMs > 0L && startupNow >= it.sampledAtMs && startupNow - it.sampledAtMs <= COLD_UNLOCK_MAX_PREV_AGE_MS
        }
        var stableLock: Int? = restored?.state
        var stableChangedAt = restored?.changedAtMs ?: 0L
        var pendingLock: Int? = restored?.pendingState?.takeIf {
            restored.pendingSinceMs > 0L && startupNow >= restored.pendingSinceMs &&
                startupNow - restored.pendingSinceMs <= LOCK_PENDING_EXPIRE_MS
        }
        var pendingSince = if (pendingLock != null) restored?.pendingSinceMs ?: 0L else 0L
        var lastFlash: Int? = restored?.flashValue
        var flashAt = restored?.flashAtMs?.takeIf {
            it > 0L && startupNow >= it && startupNow - it <= LOCK_FLASH_WINDOW_MS
        } ?: 0L
        var lastLockAt = 0L
        var lastUnlockAt = 0L
        var lastDriverDoorOpen = false
        var lastPowerAt = 0L
        var lastPower = LockSoundPowerSnapshot(null, null, null)
        var lastPersistAt = restored?.sampledAtMs ?: 0L
        var restoredSampleAt = restored?.sampledAtMs ?: 0L
        var startupSamplePending = restored != null
        var pollingEnabled = config.lock.enabled || config.unlock.enabled
        fun persistState(now: Long, reason: String) {
            val state = stableLock ?: return
            val snapshot = PersistedLockState(
                state = state,
                changedAtMs = stableChangedAt,
                sampledAtMs = now,
                flashValue = lastFlash,
                flashAtMs = flashAt,
                pendingState = pendingLock,
                pendingSinceMs = pendingSince,
            )
            persistedLockState = snapshot
            writePersistedLockState(snapshot, reason)
            lastPersistAt = now
        }
        fun updateStableLock(value: Int, now: Long, reason: String, force: Boolean = false) {
            if (stableLock == value && !force) return
            if (stableLock != value) stableChangedAt = if (stableLock == null) 0L else now
            stableLock = value
            pendingLock = null
            pendingSince = 0L
            persistState(now, reason)
        }
        fun seedStableLock(value: Int, now: Long, reason: String) {
            stableLock = value
            stableChangedAt = 0L
            pendingLock = null
            pendingSince = 0L
            persistState(now, reason)
        }
        if (pendingLock != null || flashAt > 0L) {
            helperLog(
                TAG,
                "restored lock state=$stableLock changedAt=$stableChangedAt pending=$pendingLock/$pendingSince flash=$lastFlash/$flashAt",
            )
        }
        while (true) {
            try {
                val cfg = config
                if (!cfg.lock.enabled && !cfg.unlock.enabled) {
                    pollingEnabled = false
                    stableLock = null
                    pendingLock = null
                    pendingSince = 0L
                    lastFlash = null
                    flashAt = 0L
                    lastDriverDoorOpen = false
                    lastPowerAt = 0L
                    lastPower = LockSoundPowerSnapshot(null, null, null)
                    sleepQuietly(1_000L)
                    continue
                }
                if (!pollingEnabled) {
                    pollingEnabled = true
                    val now = System.currentTimeMillis()
                    val cached = readPersistedLockState()?.takeIf {
                        it.sampledAtMs > 0L && now >= it.sampledAtMs && now - it.sampledAtMs <= COLD_UNLOCK_MAX_PREV_AGE_MS
                    }
                    persistedLockState = cached
                    restoredSampleAt = cached?.sampledAtMs ?: 0L
                    startupSamplePending = cached != null
                    stableLock = cached?.state
                    stableChangedAt = cached?.changedAtMs ?: 0L
                    pendingLock = cached?.pendingState?.takeIf {
                        cached.pendingSinceMs > 0L && now >= cached.pendingSinceMs &&
                            now - cached.pendingSinceMs <= LOCK_PENDING_EXPIRE_MS
                    }
                    pendingSince = if (pendingLock != null) cached?.pendingSinceMs ?: 0L else 0L
                    lastFlash = cached?.flashValue
                    flashAt = cached?.flashAtMs?.takeIf {
                        it > 0L && now >= it && now - it <= LOCK_FLASH_WINDOW_MS
                    } ?: 0L
                    lastDriverDoorOpen = false
                    lastPowerAt = 0L
                    lastPower = LockSoundPowerSnapshot(null, null, null)
                }
                val now = System.currentTimeMillis()
                val lockState = signals.readLockState()
                val flash = signals.readTurnSignalFlash()
                val power = if (
                    lastPowerAt <= 0L ||
                    now < lastPowerAt ||
                    now - lastPowerAt >= POWER_SAMPLE_INTERVAL_MS
                ) {
                    signals.readPowerSnapshot().also {
                        lastPower = it
                        lastPowerAt = now
                    }
                } else {
                    lastPower
                }
                val powerBlocked = power.blocksPlayback()
                val driverDoorOpen = signals.readDriverDoorOpen() == true
                val driverDoorOpenEdge = driverDoorOpen && !lastDriverDoorOpen
                if (playback.hasActivePlayback() && driverDoorOpen) {
                    playback.keepActiveOutsideForOpenDoor(
                        openedEdge = driverDoorOpenEdge,
                        nowMs = now,
                    )
                }
                lastDriverDoorOpen = driverDoorOpen
                if (lockState != null && stableLock == null) {
                    seedStableLock(lockState, now, "seed")
                }

                if (flash != lastFlash) {
                    if (flash == TURN_FLASH_CONFIRM) {
                        flashAt = now
                        helperLog(TAG, "flash9 start lock=${lockState ?: stableLock} power=${power.summary()}")
                    }
                    lastFlash = flash
                    persistState(now, "flash_changed")
                }

                val inFlashWindow = flash == TURN_FLASH_CONFIRM || (flashAt > 0L && now - flashAt <= LOCK_FLASH_WINDOW_MS)

                if (startupSamplePending && lockState != null && stableLock != null) {
                    startupSamplePending = false
                    val sampleAge = now - restoredSampleAt
                    val recoverDifference = shouldRecoverStartupLockDifference(
                        previousState = stableLock,
                        currentState = lockState,
                        flashConfirmed = inFlashWindow,
                        powerBlocked = powerBlocked,
                        sampleAgeMs = sampleAge,
                    )
                    if (lockState != stableLock && !recoverDifference) {
                        helperLog(TAG, "seed stale startup state old=$stableLock new=$lockState sampleAge=$sampleAge")
                        seedStableLock(lockState, now, "stale_startup_seed")
                    }
                }

                if (lockState != null && stableLock != null && lockState != stableLock) {
                    if (powerBlocked) {
                        helperLog(TAG, "suppress lock transition powered/unknown old=$stableLock new=$lockState flash=$flash power=${power.summary()}")
                        flashAt = 0L
                        updateStableLock(lockState, now, "power_blocked")
                        pendingLock = null
                        pendingSince = 0L
                    } else {
                        if (pendingLock != lockState) {
                            pendingLock = lockState
                            pendingSince = now
                            persistState(now, "pending_transition")
                        }
                        val event = confirmedLockSoundEvent(
                            previousState = stableLock,
                            currentState = lockState,
                            flashConfirmed = inFlashWindow,
                            powerBlocked = false,
                        )
                        if (event != null && now - pendingSince >= LOCK_CONFIRM_MS) {
                            val enabled = cfg.eventConfig(event).enabled
                            if (enabled && shouldPlayLockEdge(event, now, lastLockAt, lastUnlockAt, "confirmed old=$stableLock new=$lockState flash=$flash flashConfirmed=$inFlashWindow power=${power.summary()}")) {
                                if (event == LockEvent.Lock) lastLockAt = now else lastUnlockAt = now
                                playEvent(event.kind, "confirmed old=$stableLock new=$lockState flash=$flash flashConfirmed=$inFlashWindow power=${power.summary()}")
                            } else if (!enabled) {
                                helperLog(TAG, "suppress ${event.key}: disabled old=$stableLock new=$lockState")
                            }
                            flashAt = 0L
                            updateStableLock(
                                lockState,
                                now,
                                if (event == LockEvent.Lock && !inFlashWindow) "confirmed_lock_edge" else "confirmed_flash",
                            )
                            pendingLock = null
                            pendingSince = 0L
                        } else if (now - pendingSince > LOCK_PENDING_EXPIRE_MS) {
                            helperLog(
                                TAG,
                                "suppress unlock transition without flash9 old=$stableLock new=$lockState flash=$flash power=${power.summary()}",
                            )
                            flashAt = 0L
                            updateStableLock(lockState, now, "suppressed_without_flash9")
                            pendingLock = null
                            pendingSince = 0L
                        }
                    }
                } else {
                    if (pendingLock != null) {
                        pendingLock = null
                        pendingSince = 0L
                        persistState(now, "pending_cleared")
                    }
                }

                if (flashAt > 0L && flash != TURN_FLASH_CONFIRM && now - flashAt > LOCK_FLASH_WINDOW_MS) {
                    flashAt = 0L
                    persistState(now, "flash_expired")
                }
                if (now < lastPersistAt || now - lastPersistAt >= LOCK_STATE_SAMPLE_PERSIST_MS) {
                    persistState(now, "sample")
                }
                sleepQuietly(if (playback.hasActivePlayback() || flash == TURN_FLASH_CONFIRM) 50L else 150L)
            } catch (t: Throwable) {
                helperLog(TAG, "lockLoop error ${throwableSummary(t)}")
                sleepQuietly(1_000L)
            }
        }
    }

    private fun shouldPlayLockEdge(
        event: LockEvent,
        now: Long,
        lastLockAt: Long,
        lastUnlockAt: Long,
        detail: String,
    ): Boolean {
        val lastAt = if (event == LockEvent.Lock) lastLockAt else lastUnlockAt
        if (now - lastAt >= EVENT_SUPPRESS_MS) return true
        helperLog(TAG, "suppress ${event.key} duplicate $detail")
        return false
    }

    private fun sentryLoop() {
        var lastLogScanAt = 0L
        var lastIdleBaselineAt = 0L
        var lastHighRiskAt = 0L
        var riskExitSamples = 0
        var wasGuarding = false
        var lastAccessBlockReason: String? = null
        var lastSentryFlash: Int? = null
        while (true) {
            try {
                if (!config.sentry.enabled) {
                    lastHighRiskAt = 0L
                    riskExitSamples = 0
                    lastIdleBaselineAt = 0L
                    wasGuarding = false
                    lastAccessBlockReason = null
                    lastSentryFlash = null
                    synchronized(sentryAccessLock) { sentryAccessGate.reset() }
                    sleepQuietly(2_000L)
                    continue
                }
                val now = System.currentTimeMillis()
                val access = signals.readVehicleAccessSnapshot()
                val accessBlockReason = sentryAccessBlockReason(access, now)
                val guarding = signals.isSentryGuarding()
                if (!guarding) {
                    lastHighRiskAt = 0L
                    riskExitSamples = 0
                    if (shouldRunSentryIdleBaseline(now, lastIdleBaselineAt, wasGuarding)) {
                        val warning = signals.readSentryWarning()
                        val propTs = signals.readSentryAlarmTimestamp()
                        signals.consumeSentryAlarmTimestamp(propTs, allowTrigger = false)
                        lastLogScanAt = now
                        signals.readFreshSentryLogMarker(allowTrigger = false)
                        lastIdleBaselineAt = now
                        helperLog(
                            TAG,
                            "sentry idle baseline warning=${warning.level} source=${warning.source} propTs=$propTs",
                        )
                    }
                    wasGuarding = false
                    lastAccessBlockReason = null
                    lastSentryFlash = null
                    sleepQuietly(2_000L)
                    continue
                }

                val warningReading = signals.readSentryWarning()
                val warning = warningReading.level
                val propTs = signals.readSentryAlarmTimestamp()
                var alarmTimestampChanged = false
                var highRiskLogMarker = ""
                if (!wasGuarding) {
                    alarmTimestampChanged = signals.consumeSentryAlarmTimestamp(propTs, allowTrigger = true)
                    highRiskLogMarker = signals.readFreshSentryLogMarker(allowTrigger = true)
                    lastLogScanAt = now
                    wasGuarding = true
                    helperLog(
                        TAG,
                        "sentry guarding start warning=$warning source=${warningReading.source} propTs=$propTs freshProp=$alarmTimestampChanged freshLog=${highRiskLogMarker.isNotBlank()}",
                    )
                }
                wasGuarding = true

                if (accessBlockReason != null) {
                    lastHighRiskAt = 0L
                    riskExitSamples = 0
                    signals.consumeSentryAlarmTimestamp(propTs, allowTrigger = false)
                    if (now - lastLogScanAt >= SENTRY_LOG_SCAN_MS) {
                        lastLogScanAt = now
                        signals.readFreshSentryLogMarker(allowTrigger = false)
                    }
                    if (lastAccessBlockReason != accessBlockReason) {
                        helperLog(TAG, "sentry blocked reason=$accessBlockReason access=${access.summary()}")
                    }
                    lastAccessBlockReason = accessBlockReason
                    lastSentryFlash = signals.readTurnSignalFlash()
                    sleepQuietly(750L)
                    continue
                }
                if (lastAccessBlockReason != null) {
                    helperLog(TAG, "sentry access gate cleared access=${access.summary()}")
                    lastAccessBlockReason = null
                }

                if (warning == SENTRY_WARN_ALARM) {
                    if (lastHighRiskAt <= 0L) {
                        helperLog(TAG, "sentry high risk observed source=${warningReading.source}")
                    }
                    lastHighRiskAt = now
                    riskExitSamples = 0
                } else if (warning != null) {
                    riskExitSamples = (riskExitSamples + 1).coerceAtMost(SENTRY_RISK_EXIT_CONFIRM_SAMPLES)
                    if (riskExitSamples >= SENTRY_RISK_EXIT_CONFIRM_SAMPLES && lastHighRiskAt > 0L) {
                        helperLog(TAG, "sentry risk exited warning=$warning source=${warningReading.source}")
                        lastHighRiskAt = 0L
                    }
                } else {
                    riskExitSamples = 0
                }

                if (!alarmTimestampChanged) {
                    alarmTimestampChanged = signals.consumeSentryAlarmTimestamp(propTs, allowTrigger = true)
                }
                if (highRiskLogMarker.isBlank() && now - lastLogScanAt >= SENTRY_LOG_SCAN_MS) {
                    lastLogScanAt = now
                    highRiskLogMarker = signals.readFreshSentryLogMarker(allowTrigger = true)
                }

                val flash = signals.readTurnSignalFlash()
                val power = signals.readPowerSnapshot()
                val previousSentryFlash = lastSentryFlash
                val flashFallbackSeen = shouldTriggerSentryFlashFallback(
                    previousFlash = previousSentryFlash,
                    currentFlash = flash,
                    locked = access.lockFrontLeft == LOCK_SOUND_DOOR_LOCKED,
                    powerBlocked = power.blocksPlayback(),
                )
                lastSentryFlash = flash

                if (shouldTriggerSentryFallback(
                        nowMs = now,
                        lastHighRiskAtMs = lastHighRiskAt,
                        alarmTimestampChanged = alarmTimestampChanged,
                        highRiskLogSeen = highRiskLogMarker.isNotBlank(),
                    ) || flashFallbackSeen
                ) {
                    val reasons = ArrayList<String>(4)
                    reasons += "warning=$warning source=${warningReading.source}"
                    if (alarmTimestampChanged) reasons += "prop_ts=$propTs"
                    if (highRiskLogMarker.isNotBlank()) reasons += "logcat=${highRiskLogMarker.take(80)}"
                    if (flashFallbackSeen) reasons += "flash_fallback=$previousSentryFlash->$flash power=${power.summary()}"
                    playEvent(LockEvent.Sentry.kind, "sentry ${reasons.joinToString(" | ")}")
                }
                sleepQuietly(750L)
            } catch (t: Throwable) {
                helperLog(TAG, "sentryLoop error ${throwableSummary(t)}")
                sleepQuietly(2_000L)
            }
        }
    }

    private fun playEventBlocking(event: LockEvent, reason: String): Boolean {
        val cfg = config
        return playback.play(
            event = event,
            eventCfg = cfg.eventConfig(event),
            preferredChannel = cfg.channel,
            reason = reason,
        )
    }

    private fun copyEventConfig(json: JSONObject?, destPath: String, pcmPath: String, key: String): EventConfig {
        if (json == null || !json.optBoolean("enabled", false)) return EventConfig(enabled = false, path = "")
        val sourcePath = json.optString("path").trim()
        val source = File(sourcePath)
        if (!source.isFile || source.length() <= 0L) {
            helperLog(TAG, "sync $key missing source=$sourcePath")
            return EventConfig(enabled = false, path = "")
        }
        val dest = File(destPath)
        runCatching {
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure {
            helperLog(TAG, "sync $key copy failed: ${throwableSummary(it)}")
            return EventConfig(enabled = false, path = "")
        }
        val enabled = dest.isFile && dest.length() > 0L
        val pcm = if (enabled) cachePcm(dest, pcmPath, key) else PcmConfig()
        return EventConfig(enabled = enabled, path = dest.absolutePath, pcm = pcm)
    }

    private fun cachePcm(source: File, pcmPath: String, key: String): PcmConfig {
        val pcmFile = File(pcmPath)
        runCatching { pcmFile.delete() }
        val cached = HelperPcmDecoder.decodeToPcm16(source, pcmFile, key)
        if (cached != null && cached.usable()) {
            helperLog(TAG, "pcm cache ready key=$key path=${pcmFile.name} bytes=${cached.bytes} rate=${cached.sampleRate} channels=${cached.channelCount}")
            return cached
        }
        runCatching { pcmFile.delete() }
        helperLog(TAG, "pcm cache unavailable key=$key fallback=MediaPlayer")
        return PcmConfig()
    }

    private data class RuntimeConfig(
        val channel: ExteriorChannel = ExteriorChannel.Channel1,
        val unlock: EventConfig = EventConfig(),
        val lock: EventConfig = EventConfig(),
        val sentry: EventConfig = EventConfig(),
    ) {
        fun anyEnabled(): Boolean = unlock.enabled || lock.enabled || sentry.enabled

        fun eventConfig(event: LockEvent): EventConfig =
            when (event) {
                LockEvent.Unlock -> unlock
                LockEvent.Lock -> lock
                LockEvent.Sentry -> sentry
            }

        fun summary(): String =
            "channel=$channel playerVolume=1.0 playerGain=$LOCK_SOUND_PLAYER_ENHANCER_GAIN_MB pcmGain=$LOCK_SOUND_PCM_ENHANCER_GAIN_MB " +
                "unlock=${unlock.enabled}/pcm=${unlock.pcm.usable()} " +
                "lock=${lock.enabled}/pcm=${lock.pcm.usable()} " +
                "sentry=${sentry.enabled}/pcm=${sentry.pcm.usable()}"
    }

    private data class PersistedLockState(
        val state: Int,
        val changedAtMs: Long,
        val sampledAtMs: Long,
        val flashValue: Int?,
        val flashAtMs: Long,
        val pendingState: Int?,
        val pendingSinceMs: Long,
    )

    private companion object {
        private const val TAG = "LockSound"
        private const val TURN_FLASH_CONFIRM = 9
        private const val LOCK_CONFIRM_MS = 400L
        private const val LOCK_PENDING_EXPIRE_MS = 3_000L
        private const val EVENT_SUPPRESS_MS = 5_000L
        private const val POWER_SAMPLE_INTERVAL_MS = 300L
        private const val LOCK_STATE_SAMPLE_PERSIST_MS = 5_000L
        private const val SENTRY_LOG_SCAN_MS = 1_000L
        private const val LOCK_FLASH_WINDOW_MS = 5_000L
        private const val COLD_UNLOCK_MAX_PREV_AGE_MS = 72L * 60L * 60L * 1000L
        private const val LOCK_SOUND_CONFIG_PATH = "/data/local/tmp/wjx_lock_sound.json"
        private const val LOCK_STATE_CACHE_PATH = "/data/local/tmp/wjx_lock_state.txt"
        private const val SENTRY_RESERVATION_PATH = "/data/local/tmp/wjx_sentry_playback_reservation.json"
        private const val LOCK_SOUND_UNLOCK_PATH = "/data/local/tmp/wjx_unlock.wav"
        private const val LOCK_SOUND_LOCK_PATH = "/data/local/tmp/wjx_lock.wav"
        private const val LOCK_SOUND_SENTRY_PATH = "/data/local/tmp/wjx_sentry.wav"
        private const val LOCK_SOUND_UNLOCK_PCM_PATH = "/data/local/tmp/wjx_unlock.pcm"
        private const val LOCK_SOUND_LOCK_PCM_PATH = "/data/local/tmp/wjx_lock.pcm"
        private const val LOCK_SOUND_SENTRY_PCM_PATH = "/data/local/tmp/wjx_sentry.pcm"

        private fun parseChannel(value: String): ExteriorChannel =
            runCatching { ExteriorChannel.valueOf(value) }.getOrDefault(ExteriorChannel.Channel1)

        private fun readRuntimeConfig(): RuntimeConfig {
            val file = File(LOCK_SOUND_CONFIG_PATH)
            if (!file.isFile) return RuntimeConfig()
            return runCatching {
                val json = JSONObject(file.readText())
                RuntimeConfig(
                    channel = parseChannel(json.optString("channel", ExteriorChannel.Channel1.name)),
                    unlock = json.readEvent(LOCK_SOUND_KEY_UNLOCK),
                    lock = json.readEvent(LOCK_SOUND_KEY_LOCK),
                    sentry = json.readEvent(LOCK_SOUND_KEY_SENTRY),
                )
            }.getOrDefault(RuntimeConfig())
        }

        private fun writeRuntimeConfig(config: RuntimeConfig) {
            val json = JSONObject()
                .put("version", 1)
                .put("channel", config.channel.name)
                .putEvent(LOCK_SOUND_KEY_UNLOCK, config.unlock)
                .putEvent(LOCK_SOUND_KEY_LOCK, config.lock)
                .putEvent(LOCK_SOUND_KEY_SENTRY, config.sentry)
            File(LOCK_SOUND_CONFIG_PATH).writeText(json.toString())
        }

        private fun readPersistedLockState(): PersistedLockState? {
            val file = File(LOCK_STATE_CACHE_PATH)
            if (!file.isFile) return null
            return runCatching {
                val json = JSONObject(file.readText())
                val state = json.optInt("state", Int.MIN_VALUE)
                if (state == LOCK_SOUND_DOOR_LOCKED || state == LOCK_SOUND_DOOR_UNLOCKED) {
                    PersistedLockState(
                        state = state,
                        changedAtMs = json.optLong("changedAtMs", 0L),
                        sampledAtMs = json.optLong("sampledAtMs", 0L),
                        flashValue = json.optInt("flashValue", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                        flashAtMs = json.optLong("flashAtMs", 0L),
                        pendingState = json.optInt("pendingState", Int.MIN_VALUE)
                            .takeIf { it == LOCK_SOUND_DOOR_LOCKED || it == LOCK_SOUND_DOOR_UNLOCKED },
                        pendingSinceMs = json.optLong("pendingSinceMs", 0L),
                    )
                } else {
                    null
                }
            }.getOrNull()
        }

        private fun writePersistedLockState(state: PersistedLockState, reason: String) {
            runCatching {
                writeTextAtomically(
                    LOCK_STATE_CACHE_PATH,
                    JSONObject()
                        .put("version", 2)
                        .put("state", state.state)
                        .put("changedAtMs", state.changedAtMs)
                        .put("sampledAtMs", state.sampledAtMs)
                        .put("flashValue", state.flashValue ?: JSONObject.NULL)
                        .put("flashAtMs", state.flashAtMs)
                        .put("pendingState", state.pendingState ?: JSONObject.NULL)
                        .put("pendingSinceMs", state.pendingSinceMs)
                        .toString(),
                )
            }.onFailure {
                helperLog(TAG, "lock state cache write failed reason=$reason ${throwableSummary(it)}")
            }
        }

        private fun readSentryReservationAt(): Long {
            val file = File(SENTRY_RESERVATION_PATH)
            if (!file.isFile) return 0L
            return runCatching { JSONObject(file.readText()).optLong("reservedAtMs", 0L) }.getOrDefault(0L)
        }

        private fun writeTextAtomically(path: String, value: String) {
            val target = File(path)
            val temporary = File("$path.tmp")
            temporary.writeText(value)
            if (!temporary.renameTo(target)) {
                target.writeText(value)
                temporary.delete()
            }
        }

        private fun JSONObject.readEvent(key: String): EventConfig {
            val obj = optJSONObject(key) ?: return EventConfig()
            val path = obj.optString("path")
            val pcm = obj.optJSONObject("pcm")?.let {
                PcmConfig(
                    path = it.optString("path"),
                    sampleRate = it.optInt("sampleRate", 0),
                    channelCount = it.optInt("channelCount", 0),
                    bytes = it.optLong("bytes", 0L),
                )
            } ?: PcmConfig()
            return EventConfig(
                enabled = obj.optBoolean("enabled", false) && File(path).isFile,
                path = path,
                pcm = pcm.takeIf { it.usable() && File(it.path).isFile } ?: PcmConfig(),
            )
        }

        private fun JSONObject.putEvent(key: String, config: EventConfig): JSONObject =
            put(
                key,
                JSONObject()
                    .put("enabled", config.enabled)
                    .put("path", config.path)
                    .put(
                        "pcm",
                        JSONObject()
                            .put("path", config.pcm.path)
                            .put("sampleRate", config.pcm.sampleRate)
                            .put("channelCount", config.pcm.channelCount)
                            .put("bytes", config.pcm.bytes),
                    ),
            )

        private fun sleepQuietly(ms: Long) {
            runCatching { Thread.sleep(ms) }
        }
    }
}
