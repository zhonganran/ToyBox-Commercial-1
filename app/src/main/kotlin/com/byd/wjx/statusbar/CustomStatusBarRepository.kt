package com.byd.wjx.statusbar

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
class CustomStatusBarRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<CustomStatusBarConfig> = _config.asStateFlow()
    private val _runtimeRevision = MutableStateFlow(0L)
    val runtimeRevision: StateFlow<Long> = _runtimeRevision.asStateFlow()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _config.value = load()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setEnabled(value: Boolean) = update { copy(enabled = value) }
    fun setBackgroundOpacity(value: Float) = update { copy(backgroundOpacity = value) }
    fun setHeightDp(value: Float) = update { copy(heightDp = value) }
    fun setTextSizeSp(value: Float) = update { copy(textSizeSp = value) }
    fun setHorizontalOffsetDp(value: Float) = update { copy(horizontalOffsetDp = value) }
    fun setVerticalOffsetDp(value: Float) = update { copy(verticalOffsetDp = value) }
    fun setItemSpacingDp(value: Float) = update { copy(itemSpacingDp = value) }
    fun setFontStyle(value: CustomStatusBarFontStyle) = update { copy(fontStyle = value) }
    fun setIconStyle(value: CustomStatusBarIconStyle) = update { copy(iconStyle = value) }
    fun setTextColorArgb(value: Int) = update { copy(textColorArgb = value) }
    fun addItem(item: CustomStatusBarItem) = update { addItem(item) }
    fun removeItem(item: CustomStatusBarItem) = update { removeItem(item) }
    fun moveItem(item: CustomStatusBarItem, offset: Int) = update { moveItem(item, offset) }

    fun notifyOverlayPermissionChanged() {
        _runtimeRevision.value += 1L
    }

    private fun update(transform: CustomStatusBarConfig.() -> CustomStatusBarConfig) {
        write(sanitizeCustomStatusBarConfig(_config.value.transform()))
    }

    private fun load(): CustomStatusBarConfig {
        val defaults = CustomStatusBarConfig()
        return sanitizeCustomStatusBarConfig(
            CustomStatusBarConfig(
                enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
                items = readItems(KEY_ITEMS, defaults.items),
                backgroundOpacity = prefs.getFloat(KEY_BACKGROUND_OPACITY, defaults.backgroundOpacity),
                heightDp = prefs.getFloat(KEY_HEIGHT_DP, defaults.heightDp),
                textSizeSp = prefs.getFloat(KEY_TEXT_SIZE_SP, defaults.textSizeSp),
                horizontalOffsetDp = prefs.getFloat(KEY_HORIZONTAL_OFFSET_DP, defaults.horizontalOffsetDp),
                verticalOffsetDp = prefs.getFloat(KEY_VERTICAL_OFFSET_DP, defaults.verticalOffsetDp),
                itemSpacingDp = prefs.getFloat(KEY_ITEM_SPACING_DP, defaults.itemSpacingDp),
                fontStyle = CustomStatusBarFontStyle.byId[
                    prefs.getString(KEY_FONT_STYLE, defaults.fontStyle.id)
                ] ?: defaults.fontStyle,
                iconStyle = CustomStatusBarIconStyle.byId[
                    prefs.getString(KEY_ICON_STYLE, defaults.iconStyle.id)
                ] ?: defaults.iconStyle,
                textColorArgb = prefs.getInt(KEY_TEXT_COLOR_ARGB, defaults.textColorArgb),
            ),
        )
    }

    private fun write(value: CustomStatusBarConfig) {
        _config.value = value
        prefs.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putString(KEY_ITEMS, JSONArray(value.items.map(CustomStatusBarItem::id)).toString())
            .putFloat(KEY_BACKGROUND_OPACITY, value.backgroundOpacity)
            .putFloat(KEY_HEIGHT_DP, value.heightDp)
            .putFloat(KEY_TEXT_SIZE_SP, value.textSizeSp)
            .putFloat(KEY_HORIZONTAL_OFFSET_DP, value.horizontalOffsetDp)
            .putFloat(KEY_VERTICAL_OFFSET_DP, value.verticalOffsetDp)
            .putFloat(KEY_ITEM_SPACING_DP, value.itemSpacingDp)
            .putString(KEY_FONT_STYLE, value.fontStyle.id)
            .putString(KEY_ICON_STYLE, value.iconStyle.id)
            .putInt(KEY_TEXT_COLOR_ARGB, value.textColorArgb)
            .apply()
    }

    private fun readItems(
        key: String,
        fallback: List<CustomStatusBarItem>,
    ): List<CustomStatusBarItem> {
        if (!prefs.contains(key)) return fallback
        return runCatching {
            val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
            buildList(array.length()) {
                repeat(array.length()) { index ->
                    CustomStatusBarItem.byId[array.optString(index)]?.let(::add)
                }
            }
        }.getOrDefault(fallback)
    }

    private companion object {
        const val PREFS_NAME = "wjx_custom_status_bar"
        const val KEY_ENABLED = "enabled"
        const val KEY_ITEMS = "items"
        const val KEY_BACKGROUND_OPACITY = "background_opacity"
        const val KEY_HEIGHT_DP = "height_dp"
        const val KEY_TEXT_SIZE_SP = "text_size_sp"
        const val KEY_HORIZONTAL_OFFSET_DP = "horizontal_offset_dp"
        const val KEY_VERTICAL_OFFSET_DP = "vertical_offset_dp"
        const val KEY_ITEM_SPACING_DP = "item_spacing_dp"
        const val KEY_FONT_STYLE = "font_style"
        const val KEY_ICON_STYLE = "icon_style"
        const val KEY_TEXT_COLOR_ARGB = "text_color_argb"
    }
}
