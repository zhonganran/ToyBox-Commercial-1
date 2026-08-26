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
    private val configStore = LockSoundConfigStore()
    private val stateStore = LockSoundStateStore()
    private val sentryReservationStore = SentryPlaybackReservationStore()
    @Volatile private var config: LockSoundRuntimeConfig = configStore.read()
    @Volatile private var persistedLockState: PersistedLockState? = stateStore.read()
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
    private val lockMonitor = WjxLockSoundMonitor(
        signals = signals,
        playback = playback,
        configProvider = { config },
        stateStore = stateStore,
        initialPersistedState = persistedLockState,
        onStatePersisted = { persistedLockState = it },
        playEvent = ::playEvent,
    )
    fun start() {
        if (!started.compareAndSet(false, true)) return
        Thread({ lockMonitor.run() }, "WJX-LockSoundPoll").apply { isDaemon = true; start() }
        Thread({ sentryLoop() }, "WJX-SentrySoundPoll").apply { isDaemon = true; start() }
        helperLog(TAG, "runtime started config=${config.summary()}")
    }

    fun syncConfig(json: String): Boolean {
        if (json.isBlank()) return false
        val incoming = JSONObject(json)
        val channel = parseLockSoundChannel(incoming.optString("channel", ExteriorChannel.Channel1.name))
        val next = LockSoundRuntimeConfig(
            channel = channel,
            unlock = copyEventConfig(incoming.optJSONObject(LOCK_SOUND_KEY_UNLOCK), LOCK_SOUND_UNLOCK_PATH, LOCK_SOUND_UNLOCK_PCM_PATH, LOCK_SOUND_KEY_UNLOCK),
            lock = copyEventConfig(incoming.optJSONObject(LOCK_SOUND_KEY_LOCK), LOCK_SOUND_LOCK_PATH, LOCK_SOUND_LOCK_PCM_PATH, LOCK_SOUND_KEY_LOCK),
            sentry = copyEventConfig(incoming.optJSONObject(LOCK_SOUND_KEY_SENTRY), LOCK_SOUND_SENTRY_PATH, LOCK_SOUND_SENTRY_PCM_PATH, LOCK_SOUND_KEY_SENTRY),
        )
        configStore.write(next)
        config = next
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

    fun playSentryEventForBinder(reason: String): Boolean {
        if (!signals.isSentryGuarding()) {
            helperLog(TAG, "suppress external sentry not_guarding reason=$reason")
            return false
        }
        val warning = signals.readSentryWarning()
        val alarmTimestamp = signals.readSentryAlarmTimestamp()
        val alarmTimestampChanged = signals.consumeSentryAlarmTimestamp(
            alarmTimestamp,
            allowTrigger = true,
        )
        val highRiskLogMarker = signals.readFreshSentryLogMarker(allowTrigger = true)
        if (!shouldAcceptExternalSentryTrigger(
                warningLevel = warning.level,
                alarmTimestampChanged = alarmTimestampChanged,
                highRiskLogSeen = highRiskLogMarker.isNotBlank(),
            )
        ) {
            helperLog(
                TAG,
                "suppress external sentry unconfirmed warning=${warning.level} " +
                    "source=${warning.source} propChanged=$alarmTimestampChanged " +
                    "highRiskLog=${highRiskLogMarker.isNotBlank()} reason=$reason",
            )
            return false
        }
        val evidence = buildList {
            add("warning=${warning.level} source=${warning.source}")
            if (alarmTimestampChanged) add("prop_ts=$alarmTimestamp")
            if (highRiskLogMarker.isNotBlank()) add("logcat=${highRiskLogMarker.take(80)}")
        }
        return playRealSentryEvent(
            "$reason verified ${evidence.joinToString(" | ")}",
            blocking = true,
        )
    }

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
        val previous = sentryReservationStore.readReservedAt()
        if (!isSentryCooldownComplete(now, previous)) {
            helperLog(TAG, "suppress sentry cooldown remaining=${SENTRY_PLAYBACK_COOLDOWN_MS - (now - previous)}ms reason=$reason")
            return@synchronized false
        }
        val reserved = runCatching {
            sentryReservationStore.write(now, reason)
            true
        }.getOrElse {
            helperLog(TAG, "sentry reservation write failed ${throwableSummary(it)}")
            false
        }
        if (reserved) helperLog(TAG, "reserve sentry cooldown reason=$reason")
        reserved
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

    private companion object {
        private const val TAG = "LockSound"
        private const val SENTRY_LOG_SCAN_MS = 1_000L
        private fun sleepQuietly(ms: Long) {
            runCatching { Thread.sleep(ms) }
        }
    }
}
