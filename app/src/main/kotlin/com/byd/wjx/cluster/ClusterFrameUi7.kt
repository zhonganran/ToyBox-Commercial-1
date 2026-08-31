package com.byd.wjx.cluster

import android.content.Context
import android.content.SharedPreferences
import com.byd.wjx.data.autoservice.SentinelDecoder
import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.data.vehicle.WriteOutcome
import com.byd.wjx.log.WjxFileLogger
import com.byd.wjx.vehicle.VehicleSystemProperties
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Owns the reversible Map frame required by platformized OTA V1.6 instrument firmware. */
class ClusterFrameUi7 internal constructor(
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val platformized: () -> Boolean = ::isPlatformizedFirmware,
) {
    enum class Owner { PROJECTION, CAMERA }

    private data class Frame(val center: Int, val left: Int, val menu: Int?)

    private val mutex = Mutex()
    private val owners = mutableSetOf<Owner>()
    private var saved: Frame? = null
    private var reassertJob: Job? = null
    private var failedPasses = 0

    @Volatile
    private var applied = false

    suspend fun apply(helper: HelperClient, owner: Owner): Boolean {
        if (!platformized()) return false
        return mutex.withLock {
            if (applied) {
                owners += owner
                if (reassertJob?.isActive != true) startReassert(helper)
                return@withLock true
            }
            val stock = saved ?: loadPersisted() ?: readAndPersistStock(helper) ?: return@withLock false
            withContext(NonCancellable) {
                saved = stock
                val center = writeRegister(helper, FID_CENTER, CENTER_MAP)
                if (center == WriteOutcome.NOOP) {
                    saved = null
                    clearPersisted()
                    WjxFileLogger.w(TAG, "UI7 frame unsupported: center write was a no-op")
                    return@withContext false
                }
                val leftOk = !stock.ownsLeft() ||
                    writeRegister(helper, FID_LEFT, LEFT_CARD).accepted()
                val menuOk = stock.menu == null ||
                    writeRegister(helper, FID_MENU_SET, MENU_HIDDEN).accepted()
                applied = true
                owners += owner
                startReassert(helper)
                WjxFileLogger.i(
                    TAG,
                    "applied center=${stock.center}->$CENTER_MAP " +
                        (if (stock.ownsLeft()) "left=${stock.left}->$LEFT_CARD " else "") +
                        (stock.menu?.let { "menu=$it->$MENU_HIDDEN " } ?: "") +
                        "ok=${center == WriteOutcome.REAL && leftOk && menuOk}",
                )
                center == WriteOutcome.REAL && leftOk
            }
        }
    }

    suspend fun restore(helper: HelperClient, owner: Owner) {
        if (!platformized()) return
        mutex.withLock {
            if (owner !in owners) return@withLock
            owners -= owner
            if (owners.isNotEmpty()) return@withLock
            reassertJob?.cancel()
            reassertJob = null
            restoreLocked(helper)
        }
    }

    suspend fun recoverStale(helper: HelperClient) {
        if (!platformized() || applied) return
        mutex.withLock {
            if (!applied && (saved != null || loadPersisted() != null)) {
                WjxFileLogger.w(TAG, "recovering UI7 frame left by a prior process")
                restoreLocked(helper)
            }
        }
    }

    internal fun hasStaleFrame(): Boolean = platformized() && !applied && loadPersisted() != null

    private fun Frame.ownsLeft(): Boolean = menu == null

    private suspend fun readAndPersistStock(helper: HelperClient): Frame? {
        val center = readRegister(helper, FID_CENTER)
        val left = readRegister(helper, FID_LEFT)
        if (center == null || left == null) {
            WjxFileLogger.w(TAG, "UI7 frame registers unreadable; leaving instrument unchanged")
            return null
        }
        val frame = Frame(center, left, readRegister(helper, FID_MENU_STATUS))
        if (!persist(frame)) {
            WjxFileLogger.w(TAG, "UI7 stock frame could not be persisted; write skipped")
            return null
        }
        return frame
    }

    private suspend fun restoreLocked(helper: HelperClient) = withContext(NonCancellable) {
        val stock = saved ?: loadPersisted() ?: run {
            applied = false
            return@withContext
        }
        val currentCenter = readRegister(helper, FID_CENTER)
        val restoreCenter = currentCenter == null || currentCenter == CENTER_MAP
        val leftOk = !stock.ownsLeft() || writeRegister(helper, FID_LEFT, stock.left).accepted()
        val centerOk = !restoreCenter || writeRegister(helper, FID_CENTER, stock.center).accepted()
        val menuOk = stock.menu == null || writeRegister(helper, FID_MENU_SET, stock.menu).accepted()
        applied = false
        if (leftOk && centerOk && menuOk) {
            saved = null
            clearPersisted()
        } else {
            saved = stock
            WjxFileLogger.w(TAG, "UI7 frame restore not confirmed; saved values kept for retry")
        }
    }

    private fun startReassert(helper: HelperClient) {
        reassertJob?.cancel()
        failedPasses = 0
        reassertJob = scope.launch {
            while (isActive) {
                delay(REASSERT_INTERVAL_MS)
                reassertOnce(helper)
            }
        }
    }

    internal suspend fun reassertOnce(helper: HelperClient) {
        val ok = withTimeoutOrNull(REASSERT_PASS_TIMEOUT_MS) {
            mutex.withLock { reassertPass(helper) }
        } ?: false
        if (ok) {
            failedPasses = 0
            return
        }
        failedPasses++
        if (failedPasses >= MAX_REASSERT_FAILURES) {
            WjxFileLogger.w(TAG, "UI7 frame reassert stopped after $failedPasses failures")
            reassertJob?.cancel()
            reassertJob = null
        }
    }

    private suspend fun reassertPass(helper: HelperClient): Boolean {
        if (!applied) return true
        val ownsLeft = saved?.ownsLeft() == true
        val center = readRegister(helper, FID_CENTER)
        val left = if (ownsLeft) readRegister(helper, FID_LEFT) else null
        if (center == null || (ownsLeft && left == null)) return false
        currentCoroutineContext().ensureActive()
        val centerOk = center == CENTER_MAP || writeRegister(helper, FID_CENTER, CENTER_MAP).accepted()
        val leftOk = !ownsLeft || left == LEFT_CARD || writeRegister(helper, FID_LEFT, LEFT_CARD).accepted()
        return centerOk && leftOk
    }

    private suspend fun readRegister(helper: HelperClient, fid: Int): Int? =
        runCatching { helper.read(DEV_INSTRUMENT, fid) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            ?.toInt()
            ?.let(SentinelDecoder::decodeInt)

    private suspend fun writeRegister(helper: HelperClient, fid: Int, value: Int): WriteOutcome =
        runCatching { WriteOutcome.fromStatus(helper.writeStatus(DEV_INSTRUMENT, fid, value)) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(WriteOutcome.TRANSIENT)

    private fun WriteOutcome.accepted(): Boolean =
        this == WriteOutcome.REAL || this == WriteOutcome.NOOP

    @Suppress("ApplySharedPref")
    private suspend fun persist(frame: Frame): Boolean = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
            .putInt(KEY_SAVED_CENTER, frame.center)
            .putInt(KEY_SAVED_LEFT, frame.left)
        if (frame.menu != null) editor.putInt(KEY_SAVED_MENU, frame.menu)
        editor.commit()
    }

    @Suppress("ApplySharedPref")
    private suspend fun clearPersisted() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_SAVED_CENTER)
            .remove(KEY_SAVED_LEFT)
            .remove(KEY_SAVED_MENU)
            .commit()
        Unit
    }

    private fun loadPersisted(): Frame? =
        if (prefs.contains(KEY_SAVED_CENTER) && prefs.contains(KEY_SAVED_LEFT)) {
            Frame(
                center = prefs.getInt(KEY_SAVED_CENTER, 0),
                left = prefs.getInt(KEY_SAVED_LEFT, 0),
                menu = if (prefs.contains(KEY_SAVED_MENU)) prefs.getInt(KEY_SAVED_MENU, 0) else null,
            )
        } else {
            null
        }

    companion object {
        const val PREFS_NAME = "wjx_cluster_frame_runtime"
        internal const val DEV_INSTRUMENT = 1007
        internal const val FID_CENTER = 1086373904
        internal const val FID_LEFT = 1086373907
        internal const val FID_MENU_STATUS = 683679769
        internal const val FID_MENU_SET = 1324691472
        internal const val CENTER_MAP = 2
        internal const val LEFT_CARD = 1
        internal const val MENU_HIDDEN = 2
        internal const val REASSERT_INTERVAL_MS = 5_000L
        internal const val REASSERT_PASS_TIMEOUT_MS = 4_000L
        internal const val MAX_REASSERT_FAILURES = 3
        internal const val KEY_SAVED_CENTER = "ui7_frame_saved_center"
        internal const val KEY_SAVED_LEFT = "ui7_frame_saved_left"
        internal const val KEY_SAVED_MENU = "ui7_frame_saved_menu"
        private const val PLATFORMIZED_PROPERTY = "ro.build.ui_platformized"
        private const val TAG = "ClusterFrameUi7"

        @Volatile
        private var instance: ClusterFrameUi7? = null

        fun get(context: Context): ClusterFrameUi7 = instance ?: synchronized(this) {
            instance ?: ClusterFrameUi7(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            ).also { instance = it }
        }

        private fun isPlatformizedFirmware(): Boolean =
            VehicleSystemProperties.read(PLATFORMIZED_PROPERTY).trim().lowercase() in setOf("1", "true")
    }
}
