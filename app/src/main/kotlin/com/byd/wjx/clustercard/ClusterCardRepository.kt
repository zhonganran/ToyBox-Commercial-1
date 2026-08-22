package com.byd.wjx.clustercard

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

@Singleton
class ClusterCardRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<ClusterCardConfig> = _config.asStateFlow()
    private val _runtimeRevision = MutableStateFlow(0L)
    val runtimeRevision: StateFlow<Long> = _runtimeRevision.asStateFlow()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _config.value = load()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setEnabled(value: Boolean) = update { copy(enabled = value) }

    fun setMetricIds(value: List<String>) = update { copy(metricIds = value) }

    fun setScale(value: Float) = update { copy(scale = value) }

    fun setWidthScale(value: Float) = update { copy(widthScale = value) }

    fun setXPct(value: Float) = update { copy(xPct = value) }

    fun setYPct(value: Float) = update { copy(yPct = value) }

    fun resetPosition() = update { copy(xPct = 50f, yPct = 50f) }

    fun notifyOverlayPermissionChanged() {
        _runtimeRevision.value += 1L
    }

    fun refresh() {
        val sanitized = sanitizeClusterCardConfig(load())
        if (sanitized != _config.value) write(sanitized) else _config.value = sanitized
    }

    private fun update(transform: ClusterCardConfig.() -> ClusterCardConfig) {
        write(sanitizeClusterCardConfig(_config.value.transform()))
    }

    private fun load(): ClusterCardConfig = sanitizeClusterCardConfig(
        ClusterCardConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            metricIds = readMetricIds(),
            scale = prefs.getFloat(KEY_SCALE, 0.65f),
            widthScale = prefs.getFloat(KEY_WIDTH_SCALE, 1f),
            xPct = prefs.getFloat(KEY_X_PCT, 50f),
            yPct = prefs.getFloat(KEY_Y_PCT, 50f),
        ),
    )

    private fun readMetricIds(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY_METRIC_IDS, "[]") ?: "[]")
        buildList(array.length()) {
            repeat(array.length()) { index -> add(array.optString(index)) }
        }
    }.getOrDefault(emptyList())

    private fun write(value: ClusterCardConfig) {
        _config.value = value
        val metrics = JSONArray().apply { value.metricIds.forEach(::put) }
        prefs.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putString(KEY_METRIC_IDS, metrics.toString())
            .putFloat(KEY_SCALE, value.scale)
            .putFloat(KEY_WIDTH_SCALE, value.widthScale)
            .putFloat(KEY_X_PCT, value.xPct)
            .putFloat(KEY_Y_PCT, value.yPct)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "wjx_cluster_card"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_METRIC_IDS = "metric_ids"
        private const val KEY_SCALE = "scale"
        private const val KEY_WIDTH_SCALE = "width_scale"
        private const val KEY_X_PCT = "x_pct"
        private const val KEY_Y_PCT = "y_pct"
    }
}
