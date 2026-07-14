package com.byd.wjx.data.vehicle

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Prevents helper process replacement from racing active Binder transactions. */
internal object HelperProcessGate {
    private val stateMutex = Mutex()
    private var activeOperations = 0
    private var restartInProgress = false
    private var operationsIdle = completedSignal()
    private var restartFinished = completedSignal()

    suspend fun <T> withOperation(block: suspend () -> T): T {
        while (true) {
            val waitForRestart = stateMutex.withLock {
                if (restartInProgress) {
                    restartFinished
                } else {
                    activeOperations++
                    if (activeOperations == 1) operationsIdle = CompletableDeferred()
                    null
                }
            }
            if (waitForRestart == null) break
            waitForRestart.await()
        }

        try {
            return block()
        } finally {
            stateMutex.withLock {
                activeOperations--
                if (activeOperations == 0) operationsIdle.complete(Unit)
            }
        }
    }

    suspend fun <T> withRestart(block: suspend () -> T): T {
        while (true) {
            val acquisition = stateMutex.withLock {
                if (restartInProgress) {
                    RestartAcquisition(acquired = false, signal = restartFinished)
                } else {
                    restartInProgress = true
                    restartFinished = CompletableDeferred()
                    RestartAcquisition(
                        acquired = true,
                        signal = operationsIdle.takeIf { activeOperations > 0 },
                    )
                }
            }
            if (acquisition.acquired) {
                try {
                    acquisition.signal?.await()
                } catch (error: Throwable) {
                    releaseRestart()
                    throw error
                }
                break
            }
            acquisition.signal?.await()
        }

        try {
            return block()
        } finally {
            releaseRestart()
        }
    }

    private suspend fun releaseRestart() {
        stateMutex.withLock {
            restartInProgress = false
            restartFinished.complete(Unit)
        }
    }

    private data class RestartAcquisition(
        val acquired: Boolean,
        val signal: CompletableDeferred<Unit>?,
    )

    private fun completedSignal(): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().apply { complete(Unit) }
}
