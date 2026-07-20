package com.byd.wjx.desktopcard

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.byd.wjx.storage.SoundLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

@Singleton
class DesktopQuickCardRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val soundLibrary: SoundLibrary,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _config = MutableStateFlow(load(validateSounds = true))
    val config: StateFlow<DesktopQuickCardConfig> = _config.asStateFlow()
    private val persistLayout = Runnable { persist(_config.value) }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _config.value = load(validateSounds = false)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setEnabled(value: Boolean) = update { copy(enabled = value) }
    fun setAllowOverOtherApps(value: Boolean) = update { copy(allowOverOtherApps = value) }
    fun setScale(value: Float) = updateLayout { copy(scale = value) }
    fun setSectionGapDp(value: Float) = updateLayout { copy(sectionGapDp = value) }
    fun setDividerEnabled(value: Boolean) = update { copy(dividerEnabled = value) }
    fun setFunctionCommands(value: List<String>) = update { copy(functionCommands = value) }
    fun setSoundPaths(value: List<String>) = update { copy(soundPaths = value) }

    fun savePosition(x: Int, y: Int) = update {
        copy(x = x, y = y, positionInitialized = true)
    }

    fun resetPosition() = update {
        copy(x = 0, y = 0, positionInitialized = false)
    }

    fun refresh() {
        val sanitized = sanitize(_config.value, validateSounds = true)
        if (sanitized != _config.value) write(sanitized) else _config.value = sanitized
    }

    private fun update(transform: DesktopQuickCardConfig.() -> DesktopQuickCardConfig) {
        mainHandler.removeCallbacks(persistLayout)
        write(sanitize(_config.value.transform(), validateSounds = false))
    }

    private fun updateLayout(transform: DesktopQuickCardConfig.() -> DesktopQuickCardConfig) {
        // Preview every slider tick immediately, but collapse SharedPreferences writes while the
        // finger is moving. The final value is still persisted automatically without a save button.
        _config.value = sanitize(_config.value.transform(), validateSounds = false)
        mainHandler.removeCallbacks(persistLayout)
        mainHandler.postDelayed(persistLayout, LAYOUT_PERSIST_DEBOUNCE_MS)
    }

    private fun sanitize(
        raw: DesktopQuickCardConfig,
        validateSounds: Boolean,
    ): DesktopQuickCardConfig =
        sanitizeDesktopQuickCardConfig(
            raw = raw,
            validSoundPaths = if (validateSounds) {
                soundLibrary.listSounds().mapTo(linkedSetOf()) { it.file.absolutePath }
            } else {
                null
            },
        )

    private fun load(validateSounds: Boolean): DesktopQuickCardConfig {
        val raw = DesktopQuickCardConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            allowOverOtherApps = prefs.getBoolean(KEY_ALLOW_OVER_OTHER_APPS, false),
            functionCommands = if (prefs.contains(KEY_FUNCTION_COMMANDS)) {
                readList(KEY_FUNCTION_COMMANDS)
            } else {
                emptyList()
            },
            soundPaths = readList(KEY_SOUND_PATHS),
            scale = prefs.getFloat(KEY_SCALE, 1f),
            sectionGapDp = prefs.getFloat(KEY_SECTION_GAP_DP, DESKTOP_QUICK_CARD_DEFAULT_SECTION_GAP_DP),
            dividerEnabled = prefs.getBoolean(KEY_DIVIDER_ENABLED, true),
            x = prefs.getInt(KEY_X, 0),
            y = prefs.getInt(KEY_Y, 0),
            positionInitialized = prefs.getBoolean(KEY_POSITION_INITIALIZED, false),
        )
        return sanitize(raw, validateSounds)
    }

    private fun write(value: DesktopQuickCardConfig) {
        _config.value = value
        persist(value)
    }

    private fun persist(value: DesktopQuickCardConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putBoolean(KEY_ALLOW_OVER_OTHER_APPS, value.allowOverOtherApps)
            .putString(KEY_FUNCTION_COMMANDS, JSONArray(value.functionCommands).toString())
            .putString(KEY_SOUND_PATHS, JSONArray(value.soundPaths).toString())
            .putFloat(KEY_SCALE, value.scale)
            .putFloat(KEY_SECTION_GAP_DP, value.sectionGapDp)
            .putBoolean(KEY_DIVIDER_ENABLED, value.dividerEnabled)
            .putInt(KEY_X, value.x)
            .putInt(KEY_Y, value.y)
            .putBoolean(KEY_POSITION_INITIALIZED, value.positionInitialized)
            .apply()
    }

    private fun readList(key: String): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        buildList(array.length()) {
            repeat(array.length()) { index -> add(array.optString(index)) }
        }
    }.getOrDefault(emptyList())

    companion object {
        const val PREFS_NAME = "wjx_desktop_cards"
        private const val LAYOUT_PERSIST_DEBOUNCE_MS = 120L
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALLOW_OVER_OTHER_APPS = "allow_over_other_apps"
        private const val KEY_FUNCTION_COMMANDS = "function_commands"
        private const val KEY_SOUND_PATHS = "sound_paths"
        private const val KEY_SCALE = "scale"
        private const val KEY_SECTION_GAP_DP = "section_gap_dp"
        private const val KEY_DIVIDER_ENABLED = "divider_enabled"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_POSITION_INITIALIZED = "position_initialized"
    }
}
