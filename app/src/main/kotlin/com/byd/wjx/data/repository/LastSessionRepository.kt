package com.byd.wjx.data.repository

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight in-memory holder for the most recently completed driving session's
 * SOC and fuel bookmarks. Written by TrackingService on session start/end; read by
 * HistoryImporter when enriching freshly-imported TripEntity rows.
 *
 * In-memory only — intentionally not persisted. Process restart mid-session
 * drops the bookmarks; HistoryImporter falls back to null socStart/socEnd for
 * that trip (same as before the native-stack migration). This is acceptable:
 * the SOC enrichment is best-effort and only benefits the current session.
 *
 * Thread safety: single AtomicReference<Snapshot?> guarantees that snapshot()
 * always returns a consistent view — no mix of fields from different sessions.
 */
@Singleton
class LastSessionRepository @Inject constructor() {

    data class Snapshot(
        val startSoc: Int?,
        val endSoc: Int?,
        val startTs: Long?,
        val endTs: Long?,
        val startFuelL: Double?,
        val maxFuelL: Double?,
    ) {
        val fuelConsumedL: Double?
            get() {
                val max = maxFuelL?.takeIf { it.isFinite() && it in 0.0..500.0 } ?: return null
                val start = startFuelL?.takeIf { it.isFinite() && it in 0.0..500.0 }
                if (start == null || start <= 0.05) return max.takeIf { it > 0.0 }
                return (max - start).takeIf { it > 0.0 }
            }
    }

    private val state = AtomicReference<Snapshot?>(null)

    fun onSessionStart(soc: Int?, ts: Long, fuelL: Double? = null) {
        val validFuel = fuelL.validFuel()
        state.set(
            Snapshot(
                startSoc = soc,
                endSoc = null,
                startTs = ts,
                endTs = null,
                startFuelL = validFuel,
                maxFuelL = validFuel,
            ),
        )
    }

    fun onSessionSample(fuelL: Double?) {
        val validFuel = fuelL.validFuel() ?: return
        while (true) {
            val cur = state.get() ?: return
            val next = cur.copy(maxFuelL = cur.maxFuelL?.let { maxOf(it, validFuel) } ?: validFuel)
            if (state.compareAndSet(cur, next)) return
        }
    }

    fun onSessionEnd(soc: Int?, ts: Long, fuelL: Double? = null) {
        onSessionSample(fuelL)
        // CAS loop: preserve start fields written by onSessionStart.
        while (true) {
            val cur = state.get()
            val next = Snapshot(
                startSoc = cur?.startSoc,
                endSoc   = soc,
                startTs  = cur?.startTs,
                endTs    = ts,
                startFuelL = cur?.startFuelL,
                maxFuelL = cur?.maxFuelL ?: fuelL.validFuel(),
            )
            if (state.compareAndSet(cur, next)) return
        }
    }

    fun snapshot(): Snapshot? = state.get()
}

private fun Double?.validFuel(): Double? =
    this?.takeIf { it.isFinite() && it in 0.0..500.0 }
