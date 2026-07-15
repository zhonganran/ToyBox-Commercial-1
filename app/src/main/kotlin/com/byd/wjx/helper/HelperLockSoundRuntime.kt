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
    private val sentryStateLock = Any()
    @Volatile private var config: RuntimeConfig = readRuntimeConfig()
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
    private var sentryIncidentLatched = false

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
        if (event == LockEvent.Sentry && !reserveSentryEvent(reason)) return false
        Thread({ playEventBlocking(event, reason) }, "WJX-LockSoundPlay-${event.key}").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun playEventForBinder(kind: Int, reason: String): Boolean {
        val event = LockEvent.fromKind(kind) ?: return false
        if (event == LockEvent.Sentry && !reserveSentryEvent(reason)) return false
        return playEventBlocking(event, reason)
    }

    fun stopEventForBinder(kind: Int, reason: String): Boolean {
        val event = LockEvent.fromKind(kind) ?: return false
        return playback.stop(event, reason)
    }

    private fun reserveSentryEvent(reason: String): Boolean {
        synchronized(sentryStateLock) {
            if (sentryIncidentLatched) {
                helperLog(TAG, "suppress sentry same incident reason=$reason")
                return false
            }
            sentryIncidentLatched = true
            helperLog(TAG, "reserve sentry incident reason=$reason")
            return true
        }
    }

    private fun resetSentryIncident(reason: String) {
        val reset = synchronized(sentryStateLock) {
            if (!sentryIncidentLatched) return@synchronized false
            sentryIncidentLatched = false
            true
        }
        if (reset) helperLog(TAG, "reset sentry incident reason=$reason")
    }

    private fun lockLoop() {
        var stableLock: Int? = readPersistedLockState()?.state
        var pendingLock: Int? = null
        var pendingSince = 0L
        var lastFlash: Int? = null
        var flashAt = 0L
        var lastLockAt = 0L
        var lastUnlockAt = 0L
        var lastDriverDoorOpen = false
        var coldStartChecked = false
        var lastPowerAt = 0L
        var lastPower = LockSoundPowerSnapshot(null, null, null)
        fun updateStableLock(value: Int, reason: String, force: Boolean = false) {
            if (stableLock == value && !force) return
            stableLock = value
            writePersistedLockState(value, reason)
        }
        while (true) {
            try {
                val cfg = config
                if (!cfg.lock.enabled && !cfg.unlock.enabled) {
                    signals.readLockState()?.let { stableLock = it }
                    sleepQuietly(1_000L)
                    continue
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
                if (playback.isActive(LockEvent.Unlock) && driverDoorOpen) {
                    playback.keepUnlockOutsideForOpenDoor(
                        openedEdge = driverDoorOpenEdge,
                        nowMs = now,
                    )
                }
                lastDriverDoorOpen = driverDoorOpen
                if (lockState != null && stableLock == null) {
                    updateStableLock(lockState, "seed")
                }

                if (!coldStartChecked && lockState != null) {
                    coldStartChecked = true
                    val previous = readPersistedLockState()
                    if (
                        previous != null &&
                        shouldPlayColdStartUnlock(
                            previousState = previous.state,
                            currentState = lockState,
                            flash = flash,
                            powerBlocked = powerBlocked,
                        ) &&
                        now - previous.updatedAtMs <= COLD_UNLOCK_MAX_PREV_AGE_MS &&
                        cfg.unlock.enabled &&
                        shouldPlayLockEdge(LockEvent.Unlock, now, lastLockAt, lastUnlockAt, "cold_start prev=${previous.state} age=${now - previous.updatedAtMs} flash=$flash power=${power.summary()}")
                    ) {
                        lastUnlockAt = now
                        helperLog(TAG, "cold-start unlock confirmed prev=${previous.state} age=${now - previous.updatedAtMs} flash=$flash power=${power.summary()}")
                        playEvent(LockEvent.Unlock.kind, "cold_start prev=${previous.state} current=$lockState flash=$flash power=${power.summary()}")
                    }
                    updateStableLock(lockState, "cold_start", force = true)
                }

                if (flash == TURN_FLASH_CONFIRM && lastFlash != TURN_FLASH_CONFIRM) {
                    flashAt = now
                    helperLog(TAG, "flash9 start lock=${lockState ?: stableLock} power=${power.summary()}")
                }

                val inFlashWindow = flash == TURN_FLASH_CONFIRM || (flashAt > 0L && now - flashAt <= LOCK_FLASH_WINDOW_MS)

                if (lockState != null && stableLock != null && lockState != stableLock) {
                    if (powerBlocked) {
                        helperLog(TAG, "suppress lock transition powered/unknown old=$stableLock new=$lockState flash=$flash power=${power.summary()}")
                        updateStableLock(lockState, "power_blocked")
                        pendingLock = null
                        flashAt = 0L
                    } else {
                        if (pendingLock != lockState) {
                            pendingLock = lockState
                            pendingSince = now
                        }
                        val event = confirmedLockSoundEvent(
                            previousState = stableLock,
                            currentState = lockState,
                            flashConfirmed = inFlashWindow,
                            powerBlocked = false,
                        )
                        if (event != null && now - pendingSince >= LOCK_CONFIRM_MS) {
                            val enabled = cfg.eventConfig(event).enabled
                            if (enabled && shouldPlayLockEdge(event, now, lastLockAt, lastUnlockAt, "confirmed old=$stableLock new=$lockState flash=$flash power=${power.summary()}")) {
                                if (event == LockEvent.Lock) lastLockAt = now else lastUnlockAt = now
                                playEvent(event.kind, "confirmed old=$stableLock new=$lockState flash=$flash power=${power.summary()}")
                            } else if (!enabled) {
                                helperLog(TAG, "suppress ${event.key}: disabled old=$stableLock new=$lockState")
                            }
                            updateStableLock(lockState, "confirmed_flash9")
                            pendingLock = null
                            flashAt = 0L
                        } else if (now - pendingSince > LOCK_PENDING_EXPIRE_MS) {
                            helperLog(
                                TAG,
                                "suppress lock transition without flash9 old=$stableLock new=$lockState flash=$flash power=${power.summary()}",
                            )
                            updateStableLock(lockState, "suppressed_without_flash9")
                            pendingLock = null
                        }
                    }
                } else {
                    pendingLock = null
                }

                if (flashAt > 0L && flash != TURN_FLASH_CONFIRM && now - flashAt > LOCK_FLASH_WINDOW_MS) {
                    flashAt = 0L
                }
                lastFlash = flash
                sleepQuietly(if (playback.isActive(LockEvent.Unlock) || flash == TURN_FLASH_CONFIRM) 50L else 150L)
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
        var lastSentryPlaybackAt = 0L
        var lastAccessBlockReason: String? = null
        val accessGate = SentryVehicleAccessGate()
        while (true) {
            try {
                if (!config.sentry.enabled) {
                    resetSentryIncident("disabled")
                    lastHighRiskAt = 0L
                    riskExitSamples = 0
                    lastIdleBaselineAt = 0L
                    wasGuarding = false
                    lastSentryPlaybackAt = 0L
                    lastAccessBlockReason = null
                    accessGate.reset()
                    sleepQuietly(2_000L)
                    continue
                }
                val now = System.currentTimeMillis()
                val access = signals.readVehicleAccessSnapshot()
                val accessBlockReason = accessGate.blockReason(access, now)
                val guarding = signals.isSentryGuarding()
                if (!guarding) {
                    resetSentryIncident("not_guarding")
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
                    sleepQuietly(2_000L)
                    continue
                }

                val warningReading = signals.readSentryWarning()
                val warning = warningReading.level
                val propTs = signals.readSentryAlarmTimestamp()
                if (!wasGuarding) {
                    signals.consumeSentryAlarmTimestamp(propTs, allowTrigger = false)
                    signals.readFreshSentryLogMarker(allowTrigger = false)
                    lastLogScanAt = now
                    wasGuarding = true
                    helperLog(
                        TAG,
                        "sentry guarding baseline warning=$warning source=${warningReading.source} propTs=$propTs",
                    )
                }
                wasGuarding = true

                if (accessBlockReason != null) {
                    resetSentryIncident(accessBlockReason)
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
                        resetSentryIncident("risk_exit warning=$warning")
                    }
                } else {
                    riskExitSamples = 0
                }

                val alarmTimestampChanged = signals.consumeSentryAlarmTimestamp(propTs, allowTrigger = true)
                var highRiskLogMarker = ""
                if (now - lastLogScanAt >= SENTRY_LOG_SCAN_MS) {
                    lastLogScanAt = now
                    highRiskLogMarker = signals.readFreshSentryLogMarker(allowTrigger = true)
                }

                if (shouldTriggerSentryFallback(
                        nowMs = now,
                        lastHighRiskAtMs = lastHighRiskAt,
                        alarmTimestampChanged = alarmTimestampChanged,
                        highRiskLogSeen = highRiskLogMarker.isNotBlank(),
                    )
                ) {
                    val reasons = ArrayList<String>(3)
                    reasons += "warning=$warning source=${warningReading.source}"
                    if (alarmTimestampChanged) reasons += "prop_ts=$propTs"
                    if (highRiskLogMarker.isNotBlank()) reasons += "logcat=${highRiskLogMarker.take(80)}"
                    if (isSentryCooldownComplete(now, lastSentryPlaybackAt)) {
                        if (playEvent(LockEvent.Sentry.kind, "sentry ${reasons.joinToString(" | ")}")) {
                            lastSentryPlaybackAt = now
                        }
                    } else {
                        helperLog(
                            TAG,
                            "suppress sentry cooldown remaining=${SENTRY_PLAYBACK_COOLDOWN_MS - (now - lastSentryPlaybackAt)}ms",
                        )
                    }
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
        val updatedAtMs: Long,
    )

    private companion object {
        private const val TAG = "LockSound"
        private const val TURN_FLASH_CONFIRM = 9
        private const val LOCK_CONFIRM_MS = 400L
        private const val LOCK_PENDING_EXPIRE_MS = 3_000L
        private const val EVENT_SUPPRESS_MS = 5_000L
        private const val POWER_SAMPLE_INTERVAL_MS = 300L
        private const val SENTRY_LOG_SCAN_MS = 1_000L
        private const val LOCK_FLASH_WINDOW_MS = 5_000L
        private const val COLD_UNLOCK_MAX_PREV_AGE_MS = 72L * 60L * 60L * 1000L
        private const val LOCK_SOUND_CONFIG_PATH = "/data/local/tmp/wjx_lock_sound.json"
        private const val LOCK_STATE_CACHE_PATH = "/data/local/tmp/wjx_lock_state.txt"
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
                val parts = file.readText().trim().split(",")
                val state = parts.getOrNull(0)?.toIntOrNull() ?: return@runCatching null
                val updatedAt = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                if (state == LOCK_SOUND_DOOR_LOCKED || state == LOCK_SOUND_DOOR_UNLOCKED) {
                    PersistedLockState(state, updatedAt)
                } else {
                    null
                }
            }.getOrNull()
        }

        private fun writePersistedLockState(state: Int, reason: String) {
            if (state != LOCK_SOUND_DOOR_LOCKED && state != LOCK_SOUND_DOOR_UNLOCKED) return
            runCatching {
                File(LOCK_STATE_CACHE_PATH).writeText("$state,${System.currentTimeMillis()}")
            }.onFailure {
                helperLog(TAG, "lock state cache write failed reason=$reason ${throwableSummary(it)}")
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
