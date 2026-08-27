package com.byd.wjx.led.ipixelfa

import android.content.Context
import com.byd.wjx.led.LedBrightnessSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IpixelFaPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readSnapshot())
    val state: StateFlow<IpixelFaPreferenceSnapshot> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) = update { putBoolean(KEY_ENABLED, enabled) }

    fun bindDevice(address: String, name: String, enable: Boolean = true) = update {
        putString(KEY_DEVICE_ADDRESS, address)
        putString(KEY_DEVICE_NAME, name)
        putBoolean(KEY_ENABLED, enable)
    }

    fun unbindDevice() = update {
        putBoolean(KEY_ENABLED, false)
        remove(KEY_DEVICE_ADDRESS)
        remove(KEY_DEVICE_NAME)
    }

    fun setBrightness(percent: Int) = update {
        putInt(KEY_BRIGHTNESS, percent.coerceIn(0, 100))
    }

    fun setBrightnessSchedule(schedule: LedBrightnessSchedule) = update {
        val normalized = schedule.normalized(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
        putBoolean(KEY_BRIGHTNESS_SCHEDULE_ENABLED, normalized.enabled)
        putInt(KEY_DAY_START_MINUTE, normalized.dayStartMinute)
        putInt(KEY_NIGHT_START_MINUTE, normalized.nightStartMinute)
        putInt(KEY_DAY_BRIGHTNESS, normalized.dayBrightness)
        putInt(KEY_NIGHT_BRIGHTNESS, normalized.nightBrightness)
    }

    @Synchronized
    fun saveRule(rule: IpixelFaLinkageRule): Boolean {
        val normalized = rule.normalized()
        val current = _state.value.rules
        val duplicate = current.any {
            it.id != normalized.id &&
                it.triggerType == normalized.triggerType &&
                it.conditionValue == normalized.conditionValue
        }
        if (duplicate) return false

        val index = current.indexOfFirst { it.id == normalized.id }
        val updated = if (index >= 0) {
            current.toMutableList().apply { this[index] = normalized }
        } else {
            current + normalized
        }
        setRules(updated)
        return true
    }

    @Synchronized
    fun deleteRule(id: String) = setRules(_state.value.rules.filterNot { it.id == id })

    @Synchronized
    fun setRuleEnabled(id: String, enabled: Boolean) = setRules(
        _state.value.rules.map { if (it.id == id) it.copy(enabled = enabled) else it }
    )

    @Synchronized
    fun moveRule(id: String, offset: Int) {
        val rules = _state.value.rules.toMutableList()
        val from = rules.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + offset).coerceIn(rules.indices)
        if (from == to) return
        val item = rules.removeAt(from)
        rules.add(to, item)
        setRules(rules)
    }

    private fun setRules(rules: List<IpixelFaLinkageRule>) = update {
        putString(KEY_RULES, IpixelFaLinkageRuleCodec.encode(rules))
    }

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = readSnapshot()
    }

    private fun readSnapshot(): IpixelFaPreferenceSnapshot = IpixelFaPreferenceSnapshot(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        deviceAddress = prefs.getString(KEY_DEVICE_ADDRESS, "").orEmpty(),
        deviceName = prefs.getString(KEY_DEVICE_NAME, "").orEmpty(),
        brightness = prefs.getInt(KEY_BRIGHTNESS, 70).coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS),
        brightnessSchedule = LedBrightnessSchedule(
            enabled = prefs.getBoolean(KEY_BRIGHTNESS_SCHEDULE_ENABLED, false),
            dayStartMinute = prefs.getInt(
                KEY_DAY_START_MINUTE,
                LedBrightnessSchedule.DEFAULT_DAY_START_MINUTE,
            ),
            nightStartMinute = prefs.getInt(
                KEY_NIGHT_START_MINUTE,
                LedBrightnessSchedule.DEFAULT_NIGHT_START_MINUTE,
            ),
            dayBrightness = prefs.getInt(KEY_DAY_BRIGHTNESS, 70),
            nightBrightness = prefs.getInt(KEY_NIGHT_BRIGHTNESS, 20),
        ).normalized(MIN_BRIGHTNESS, MAX_BRIGHTNESS),
        rules = prefs.getString(KEY_RULES, null).let { raw ->
            when {
                raw == null -> IpixelFaLinkageRuleFactory.defaultRules()
                else -> IpixelFaLinkageRuleCodec.decode(raw) ?: emptyList()
            }
        },
    )

    companion object {
        const val PREFS_NAME = "wjx_led_ipixel_fa"
        internal const val KEY_RULES = "rules"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEVICE_ADDRESS = "device_address"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_BRIGHTNESS_SCHEDULE_ENABLED = "brightness_schedule_enabled"
        private const val KEY_DAY_START_MINUTE = "day_start_minute"
        private const val KEY_NIGHT_START_MINUTE = "night_start_minute"
        private const val KEY_DAY_BRIGHTNESS = "day_brightness"
        private const val KEY_NIGHT_BRIGHTNESS = "night_brightness"
        private const val MIN_BRIGHTNESS = 0
        private const val MAX_BRIGHTNESS = 100
    }
}
