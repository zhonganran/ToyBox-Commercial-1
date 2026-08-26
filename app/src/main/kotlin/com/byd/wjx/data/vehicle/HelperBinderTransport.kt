package com.byd.wjx.data.vehicle

import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import com.byd.wjx.helper.HelperBinderProtocol
import com.byd.wjx.log.WjxFileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Owns Binder lookup, caching, retry, timeout, and read/write concurrency lanes. */
internal class HelperBinderTransport(
    private val binderResolver: () -> IBinder?,
) {
    private val readMutex = Mutex()
    private val commandMutex = Mutex()

    @Volatile
    private var cached: IBinder? = null

    suspend fun <T> transactParsed(
        code: Int,
        writeArgs: (Parcel) -> Unit,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        parse: (Parcel) -> T?,
    ): T? = HelperProcessGate.withOperation {
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                mutexFor(code).withLock {
                    repeat(2) { attempt ->
                        val binder = ensureBinder() ?: return@withLock null
                        val data = Parcel.obtain()
                        val reply = Parcel.obtain()
                        try {
                            data.writeInterfaceToken(HelperBinderProtocol.DESCRIPTOR)
                            writeArgs(data)
                            if (!binder.transact(code, data, reply, 0)) return@withLock null
                            return@withLock parse(reply)
                        } catch (error: DeadObjectException) {
                            cached = null
                            if (attempt == 1) {
                                WjxFileLogger.w(
                                    TAG,
                                    "binder dead after retry: ${error.message}",
                                    error,
                                )
                                return@withLock null
                            }
                        } catch (error: Exception) {
                            WjxFileLogger.w(
                                TAG,
                                "transact failed code=$code: ${error.message}",
                                error,
                            )
                            return@withLock null
                        } finally {
                            data.recycle()
                            reply.recycle()
                        }
                    }
                    null
                }
            }
        }
    }

    private fun mutexFor(code: Int): Mutex = when (code) {
        HelperBinderProtocol.TX_READ,
        HelperBinderProtocol.TX_READ_BATCH,
        HelperBinderProtocol.TX_PING,
        -> readMutex
        else -> commandMutex
    }

    private fun ensureBinder(): IBinder? {
        cached?.takeIf { it.isBinderAlive }?.let { return it }
        return binderResolver()?.also { cached = it }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2_000L
        private const val TAG = "HelperClient"
    }
}
