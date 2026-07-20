package com.byd.wjx.lyrics

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.byd.wjx.log.WjxFileLogger

internal data class NotificationPlaybackAnchor(
    val positionMs: Long,
    val anchorElapsedMs: Long,
)

internal fun resolveNotificationPlaybackAnchor(
    previous: MusicPlaybackSnapshot,
    sameSong: Boolean,
    statePositionMs: Long?,
    stateUpdatedAtMs: Long?,
    nowElapsedMs: Long,
): NotificationPlaybackAnchor {
    val validStatePosition = statePositionMs?.takeIf { it >= 0L }
    val canUseControllerPosition = sameSong || !previous.hasSong
    if (canUseControllerPosition && validStatePosition != null) {
        return NotificationPlaybackAnchor(
            positionMs = validStatePosition,
            anchorElapsedMs = stateUpdatedAtMs?.takeIf { it > 0L } ?: nowElapsedMs,
        )
    }
    if (sameSong) {
        return NotificationPlaybackAnchor(
            positionMs = previous.projectedPositionMs(nowElapsedMs),
            anchorElapsedMs = nowElapsedMs,
        )
    }
    return NotificationPlaybackAnchor(positionMs = 0L, anchorElapsedMs = nowElapsedMs)
}

internal fun shouldAcceptNotificationIdentity(
    previous: MusicPlaybackSnapshot,
    packageName: String,
    title: String,
    artist: String,
): Boolean {
    if (!previous.hasSong || !previous.playing || previous.packageName != packageName) return true
    return MusicIdentity.isSameSong(previous.title, previous.artist, title, artist)
}

