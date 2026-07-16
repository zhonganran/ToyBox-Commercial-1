package com.byd.wjx.service

import android.content.Context
import com.byd.wjx.domain.tracker.DriveSessionPersistence
import com.byd.wjx.domain.tracker.PersistedSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPersistence @Inject constructor(
    @ApplicationContext context: Context,
) : DriveSessionPersistence {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lastSavedSessionStartedAt = Long.MIN_VALUE
    private var lastSavedActiveAt = Long.MIN_VALUE

    override fun load(): PersistedSession? {
        if (!prefs.contains(KEY_STARTED_AT)) return null
        val ts = prefs.getLong(KEY_STARTED_AT, 0L)
        val last = prefs.getLong(KEY_LAST_ACTIVE_TS, 0L)
        if (ts <= 0L) return null
        return PersistedSession(sessionStartedAt = ts, lastActiveTs = last)
    }

    @Synchronized
    override fun save(sessionStartedAt: Long, lastActiveTs: Long) {
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
    override fun clear() {
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
