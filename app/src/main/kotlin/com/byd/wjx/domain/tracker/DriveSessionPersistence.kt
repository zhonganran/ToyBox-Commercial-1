package com.byd.wjx.domain.tracker

/** Persisted anchor used to resume an ignition-on session after process restart. */
data class PersistedSession(
    val sessionStartedAt: Long,
    val lastActiveTs: Long,
) {
    fun isStale(now: Long, idleCloseMs: Long): Boolean =
        now - lastActiveTs >= idleCloseMs
}

/** Domain boundary; Android storage details stay behind the service implementation. */
interface DriveSessionPersistence {
    fun load(): PersistedSession?
    fun save(sessionStartedAt: Long, lastActiveTs: Long)
    fun clear()
}
