package com.byd.wjx.data.energy

import android.annotation.SuppressLint
import android.content.Context
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.domain.energy.EnergyFlowAccumulator
import com.byd.wjx.domain.energy.EnergyFlowStats
import com.byd.wjx.domain.energy.EnergyFlowTotals
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface EnergyFlowStatsRepository {
    val state: StateFlow<EnergyFlowStats>
}

/** Persistent owner for energy-flow totals. Disk writes are batched to at most once per 30s. */
@Singleton
class EnergyFlowStatsStore @Inject constructor(
    @ApplicationContext context: Context,
) : EnergyFlowStatsRepository {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val accumulator = EnergyFlowAccumulator(
        initialLifetime = readTotals(PREFIX_LIFETIME),
        initialSession = readTotals(PREFIX_SESSION),
        initialSessionStartedAt = preferences.getLong(KEY_SESSION_STARTED_AT, 0L)
            .takeIf { it > 0L },
    )
    private val _state = MutableStateFlow(accumulator.stats)
    override val state: StateFlow<EnergyFlowStats> = _state.asStateFlow()

    private var dirty = false
    private var lastPersistedAtMs = preferences.getLong(KEY_UPDATED_AT, 0L)

    @Synchronized
    fun onSample(nowMs: Long, data: DiParsData, sessionStartedAt: Long?) {
        val before = accumulator.stats
        val next = accumulator.onSample(nowMs, data, sessionStartedAt)
        if (next != before) _state.value = next
        if (
            next.sessionStartedAt != before.sessionStartedAt ||
            next.session != before.session ||
            next.lifetime != before.lifetime
        ) {
            dirty = true
        }
        if (dirty && nowMs - lastPersistedAtMs >= PERSIST_INTERVAL_MS) {
            persist(nowMs, synchronous = false)
        }
    }

    @Synchronized
    fun flush(nowMs: Long = System.currentTimeMillis()) {
        if (dirty) persist(nowMs, synchronous = true)
    }

    @SuppressLint("ApplySharedPref") // flush() intentionally guarantees final process-shutdown durability.
    private fun persist(nowMs: Long, synchronous: Boolean) {
        val snapshot = accumulator.stats
        val editor = preferences.edit().apply {
            snapshot.sessionStartedAt?.let { putLong(KEY_SESSION_STARTED_AT, it) }
                ?: remove(KEY_SESSION_STARTED_AT)
            putTotals(PREFIX_SESSION, snapshot.session)
            putTotals(PREFIX_LIFETIME, snapshot.lifetime)
            putLong(KEY_UPDATED_AT, nowMs)
        }
        val accepted = if (synchronous) editor.commit() else {
            editor.apply()
            true
        }
        if (accepted) {
            lastPersistedAtMs = nowMs
            dirty = false
        }
    }

    private fun readTotals(prefix: String): EnergyFlowTotals = EnergyFlowTotals(
        ev4wdRegenKwh = readDouble(prefix, KEY_EV_4WD),
        evFwdRegenKwh = readDouble(prefix, KEY_EV_FWD),
        evRwdRegenKwh = readDouble(prefix, KEY_EV_RWD),
        hevRegenKwh = readDouble(prefix, KEY_HEV),
        assistRegenKwh = readDouble(prefix, KEY_ASSIST),
        otherRegenKwh = readDouble(prefix, KEY_OTHER),
        engineGenerationKwh = readDouble(prefix, KEY_ENGINE_GEN),
    )

    private fun readDouble(prefix: String, key: String): Double = runCatching {
        if (!preferences.contains("${prefix}_$key")) return@runCatching 0.0
        Double.fromBits(preferences.getLong("${prefix}_$key", 0L))
            .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    }.getOrDefault(0.0)

    private fun android.content.SharedPreferences.Editor.putTotals(
        prefix: String,
        totals: EnergyFlowTotals,
    ) {
        putLong("${prefix}_$KEY_EV_4WD", totals.ev4wdRegenKwh.toBits())
        putLong("${prefix}_$KEY_EV_FWD", totals.evFwdRegenKwh.toBits())
        putLong("${prefix}_$KEY_EV_RWD", totals.evRwdRegenKwh.toBits())
        putLong("${prefix}_$KEY_HEV", totals.hevRegenKwh.toBits())
        putLong("${prefix}_$KEY_ASSIST", totals.assistRegenKwh.toBits())
        putLong("${prefix}_$KEY_OTHER", totals.otherRegenKwh.toBits())
        putLong("${prefix}_$KEY_ENGINE_GEN", totals.engineGenerationKwh.toBits())
    }

    companion object {
        const val PREFS_NAME = "wjx_energy_flow_stats"
        const val PERSIST_INTERVAL_MS = 30_000L

        private const val PREFIX_SESSION = "session"
        private const val PREFIX_LIFETIME = "lifetime"
        private const val KEY_SESSION_STARTED_AT = "session_started_at"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_EV_4WD = "ev_4wd"
        private const val KEY_EV_FWD = "ev_fwd"
        private const val KEY_EV_RWD = "ev_rwd"
        private const val KEY_HEV = "hev"
        private const val KEY_ASSIST = "assist"
        private const val KEY_OTHER = "other"
        private const val KEY_ENGINE_GEN = "engine_generation"
    }
}
