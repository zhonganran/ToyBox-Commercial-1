package com.byd.wjx.led

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readSnapshot())
    val state: StateFlow<LedPreferenceSnapshot> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) = update {
        putBoolean(KEY_ENABLED, enabled)
    }

    fun setDevice(address: String, name: String) = update {
        putString(KEY_DEVICE_ADDRESS, address)
        putString(KEY_DEVICE_NAME, name)
    }

    fun clearDevice() = update {
        remove(KEY_DEVICE_ADDRESS)
        remove(KEY_DEVICE_NAME)
    }

    fun setBrightness(percent: Int) = update {
        putInt(KEY_BRIGHTNESS, percent.coerceIn(0, 100))
    }

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = readSnapshot()
    }

    private fun readSnapshot(): LedPreferenceSnapshot = LedPreferenceSnapshot(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        deviceAddress = prefs.getString(KEY_DEVICE_ADDRESS, "").orEmpty(),
        deviceName = prefs.getString(KEY_DEVICE_NAME, "").orEmpty(),
        brightness = prefs.getInt(KEY_BRIGHTNESS, 70).coerceIn(0, 100),
    )

    companion object {
        const val PREFS_NAME = "wjx_led_link"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEVICE_ADDRESS = "device_address"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_BRIGHTNESS = "brightness"
    }
}

