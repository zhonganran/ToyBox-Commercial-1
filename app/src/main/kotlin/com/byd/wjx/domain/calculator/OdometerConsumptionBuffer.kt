package com.byd.wjx.domain.calculator

import com.byd.wjx.data.local.dao.OdometerSampleDao
import com.byd.wjx.data.local.entity.OdometerSampleEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Singleton

data class BufferStatus(
    val rowCount: Int,
    val newestMileageKm: Double?,
    val oldestMileageKm: Double?,
    val recentAvg: Double,
    val shortAvg: Double?,
)

/**
 * Persistent rolling window of (mileage, totalElec, soc, sessionId, ts) snapshots.
 * Single source of truth for consumption display, trend arrow, and range
 * estimation. See spec 3.1 for algorithm details.
 *
 * SessionId boundary: pairs of consecutive samples (by mileage ASC) with
 * different sessionId values are skipped during averaging — they straddle an
 * ignition cycle, so the energy delta does not reflect "consumption while
 * driving" (could be overnight BMS drain, AC charging, etc).
 */
@Singleton
class OdometerConsumptionBuffer(
    private val dao: OdometerSampleDao,
    private val fallbackEmaProvider: suspend () -> Double,
) : ConsumptionAvgSource {
    private val mutex = Mutex()
    private var lastSampleCache: OdometerSampleEntity? = null
    private var averagesDirty = true
    private var averagesCalculatedAt = 0L
    private var cachedRecentAverage = 0.0
    private var cachedShortAverage: Double? = null

    suspend fun onSample(
        mileage: Double?,
        totalElec: Double?,
        socPercent: Int?,
        sessionId: Long?,
    ): Unit = mutex.withLock {
        // Charging is handled by the built-in delta guards: mileage stays constant
        // during charging, so MIN_MILEAGE_DELTA suppresses inserts and dKm<=0
        // skips the pair during averaging. An explicit isCharging flag was
        // dropped in v2.4.7 because chargeGunState semantics differ per model
        // and the mis-detection silently blocked all driving samples.
        if (mileage == null) return@withLock
        // DiPlus startup race: occasionally returns Mileage:0 before the CAN
        // bus delivers the real odometer. Persisting that zero would poison
        // the buffer permanently — every real reading afterwards looks like a
        // jump > 100 km and gets rejected. Real BYD vehicles always report
        // odometers far above 1 km, so a sub-1-km reading is always a glitch.
        if (mileage < MIN_VALID_ODOMETER_KM) return@withLock
        val prev = lastSampleCache ?: dao.last().also { lastSampleCache = it }
        if (prev != null) {
            if (mileage < prev.mileageKm) return@withLock // odometer regression
            if (mileage - prev.mileageKm > 100.0) {
                // Unrealistic jump: prev row is stale (corrupt baseline from a
                // legacy startup-race or a DiPars hiccup). Wipe and re-anchor
                // on the new reading instead of silently rejecting forever.
                dao.clear()
                lastSampleCache = null
            } else {
                val sameSession = prev.sessionId == sessionId
                val tinyMove = mileage - prev.mileageKm < MIN_MILEAGE_DELTA
                if (sameSession && tinyMove) return@withLock // spam suppression
            }
        }
        val inserted = OdometerSampleEntity(
            mileageKm = mileage,
            totalElecKwh = totalElec,
            socPercent = socPercent,
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
        )
        dao.insert(inserted)
        lastSampleCache = inserted
        averagesDirty = true
        val newest = mileage
        dao.trimBelow(newest - WINDOW_KM - TRIM_HYSTERESIS_KM)
        val n = dao.count()
        if (n > MAX_BUFFER_ROWS) dao.deleteOldest(n - MAX_BUFFER_ROWS)
    }

    override suspend fun recentAvgConsumption(): Double = mutex.withLock {
        refreshAverageCacheIfNeeded()
        cachedRecentAverage
    }

    suspend fun shortAvgConsumption(): Double? = mutex.withLock {
        refreshAverageCacheIfNeeded()
        cachedShortAverage
    }

    /**
     * One-shot cleanup for buffers poisoned by the v2.4.5–v2.4.7 startup race
     * (DiPars returned `Mileage:0` on first poll and the row stuck around).
     * Returns the number of rows cleared so the caller can log it. Safe to
     * call on every service start — no-op when the buffer is already healthy.
     */
    suspend fun cleanupCorruptStartupRows(): Int = mutex.withLock {
        val all = dao.windowFrom(0.0)
        val oldest = all.firstOrNull() ?: return@withLock 0
        if (oldest.mileageKm < MIN_VALID_ODOMETER_KM) {
            val n = all.size
            dao.clear()
            lastSampleCache = null
            averagesDirty = true
            n
        } else {
            lastSampleCache = all.lastOrNull()
            averagesDirty = true
            0
        }
    }

    suspend fun status(): BufferStatus = mutex.withLock {
        val all = dao.windowFrom(0.0)
        val newest = all.lastOrNull()
        val shortWindow = newest?.let { latest ->
            all.filter { it.mileageKm >= latest.mileageKm - SHORT_WINDOW_KM }
        }.orEmpty()
        BufferStatus(
            rowCount = all.size,
            newestMileageKm = newest?.mileageKm,
            oldestMileageKm = all.firstOrNull()?.mileageKm,
            recentAvg = computeAvgUnlocked(all, WINDOW_KM, fallbackOnShort = true),
            shortAvg = run {
                val v = computeAvgUnlocked(shortWindow, SHORT_WINDOW_KM, fallbackOnShort = false)
                if (v.isNaN()) null else v
            },
        )
    }

    private suspend fun refreshAverageCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (!averagesDirty && now - averagesCalculatedAt < AVERAGE_CACHE_MAX_AGE_MS) return

        val newest = lastSampleCache ?: dao.last().also { lastSampleCache = it }
        if (newest == null) {
            cachedRecentAverage = fallbackEmaProvider()
            cachedShortAverage = null
        } else {
            val window = dao.windowFrom(newest.mileageKm - WINDOW_KM)
            cachedRecentAverage = computeAvgUnlocked(window, WINDOW_KM, fallbackOnShort = true)
            val shortWindow = window.filter { it.mileageKm >= newest.mileageKm - SHORT_WINDOW_KM }
            val short = computeAvgUnlocked(shortWindow, SHORT_WINDOW_KM, fallbackOnShort = false)
            cachedShortAverage = short.takeUnless(Double::isNaN)
        }
        averagesDirty = false
        averagesCalculatedAt = now
    }

    private suspend fun computeAvgUnlocked(
        samples: List<OdometerSampleEntity>,
        windowKm: Double,
        fallbackOnShort: Boolean,
    ): Double {
        if (samples.size < 2) return if (fallbackOnShort) fallbackEmaProvider() else Double.NaN
        var totalKm = 0.0
        var totalKwh = 0.0
        for (i in 1..samples.lastIndex) {
            val prev = samples[i - 1]
            val cur = samples[i]
            if (prev.sessionId != cur.sessionId) continue
            val pe = prev.totalElecKwh
            val ce = cur.totalElecKwh
            if (pe == null || ce == null) continue
            val dKm = cur.mileageKm - prev.mileageKm
            val dKwh = ce - pe
            if (dKm <= 0.0 || dKwh < 0.0) continue
            totalKm += dKm
            totalKwh += dKwh
        }
        // windowFrom(newest - SHORT_WINDOW_KM) returns samples with mileage_km >=
        // newest - SHORT_WINDOW_KM, but the oldest such sample almost never sits
        // exactly on that boundary — poll-jitter at real speeds makes
        // oldest_in_window a hair above the cut, so totalKm = (last -
        // oldest_in_window) is typically slightly under SHORT_WINDOW_KM. On top of
        // that, regen / idle pairs inside the window are skipped, shrinking the
        // valid distance further. The window was widened to 3 km (v3.1.x) so a
        // regen tail no longer starves it below the threshold. Requiring totalKm
        // == SHORT_WINDOW_KM here would reject the window most of the time and the
        // display would oscillate between a coloured arrow and grey "no trend" all
        // trip long. 1.5 km of valid pair data is enough to call the short-window
        // trend.
        val minOk = if (fallbackOnShort) MIN_BUFFER_KM else MIN_SHORT_BUFFER_KM
        if (totalKm < minOk) return if (fallbackOnShort) fallbackEmaProvider() else Double.NaN
        return totalKwh / totalKm * 100.0
    }

    companion object {
        const val WINDOW_KM = 25.0
        const val SHORT_WINDOW_KM = 3.0
        const val MIN_BUFFER_KM = 5.0
        const val MIN_SHORT_BUFFER_KM = 1.5
        const val MIN_MILEAGE_DELTA = 0.05
        const val MAX_BUFFER_ROWS = 500
        const val TRIM_HYSTERESIS_KM = 1.0
        const val MIN_VALID_ODOMETER_KM = 1.0
        const val AVERAGE_CACHE_MAX_AGE_MS = 60_000L
    }
}
