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
    private val configFlows = ClusterCardSlot.entries.associateWith { slot -> MutableStateFlow(load(slot)) }
    private val readOnlyConfigFlows = configFlows.mapValues { (_, flow) -> flow.asStateFlow() }
    val config: StateFlow<ClusterCardConfig> = config(ClusterCardSlot.PRIMARY)
    val config2: StateFlow<ClusterCardConfig> = config(ClusterCardSlot.SECONDARY)
    private val _runtimeRevision = MutableStateFlow(0L)
    val runtimeRevision: StateFlow<Long> = _runtimeRevision.asStateFlow()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        ClusterCardSlot.entries.forEach { slot -> configFlow(slot).value = load(slot) }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun config(slot: ClusterCardSlot): StateFlow<ClusterCardConfig> = checkNotNull(readOnlyConfigFlows[slot])

    fun current(slot: ClusterCardSlot): ClusterCardConfig = configFlow(slot).value

    fun setEnabled(slot: ClusterCardSlot, value: Boolean) = update(slot) { copy(enabled = value) }

    fun setMetricIds(slot: ClusterCardSlot, value: List<String>) = update(slot) { copy(metricIds = value) }

    fun setScale(slot: ClusterCardSlot, value: Float) = update(slot) { copy(scale = value) }

    fun setWidthScale(slot: ClusterCardSlot, value: Float) = update(slot) { copy(widthScale = value) }

    fun setXPct(slot: ClusterCardSlot, value: Float) = update(slot) { copy(xPct = value) }

    fun setYPct(slot: ClusterCardSlot, value: Float) = update(slot) { copy(yPct = value) }

    fun setFontFamily(slot: ClusterCardSlot, value: ClusterCardFont) = update(slot) { copy(fontFamily = value) }

    fun setTextColor(slot: ClusterCardSlot, value: ClusterCardTextColor) = update(slot) { copy(textColor = value) }

    fun setFontScale(slot: ClusterCardSlot, value: Float) = update(slot) { copy(fontScale = value) }

    fun setHeightScale(slot: ClusterCardSlot, value: Float) = update(slot) {
        copy(heightScale = value)
    }

    fun resetPosition(slot: ClusterCardSlot) = update(slot) {
        copy(xPct = slot.defaultXPct, yPct = slot.defaultYPct)
    }

    fun setEnabled(value: Boolean) = setEnabled(ClusterCardSlot.PRIMARY, value)

    fun notifyOverlayPermissionChanged() {
        _runtimeRevision.value += 1L
    }

    fun refresh(slot: ClusterCardSlot? = null) {
        val slots = slot?.let(::listOf) ?: ClusterCardSlot.entries
        slots.forEach { target ->
            val flow = configFlow(target)
            val sanitized = sanitizeClusterCardConfig(load(target))
            if (sanitized != flow.value) write(target, sanitized) else flow.value = sanitized
        }
    }

    private fun update(slot: ClusterCardSlot, transform: ClusterCardConfig.() -> ClusterCardConfig) {
        val flow = configFlow(slot)
        write(slot, sanitizeClusterCardConfig(flow.value.transform()))
    }

    private fun load(slot: ClusterCardSlot): ClusterCardConfig = sanitizeClusterCardConfig(
        ClusterCardConfig(
            enabled = prefs.getBoolean(key(KEY_ENABLED, slot), false),
            metricIds = readMetricIds(slot),
            scale = prefs.getFloat(key(KEY_SCALE, slot), 0.65f),
            widthScale = prefs.getFloat(key(KEY_WIDTH_SCALE, slot), 1f),
            xPct = prefs.getFloat(key(KEY_X_PCT, slot), slot.defaultXPct),
            yPct = prefs.getFloat(key(KEY_Y_PCT, slot), slot.defaultYPct),
            fontFamily = ClusterCardFont.fromStoredValue(
                prefs.getString(key(KEY_FONT_FAMILY, slot), null),
            ),
            textColor = ClusterCardTextColor.fromStoredValue(
                prefs.getString(key(KEY_TEXT_COLOR, slot), null),
            ),
            fontScale = prefs.getFloat(key(KEY_FONT_SCALE, slot), 1f),
            heightScale = prefs.getFloat(key(KEY_HEIGHT_SCALE, slot), 1f),
        ),
    )

    private fun readMetricIds(slot: ClusterCardSlot): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key(KEY_METRIC_IDS, slot), "[]") ?: "[]")
        buildList(array.length()) {
            repeat(array.length()) { index -> add(array.optString(index)) }
        }
    }.getOrDefault(emptyList())

    private fun write(slot: ClusterCardSlot, value: ClusterCardConfig) {
        configFlow(slot).value = value
        val metrics = JSONArray().apply { value.metricIds.forEach(::put) }
        prefs.edit()
            .putBoolean(key(KEY_ENABLED, slot), value.enabled)
            .putString(key(KEY_METRIC_IDS, slot), metrics.toString())
            .putFloat(key(KEY_SCALE, slot), value.scale)
            .putFloat(key(KEY_WIDTH_SCALE, slot), value.widthScale)
            .putFloat(key(KEY_X_PCT, slot), value.xPct)
            .putFloat(key(KEY_Y_PCT, slot), value.yPct)
            .putString(key(KEY_FONT_FAMILY, slot), value.fontFamily.name)
            .putString(key(KEY_TEXT_COLOR, slot), value.textColor.name)
            .putFloat(key(KEY_FONT_SCALE, slot), value.fontScale)
            .putFloat(key(KEY_HEIGHT_SCALE, slot), value.heightScale)
            .apply()
    }

    private fun configFlow(slot: ClusterCardSlot): MutableStateFlow<ClusterCardConfig> =
        checkNotNull(configFlows[slot])

    private fun key(base: String, slot: ClusterCardSlot): String = base + slot.preferenceSuffix

    companion object {
        const val PREFS_NAME = "wjx_cluster_card"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_METRIC_IDS = "metric_ids"
        private const val KEY_SCALE = "scale"
        private const val KEY_WIDTH_SCALE = "width_scale"
        private const val KEY_X_PCT = "x_pct"
        private const val KEY_Y_PCT = "y_pct"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_HEIGHT_SCALE = "height_scale"
    }
}
