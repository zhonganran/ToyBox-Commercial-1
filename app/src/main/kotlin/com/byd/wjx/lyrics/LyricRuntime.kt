package com.byd.wjx.lyrics

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.activation.PremiumFeature
import com.byd.wjx.data.autoservice.AdbOnDeviceClient
import com.byd.wjx.data.foreground.ForegroundAppMonitor
import com.byd.wjx.data.vehicle.HelperBootstrap
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.util.HomePackageResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

@Singleton
class LyricRuntime @Inject constructor(
    @ApplicationContext context: Context,
    private val adb: AdbOnDeviceClient,
    private val helperBootstrap: HelperBootstrap,
    private val helperClient: HelperClient,
    private val activationManager: ActivationManager,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    okHttpClient: OkHttpClient,
) {
    private val appContext = context.applicationContext
    private val settings = LyricSettings(appContext)
    private val repository = LyricRepository(okHttpClient)
    private val desktopOverlay = DesktopLyricOverlayController(appContext, settings)
    private val clusterOverlay = ClusterLyricOverlayController(appContext, settings)
    private val poller = MusicPlaybackPoller(adb)
    private val homePackages by lazy { resolveHomePackages() }

    private var scope: CoroutineScope? = null
    private var latestPlayback = MusicPlaybackSnapshot.EMPTY
    private var latestLyrics = LyricsResult()
    private var latestCameraActive = false
    private var lastSongKey = ""
    private var trackChangeHoldUntilMs = 0L
    private var lastDesktopFrame: LyricFrame? = null
    private var lastClusterFrame: LyricFrame? = null
    private var suppressNextFrameAnimation = false
    private var lyricLoadJob: Job? = null
    private var grantAttempted = false
    private var cachedLyricsAllowed = false
    private var lastLyricsAllowedCheckAt = Long.MIN_VALUE

    fun start(cameraActive: StateFlow<Boolean>) {
        if (scope?.isActive == true) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        WjxFileLogger.i(TAG, "start")

        newScope.launch(Dispatchers.IO) { ensurePermissionsBestEffort() }
        newScope.launch {
            cameraActive.collectLatest { active ->
                latestCameraActive = active
                if (active) hideOverlays(hard = true)
            }
        }
        newScope.launch {
            MusicPlaybackStore.state.collectLatest { snapshot ->
                if (!snapshot.hasSong) {
                    latestPlayback = snapshot
                    latestLyrics = LyricsResult()
                    lastSongKey = ""
                    trackChangeHoldUntilMs = 0L
                    lyricLoadJob?.cancel()
                    lyricLoadJob = null
                    return@collectLatest
                }
                val previous = latestPlayback
                val sameSong = snapshot.hasSong && previous.hasSong && snapshot.isSameSongAs(previous)
                val trackChanged = snapshot.hasSong && previous.hasSong && !sameSong
                if (sameSong) {
                    val expected = previous.projectedPositionMs(snapshot.anchorElapsedMs)
                    val drift = snapshot.positionMs - expected
                    if (abs(drift) > POSITION_DRIFT_RESET_MS) {
                        suppressNextFrameAnimation = true
                        WjxFileLogger.i(TAG, "lyrics position resync drift=${drift}ms title=${snapshot.title.take(48)}")
                    }
                } else if (trackChanged) {
                    trackChangeHoldUntilMs = SystemClock.elapsedRealtime() + TRACK_CHANGE_HOLD_MS
                    suppressNextFrameAnimation = true
                    WjxFileLogger.i(TAG, "lyrics track changed hold=${TRACK_CHANGE_HOLD_MS}ms title=${snapshot.title.take(48)}")
                }
                latestPlayback = snapshot
                val key = snapshot.songKey()
                if (key != lastSongKey) {
                    lastSongKey = key
                    latestLyrics = LyricsResult(key)
                    lyricLoadJob?.cancel()
                    lyricLoadJob = launch(Dispatchers.IO) {
                        val loaded = runCatching { repository.load(snapshot) }
                            .onFailure {
                                if (it !is CancellationException) {
                                    WjxFileLogger.w(
                                        TAG,
                                        "lyrics load failed key=${logSafeKey(key)}: ${it.message}",
                                        it,
                                    )
                                }
                            }
                            .getOrDefault(LyricsResult(key))
                        withContext(Dispatchers.Main.immediate) {
                            if (lastSongKey == loaded.key) {
                                latestLyrics = loaded
                                trackChangeHoldUntilMs = 0L
                                suppressNextFrameAnimation = true
                                WjxFileLogger.i(
                                    TAG,
                                    "lyrics loaded key=${logSafeKey(loaded.key)} lines=${loaded.lines.size}",
                                )
                            }
                        }
                    }
                }
            }
        }
        newScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (MusicPlaybackStore.shouldPollFallback()) {
                    runCatching { poller.pollOnce()?.let(MusicPlaybackStore::publish) }
                        .onFailure { WjxFileLogger.w(TAG, "ADB media_session poll failed: ${it.message}") }
                }
                val delayMs = if (latestPlayback.playing) 2_000L else if (latestPlayback.hasSong) 8_000L else 10_000L
                delay(delayMs)
            }
        }
        newScope.launch {
            while (isActive) {
                renderTick()
                delay(RENDER_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        WjxFileLogger.i(TAG, "stop")
        scope?.cancel()
        scope = null
        lyricLoadJob?.cancel()
        lyricLoadJob = null
        hideOverlays(hard = true)
    }

    fun refresh() {
        desktopOverlay.refreshPosition()
        clusterOverlay.refreshPosition()
    }

    private suspend fun ensurePermissionsBestEffort() {
        if (grantAttempted) return
        grantAttempted = true
        runCatching {
            adb.connect()
            val listenerGranted = adb.grantMusicNotificationListenerAccess()
            WjxFileLogger.i(TAG, "notification listener grant=$listenerGranted")
            NotificationListenerService.requestRebind(
                ComponentName(appContext, MusicNotificationListenerService::class.java),
            )
        }.onFailure {
            WjxFileLogger.w(TAG, "notification listener grant failed: ${it.message}")
        }
        runCatching {
            if (helperBootstrap.ensureCurrent()) {
                val overlayGranted = helperClient.grantOverlayPermission()
                WjxFileLogger.i(TAG, "overlay/project-media grant=$overlayGranted")
            }
        }.onFailure {
            WjxFileLogger.w(TAG, "overlay/project-media grant failed: ${it.message}")
        }
    }

    private fun renderTick() {
        val playback = latestPlayback
        val lyricsAllowed = canUseLyrics()
        val desktopVisible = lyricsAllowed && settings.desktopEnabled &&
            (settings.allowOnOtherApps || isHomeForeground())
        val clusterVisible = lyricsAllowed && settings.clusterEnabled
        val shouldHideAll = latestCameraActive ||
            !playback.playing ||
            !playback.hasSong ||
            (!desktopVisible && !clusterVisible)
        if (shouldHideAll || !Settings.canDrawOverlays(appContext)) {
            hideOverlays(hard = latestCameraActive || !Settings.canDrawOverlays(appContext))
            return
        }

        val animate = !suppressNextFrameAnimation
        suppressNextFrameAnimation = false
        if (desktopVisible) {
            val frame = buildFrame(playback, settings.desktopSyncOffsetMs, lastDesktopFrame)
            desktopOverlay.update(frame, animate)
            lastDesktopFrame = frame
        } else {
            desktopOverlay.hideSoft()
        }
        if (clusterVisible) {
            val frame = buildFrame(playback, settings.clusterSyncOffsetMs, lastClusterFrame)
            clusterOverlay.update(frame, animate)
            lastClusterFrame = frame
        } else {
            clusterOverlay.hide()
        }
    }

    private fun canUseLyrics(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastLyricsAllowedCheckAt == Long.MIN_VALUE || now - lastLyricsAllowedCheckAt >= ACTIVATION_CHECK_INTERVAL_MS) {
            cachedLyricsAllowed = activationManager.canUse(PremiumFeature.DESKTOP_LYRICS)
            lastLyricsAllowedCheckAt = now
        }
        return cachedLyricsAllowed
    }

    private fun buildFrame(
        playback: MusicPlaybackSnapshot,
        syncOffsetMs: Int,
        fallbackFrame: LyricFrame?,
    ): LyricFrame {
        val immediate = playback.realtimeLyrics.filter { LyricTextFilter.isUsable(it) }
        val lines = latestLyrics.lines
        if (lines.isEmpty()) {
            if (immediate.isEmpty() && SystemClock.elapsedRealtime() < trackChangeHoldUntilMs) {
                fallbackFrame?.let { return it }
            }
            return when {
                immediate.size >= 3 -> LyricFrame(
                    previous = immediate[0],
                    current = immediate[1],
                    next = immediate[2],
                    title = playback.title,
                    artist = playback.artist,
                )
                immediate.size == 2 -> LyricFrame(
                    previous = playback.artist,
                    current = immediate[0],
                    next = immediate[1],
                    title = playback.title,
                    artist = playback.artist,
                )
                immediate.size == 1 -> LyricFrame(
                    previous = playback.artist,
                    current = immediate[0],
                    title = playback.title,
                    artist = playback.artist,
                )
                else -> LyricFrame(
                    previous = playback.artist,
                    current = playback.title.ifBlank { "正在播放" },
                    title = playback.title,
                    artist = playback.artist,
                )
            }
        }
        val position = (playback.projectedPositionMs() + syncOffsetMs).coerceAtLeast(0L)
        val index = lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0)
        return LyricFrame(
            previous2 = lines.getOrNull(index - 2)?.text.orEmpty(),
            previous = lines.getOrNull(index - 1)?.text.orEmpty(),
            current = lines.getOrNull(index)?.text ?: playback.title,
            next = lines.getOrNull(index + 1)?.text.orEmpty(),
            next2 = lines.getOrNull(index + 2)?.text.orEmpty(),
            title = playback.title,
            artist = playback.artist,
        )
    }

    private fun hideOverlays(hard: Boolean = false) {
        if (hard) {
            desktopOverlay.hide()
        } else {
            desktopOverlay.hideSoft()
        }
        clusterOverlay.hide()
    }

    private fun isHomeForeground(): Boolean {
        val foreground = foregroundAppMonitor.foreground.value
        val pkg = foreground.packageName?.takeIf(String::isNotBlank) ?: return false
        return pkg != appContext.packageName &&
            HomePackageResolver.isHomePackage(pkg, homePackages) &&
            !HomePackageResolver.isLauncherSubpage(foreground.className)
    }

    private fun resolveHomePackages(): Set<String> {
        return HomePackageResolver.resolve(appContext)
    }

    private fun logSafeKey(value: String): String =
        value.replace('\u0000', '|').take(48)

    companion object {
        private const val TAG = "LyricRuntime"
        private const val RENDER_INTERVAL_MS = 200L
        private const val ACTIVATION_CHECK_INTERVAL_MS = 2_000L
        private const val TRACK_CHANGE_HOLD_MS = 2_500L
        private const val POSITION_DRIFT_RESET_MS = 2_500L
    }
}
