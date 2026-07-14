package com.byd.wjx.service

/**
 * Debounces system wake events. DiLink can emit boot, user, screen, ACC and
 * network broadcasts in a tight burst; one full-stack kick is enough.
 */
object StartupThrottle {
    private const val MIN_RESTART_INTERVAL_MS = 5_000L
    private val lock = Any()
    private var lastStartMs = 0L

    fun shouldStart(): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (now - lastStartMs < MIN_RESTART_INTERVAL_MS) return@synchronized false
        lastStartMs = now
        true
    }
}
