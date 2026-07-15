package com.byd.wjx.data.automation

import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the pending state for replaceable asynchronous work.
 *
 * Completion from a cancelled older operation must never clear the pending flag of its newer
 * replacement. Tokens make that ownership explicit without retaining completed Jobs.
 */
internal class LatestOperationGate {
    private val generation = AtomicLong(0L)
    private val activeGeneration = AtomicLong(NO_ACTIVE_OPERATION)

    val pending: Boolean
        get() = activeGeneration.get() != NO_ACTIVE_OPERATION

    fun begin(): Long {
        val token = generation.incrementAndGet()
        activeGeneration.set(token)
        return token
    }

    fun isLatest(token: Long): Boolean = activeGeneration.get() == token

    fun finish(token: Long) {
        activeGeneration.compareAndSet(token, NO_ACTIVE_OPERATION)
    }

    fun cancelAll() {
        generation.incrementAndGet()
        activeGeneration.set(NO_ACTIVE_OPERATION)
    }

    private companion object {
        const val NO_ACTIVE_OPERATION = 0L
    }
}
