package com.byd.wjx.data.vehicle

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelperProcessGateTest {

    @Test
    fun `restart waits for active binder operation`() = runTest {
        val operationEntered = CompletableDeferred<Unit>()
        val releaseOperation = CompletableDeferred<Unit>()
        val restartEntered = CompletableDeferred<Unit>()

        val operation = async {
            HelperProcessGate.withOperation {
                operationEntered.complete(Unit)
                releaseOperation.await()
            }
        }
        operationEntered.await()

        val restart = async {
            HelperProcessGate.withRestart {
                restartEntered.complete(Unit)
            }
        }
        runCurrent()
        assertFalse(restartEntered.isCompleted)

        releaseOperation.complete(Unit)
        operation.await()
        restart.await()
        assertTrue(restartEntered.isCompleted)
    }

    @Test
    fun `binder operation waits while restart owns gate`() = runTest {
        val restartEntered = CompletableDeferred<Unit>()
        val releaseRestart = CompletableDeferred<Unit>()
        val operationEntered = CompletableDeferred<Unit>()

        val restart = async {
            HelperProcessGate.withRestart {
                restartEntered.complete(Unit)
                releaseRestart.await()
            }
        }
        restartEntered.await()

        val operation = async {
            HelperProcessGate.withOperation {
                operationEntered.complete(Unit)
            }
        }
        runCurrent()
        assertFalse(operationEntered.isCompleted)

        releaseRestart.complete(Unit)
        restart.await()
        operation.await()
        assertTrue(operationEntered.isCompleted)
    }

    @Test
    fun `cancelled restart releases waiting operations`() = runTest {
        val operationEntered = CompletableDeferred<Unit>()
        val releaseOperation = CompletableDeferred<Unit>()

        val operation = async {
            HelperProcessGate.withOperation {
                operationEntered.complete(Unit)
                releaseOperation.await()
            }
        }
        operationEntered.await()

        val restart = async {
            HelperProcessGate.withRestart { error("restart block must not run") }
        }
        runCurrent()
        restart.cancelAndJoin()

        val secondOperationEntered = CompletableDeferred<Unit>()
        val secondOperation = async {
            HelperProcessGate.withOperation {
                secondOperationEntered.complete(Unit)
            }
        }
        runCurrent()
        assertTrue(secondOperationEntered.isCompleted)

        releaseOperation.complete(Unit)
        operation.await()
        secondOperation.await()
    }
}
