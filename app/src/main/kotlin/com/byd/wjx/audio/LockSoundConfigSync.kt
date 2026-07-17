package com.byd.wjx.audio

import android.content.Context
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.vehicle.CarProfileDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class LockSoundConfigSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
    private val activationManager: ActivationManager,
    private val profileDetector: CarProfileDetector = CarProfileDetector(),
) {
    private val syncMutex = Mutex()
    private var lastSuccessfulJson: String = ""
    private var lastSuccessfulAt: Long = 0L

    suspend fun sync(reason: String): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val settings = LockSoundSettings(context)
            if (clearInvalidSoundPaths(settings)) {
                WjxFileLogger.w(TAG, "cleared invalid WJX sound paths before sync reason=$reason")
            }
            val hailingSettings = ExteriorHailingSettings(context)
            val profile = profileDetector.detect()
            val channel = hailingSettings.effectiveChannel(profile)
            val storedConfig = settings.snapshot()
            val premiumAllowed = vehicleEventPremiumAllowed()
            val config = storedConfig.effectiveForPremium(premiumAllowed)
            val json = buildJson(config, channel)
            val now = System.currentTimeMillis()
            if (json == lastSuccessfulJson && now - lastSuccessfulAt in 0 until DUPLICATE_SYNC_SKIP_MS) {
                WjxFileLogger.i(TAG, "sync skipped duplicate reason=$reason age=${now - lastSuccessfulAt}ms")
                return@withLock true
            }
            val helperReady = runCatching { helperBootstrap.ensureCurrent() }
                .onFailure { WjxFileLogger.w(TAG, "helper ensure failed reason=$reason: ${it.message}", it) }
                .getOrDefault(false)
            if (!helperReady) {
                WjxFileLogger.w(TAG, "sync skipped helper not ready reason=$reason")
                return@withLock false
            }
            var ok = runCatching { helperClient.syncLockSoundConfig(json) }
                .onFailure { WjxFileLogger.w(TAG, "sync transact failed reason=$reason: ${it.message}", it) }
                .getOrDefault(false)
            if (!ok) {
                WjxFileLogger.w(TAG, "sync failed, restart helper and retry reason=$reason")
                val restarted = runCatching { helperBootstrap.restart("lock_sound:$reason") }
                    .onFailure { WjxFileLogger.w(TAG, "helper restart failed reason=$reason: ${it.message}", it) }
                    .getOrDefault(false)
                if (restarted) {
                    ok = runCatching { helperClient.syncLockSoundConfig(json) }
                        .onFailure { WjxFileLogger.w(TAG, "sync retry failed reason=$reason: ${it.message}", it) }
                        .getOrDefault(false)
                }
            }
            if (ok) {
                lastSuccessfulJson = json
                lastSuccessfulAt = System.currentTimeMillis()
            }
            WjxFileLogger.i(
                TAG,
                    "sync reason=$reason ok=$ok channel=$channel controller=${profile.seriesLabel} " +
                    "premiumAllowed=$premiumAllowed " +
                    "playback=playerFull playerGain=1500 pcmGain=2800 " +
                    "unlock=${config.unlockEnabled}/${File(config.unlockPath).name} " +
                    "lock=${config.lockEnabled}/${File(config.lockPath).name} " +
                    "sentry=${config.sentryEnabled}/${File(config.sentryPath).name}",
            )
            ok
        }
    }

    private fun buildJson(
        config: LockSoundConfig,
        channel: ExteriorChannel,
    ): String =
        JSONObject()
            .put("version", 1)
            .put("channel", channel.name)
            .putEvent(LockSoundEvent.Unlock, config.unlockEnabled, config.unlockPath)
            .putEvent(LockSoundEvent.Lock, config.lockEnabled, config.lockPath)
            .putEvent(LockSoundEvent.Sentry, config.sentryEnabled, config.sentryPath)
            .toString()

    private fun JSONObject.putEvent(event: LockSoundEvent, enabled: Boolean, path: String): JSONObject =
        putEvent(event.configKey, enabled, path)

    private fun JSONObject.putEvent(key: String, enabled: Boolean, path: String): JSONObject =
        put(key, JSONObject().put("enabled", enabled).put("path", path))

    private fun LockSoundConfig.effectiveForPremium(premiumAllowed: Boolean): LockSoundConfig =
        if (premiumAllowed) this else copy(
            unlockEnabled = false,
            lockEnabled = false,
            sentryEnabled = false,
        )

    private fun clearInvalidSoundPaths(settings: LockSoundSettings): Boolean {
        val config = settings.snapshot()
        var changed = false
        LockSoundEvent.entries.forEach { event ->
            val path = config.path(event)
            if ((config.enabled(event) || path.isNotBlank()) && !SoundLibrary.isManagedSoundPath(path)) {
                settings.setEnabled(event, false)
                settings.setSoundPath(event, "")
                changed = true
            }
        }
        return changed
    }

    private suspend fun vehicleEventPremiumAllowed(): Boolean {
        if (activationManager.canUse(PremiumFeature.VEHICLE_EVENT_SOUNDS)) return true
        val refreshed = runCatching {
            activationManager.refreshIfNeeded(forceAfterMs = ACTIVATION_RESTORE_REFRESH_MS)
        }.onFailure {
            WjxFileLogger.w(TAG, "activation restore refresh failed: ${it.message}", it)
        }.getOrNull()
        return refreshed?.let { it.activated || it.trialActive } == true
    }

    companion object {
        private const val TAG = "LockSoundSync"
        private const val DUPLICATE_SYNC_SKIP_MS = 20_000L
        private const val ACTIVATION_RESTORE_REFRESH_MS = 30_000L
    }
}