class MusicNotificationListenerService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val controllers = linkedMapOf<String, MediaController>()
    private val callbacks = linkedMapOf<String, MediaController.Callback>()
    private var mediaSessionManager: MediaSessionManager? = null
    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { active ->
        syncSessions(active.orEmpty())
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            scanSessions()
            handler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        mediaSessionManager = manager
        val component = ComponentName(this, MusicNotificationListenerService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, component, handler)
        }.onFailure {
            WjxFileLogger.w(TAG, "active-session listener registration failed: ${it.message}")
        }
        WjxFileLogger.i(TAG, "listener connected")
        handler.post(scanRunnable)
    }

    override fun onListenerDisconnected() {
        WjxFileLogger.w(TAG, "listener disconnected")
        disconnectSessionManager()
        unregisterAll()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        disconnectSessionManager()
        unregisterAll()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!SupportedMusicPackages.names.contains(sbn.packageName)) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title").orEmpty().trim()
        if (!MusicIdentity.hasUsableTitle(title)) return
        val artist = extras.getCharSequence("android.text")?.toString().orEmpty().trim()
        val controller = controllers[sbn.packageName]
        controller?.let { snapshotFromController(it) }?.let {
            MusicPlaybackStore.publish(it)
            return
        }
        val previous = MusicPlaybackStore.state.value
        if (!shouldAcceptNotificationIdentity(previous, sbn.packageName, title, artist)) return
        val state = controller?.playbackState
        val sameSong = previous.packageName == sbn.packageName &&
            MusicIdentity.isSameSong(previous.title, previous.artist, title, artist)
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val playbackAnchor = resolveNotificationPlaybackAnchor(
            previous = previous,
            sameSong = sameSong,
            statePositionMs = state?.position,
            stateUpdatedAtMs = state?.lastPositionUpdateTime,
            nowElapsedMs = nowElapsedMs,
        )
        MusicPlaybackStore.publish(
            MusicPlaybackSnapshot(
                title = title,
                artist = artist,
                durationMs = if (sameSong) previous.durationMs else 0L,
                positionMs = playbackAnchor.positionMs,
                playing = state?.state == PlaybackState.STATE_PLAYING ||
                    (state == null && sameSong && previous.playing),
                packageName = sbn.packageName,
                mediaUri = if (sameSong) previous.mediaUri else null,
                metadataLyrics = if (sameSong) previous.metadataLyrics else null,
                timedLyrics = if (sameSong) previous.timedLyrics else null,
                realtimeLyrics = if (sameSong) previous.realtimeLyrics else emptyList(),
                anchorElapsedMs = playbackAnchor.anchorElapsedMs,
                playbackSpeed = state?.playbackSpeed?.takeIf { it > 0f }
                    ?: previous.playbackSpeed.takeIf { sameSong && it > 0f }
                    ?: 1f,
                source = MusicPlaybackSource.Notification,
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (SupportedMusicPackages.names.contains(sbn.packageName)) {
            handler.postDelayed({ confirmStoppedIfNoActiveSession(sbn.packageName) }, 500L)
        }
    }

    private fun scanSessions() {
        val manager = mediaSessionManager ?: return
        val component = ComponentName(this, MusicNotificationListenerService::class.java)
        val active = runCatching { manager.getActiveSessions(component) }
            .onFailure { WjxFileLogger.w(TAG, "getActiveSessions failed: ${it.message}") }
            .getOrDefault(emptyList())
        syncSessions(active)
    }

    private fun syncSessions(activeSessions: List<MediaController>) {
        val active = activeSessions.filter { SupportedMusicPackages.names.contains(it.packageName) }

        val activePackages = active.map { it.packageName }.toSet()
        controllers.keys.toList()
            .filter { it !in activePackages }
            .forEach(::unregister)

        active.forEach(::register)
        active.mapNotNull(::snapshotFromController)
            .sortedWith(compareByDescending<MusicPlaybackSnapshot> { it.playing }.thenBy { it.title.isBlank() })
            .firstOrNull { it.hasSong }
            ?.let(MusicPlaybackStore::publish)
    }

    private fun disconnectSessionManager() {
        handler.removeCallbacks(scanRunnable)
        mediaSessionManager?.let { manager ->
            runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        }
        mediaSessionManager = null
    }

    private fun register(controller: MediaController) {
        val pkg = controller.packageName
        if (controllers.containsKey(pkg)) return
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                handler.post { snapshotFromController(controller, metadata, controller.playbackState)?.let(MusicPlaybackStore::publish) }
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                handler.post { snapshotFromController(controller, controller.metadata, state)?.let(MusicPlaybackStore::publish) }
            }

            override fun onSessionDestroyed() {
                handler.post { unregister(pkg) }
            }
        }
        runCatching { controller.registerCallback(callback, handler) }
            .onSuccess {
                controllers[pkg] = controller
                callbacks[pkg] = callback
                WjxFileLogger.i(TAG, "registered media controller $pkg")
            }
            .onFailure { WjxFileLogger.w(TAG, "register controller failed pkg=$pkg: ${it.message}") }
    }

    private fun unregister(packageName: String) {
        val controller = controllers.remove(packageName)
        val callback = callbacks.remove(packageName)
        if (controller != null && callback != null) {
            runCatching { controller.unregisterCallback(callback) }
        }
    }

    private fun unregisterAll() {
        controllers.keys.toList().forEach(::unregister)
    }

    private fun confirmStoppedIfNoActiveSession(packageName: String) {
        val manager = mediaSessionManager ?: return
        val component = ComponentName(this, MusicNotificationListenerService::class.java)
        val stillActive = runCatching {
            manager.getActiveSessions(component).any { it.packageName == packageName }
        }.onFailure {
            WjxFileLogger.w(TAG, "confirm active sessions failed: ${it.message}")
        }.getOrDefault(true)
        if (!stillActive) {
            MusicPlaybackStore.clearIfPackage(packageName)
            WjxFileLogger.i(TAG, "confirmed music stop package=$packageName")
        }
    }

    private fun snapshotFromController(
        controller: MediaController,
        metadata: MediaMetadata? = controller.metadata,
        playbackState: PlaybackState? = controller.playbackState,
    ): MusicPlaybackSnapshot? {
        val previous = MusicPlaybackStore.state.value
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: previous.takeIf { it.packageName == controller.packageName }?.title
            ?: return null
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: previous.takeIf { it.packageName == controller.packageName }?.artist
            ?: ""
        val sameSong = previous.packageName == controller.packageName &&
            MusicIdentity.isSameSong(previous.title, previous.artist, title, artist)
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }
            ?: previous.takeIf { sameSong }?.durationMs
            ?: 0L
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val playbackAnchor = resolveNotificationPlaybackAnchor(
            previous = previous,
            sameSong = sameSong,
            statePositionMs = playbackState?.position,
            stateUpdatedAtMs = playbackState?.lastPositionUpdateTime,
            nowElapsedMs = nowElapsedMs,
        )
        val playing = playbackState?.state == PlaybackState.STATE_PLAYING ||
            (playbackState == null && sameSong && previous.playing)
        val mediaUri = metadata?.firstString(MEDIA_URI_KEYS)
            ?: previous.takeIf { sameSong }?.mediaUri
        val metadataLyrics = metadata?.getString(BYD_LYRICS)
        val timedLyrics = parseBydTimedLyrics(metadataLyrics)
        val realtimeLines = playbackState?.extras?.readRealtimeLyricLines().orEmpty()
        return MusicPlaybackSnapshot(
            title = title,
            artist = artist,
            durationMs = duration,
            positionMs = playbackAnchor.positionMs,
            playing = playing,
            packageName = controller.packageName,
            mediaUri = mediaUri,
            metadataLyrics = metadataLyrics,
            timedLyrics = timedLyrics,
            realtimeLyrics = realtimeLines,
            anchorElapsedMs = playbackAnchor.anchorElapsedMs,
            playbackSpeed = playbackState?.playbackSpeed?.takeIf { it > 0f }
                ?: previous.playbackSpeed.takeIf { sameSong && it > 0f }
                ?: 1f,
            source = MusicPlaybackSource.MediaController,
        )
    }

    private fun MediaMetadata.firstString(keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key ->
            getString(key)?.trim()?.takeIf { it.isNotBlank() }
        }

    private fun Bundle.readRealtimeLyricLines(): List<String> =
        listOfNotNull(
            getString("lrc_line_1"),
            getString("lrc_line_2"),
            getString("lrc_line_3"),
        ).map { it.trim() }.filter { LyricTextFilter.isUsable(it) }

    private fun parseBydTimedLyrics(raw: String?): List<LyricLine>? =
        LyricRepository.parseBydMetadataLyrics(raw, "media_metadata")?.lines

    companion object {
        private const val TAG = "MusicLyricsListener"
        // MediaSession callbacks provide immediate updates. This low-frequency scan only repairs
        // missed active-session callbacks on vendor ROMs.
        private const val SCAN_INTERVAL_MS = 10_000L
        private const val BYD_LYRICS = "com.byd.mediacenter.metadata.LYRICS"
        private val MEDIA_URI_KEYS = listOf(
            MediaMetadata.METADATA_KEY_MEDIA_URI,
            "com.byd.mediacenter.metadata.MEDIA_URI",
            "com.byd.mediacenter.metadata.FILE_PATH",
            "com.byd.mediacenter.metadata.PATH",
        )
    }
}
