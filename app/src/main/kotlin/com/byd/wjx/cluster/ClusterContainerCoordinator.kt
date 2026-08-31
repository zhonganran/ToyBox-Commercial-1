package com.byd.wjx.cluster

import android.content.Context
import android.content.SharedPreferences
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes and journals ownership of BYD's instrument Android compositor. */
class ClusterContainerCoordinator internal constructor(
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    enum class Owner {
        CAMERA,
        PROJECTION,
        CLUSTER_CARD,
        CLUSTER_LYRICS,
    }

    private val desiredOwners = ConcurrentHashMap.newKeySet<Owner>()
    private val reconcileMutex = Mutex()

    @Volatile
    private var confirmedPowered = false
    private var deferredRetryJob: Job? = null
    private var deferredRetryCount = 0

    fun request(helper: HelperClient, owner: Owner, active: Boolean) {
        updateDesired(owner, active)
        scope.launch { reconcile(helper) }
    }

    suspend fun setOwner(helper: HelperClient, owner: Owner, active: Boolean): Boolean {
        updateDesired(owner, active)
        reconcile(helper)
        return if (active) {
            confirmedPowered
        } else {
            desiredOwners.isNotEmpty() || (!confirmedPowered && !hasStaleMarker())
        }
    }

    /** Turns off a compositor left powered by a process that died before releasing its owner. */
    suspend fun recoverStale(helper: HelperClient) {
        if (desiredOwners.isNotEmpty() || !hasStaleMarker()) return
        WjxFileLogger.w(TAG, "recovering cluster compositor left by a prior process")
        reconcile(helper)
    }

    internal fun ownersSnapshot(): Set<Owner> = desiredOwners.toSet()

    private fun updateDesired(owner: Owner, active: Boolean) {
        if (active) desiredOwners += owner else desiredOwners -= owner
    }

    private suspend fun reconcile(helper: HelperClient) = withContext(NonCancellable) {
        reconcileMutex.withLock {
            while (true) {
                val shouldPower = desiredOwners.isNotEmpty()
                if (shouldPower) {
                    if (!confirmedPowered) {
                        if (!writeMarker(true)) {
                            WjxFileLogger.e(TAG, "cluster compositor marker write failed; refusing power-up")
                            return@withLock
                        }
                        val applied = applyWithRetry(helper, true)
                        if (!applied) {
                            WjxFileLogger.w(TAG, "cluster compositor power-up not confirmed")
                            scheduleDeferredRetry(helper)
                            return@withLock
                        }
                        clearDeferredRetry()
                        confirmedPowered = true
                        WjxFileLogger.i(TAG, "cluster compositor powered owners=${ownerLabels()}")
                    }
                } else {
                    if (confirmedPowered || hasStaleMarker()) {
                        val applied = applyWithRetry(helper, false)
                        if (!applied) {
                            WjxFileLogger.w(TAG, "cluster compositor power-down not confirmed; marker kept")
                            scheduleDeferredRetry(helper)
                            return@withLock
                        }
                        clearDeferredRetry()
                        confirmedPowered = false
                        if (!writeMarker(false)) {
                            WjxFileLogger.w(TAG, "cluster compositor stopped but stale marker could not be cleared")
                        }
                        WjxFileLogger.i(TAG, "cluster compositor powered down")
                    }
                }
                if (shouldPower == desiredOwners.isNotEmpty()) return@withLock
            }
        }
    }

    private fun ownerLabels(): String = desiredOwners.joinToString("+") { it.name.lowercase() }

    private suspend fun applyWithRetry(helper: HelperClient, powered: Boolean): Boolean {
        repeat(APPLY_ATTEMPTS) { attempt ->
            if (runCatching { helper.setClusterContainerMode(powered) }.getOrDefault(false)) {
                return true
            }
            if (attempt < APPLY_ATTEMPTS - 1) delay(APPLY_RETRY_DELAYS_MS[attempt])
        }
        return false
    }

    private fun scheduleDeferredRetry(helper: HelperClient) {
        if (deferredRetryJob?.isActive == true || deferredRetryCount >= MAX_DEFERRED_RETRIES) return
        val delayMs = DEFERRED_RETRY_BASE_MS * (deferredRetryCount + 1)
        deferredRetryCount++
        deferredRetryJob = scope.launch {
            delay(delayMs)
            deferredRetryJob = null
            reconcile(helper)
        }
    }

    private fun clearDeferredRetry() {
        deferredRetryCount = 0
        deferredRetryJob?.cancel()
        deferredRetryJob = null
    }

    private fun hasStaleMarker(): Boolean = prefs.getBoolean(KEY_POWERED, false)

    @Suppress("ApplySharedPref")
    private fun writeMarker(powered: Boolean): Boolean = if (powered) {
        prefs.edit().putBoolean(KEY_POWERED, true).commit()
    } else {
        prefs.edit().remove(KEY_POWERED).commit()
    }

    companion object {
        const val PREFS_NAME = "wjx_cluster_container_runtime"
        internal const val KEY_POWERED = "compositor_powered"
        private const val TAG = "ClusterContainer"
        private const val APPLY_ATTEMPTS = 3
        private val APPLY_RETRY_DELAYS_MS = longArrayOf(250L, 600L)
        private const val MAX_DEFERRED_RETRIES = 6
        private const val DEFERRED_RETRY_BASE_MS = 1_500L

        @Volatile
        private var instance: ClusterContainerCoordinator? = null

        fun get(context: Context): ClusterContainerCoordinator =
            instance ?: synchronized(this) {
                instance ?: ClusterContainerCoordinator(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
