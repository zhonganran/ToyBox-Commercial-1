package com.byd.wjx.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the current widget-session anchor across process restarts: the ignition-on
 * timestamp and last-active heartbeat.
 *
 * Cleared on ignition-off (powerState 0 + 10 sec idle) so the next session
 * gets a fresh sessionStartedAt.
 */
data class PersistedSession(
    val sessionStartedAt: Long,
    val lastActiveTs: Long,
) {
    /**
     * Prefs may hold a session that *should* have been idle-closed but wasn't
     * (process killed inside the grace window, or DiLink rebooted before idle-close
     * could fire). If the last-active tick was longer ago than the idle-close
     * threshold, treat the session as dead — otherwise the widget anchor is
     * ancient and trip time renders as a very large duration on the next ignition-on.
     */
    fun isStale(now: Long, idleCloseMs: Long): Boolean {
        return now - lastActiveTs >= idleCloseMs
    }
}

@Singleton
class SessionPersistence @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lastSavedSessionStartedAt = Long.MIN_VALUE
    private var lastSavedActiveAt = Long.MIN_VALUE

    fun load(): PersistedSession? {
        if (!prefs.contains(KEY_STARTED_AT)) return null
        val ts = prefs.getLong(KEY_STARTED_AT, 0L)
        val last = prefs.getLong(KEY_LAST_ACTIVE_TS, 0L)
        if (ts <= 0L) return null
        return PersistedSession(sessionStartedAt = ts, lastActiveTs = last)
    }

    @Synchronized
    fun save(sessionStartedAt: Long, lastActiveTs: Long) {
        val sameSession = sessionStartedAt == lastSavedSessionStartedAt
        val forwardWithinInterval = lastActiveTs >= lastSavedActiveAt &&
            lastActiveTs - lastSavedActiveAt < MIN_HEARTBEAT_WRITE_INTERVAL_MS
        if (sameSession && forwardWithinInterval) return
        prefs.edit()
            .putLong(KEY_STARTED_AT, sessionStartedAt)
            .putLong(KEY_LAST_ACTIVE_TS, lastActiveTs)
            .apply()
        lastSavedSessionStartedAt = sessionStartedAt
        lastSavedActiveAt = lastActiveTs
    }

    @Synchronized
    fun clear() {
        prefs.edit()
            .remove(KEY_STARTED_AT)
            .remove(KEY_LAST_ACTIVE_TS)
            .apply()
        lastSavedSessionStartedAt = Long.MIN_VALUE
        lastSavedActiveAt = Long.MIN_VALUE
    }

    companion object {
        private const val PREFS_NAME = "wjx_widget_session"
        private const val KEY_STARTED_AT = "session_started_at"
        private const val KEY_LAST_ACTIVE_TS = "last_active_ts"
        private const val MIN_HEARTBEAT_WRITE_INTERVAL_MS = 3_000L
    }
}
