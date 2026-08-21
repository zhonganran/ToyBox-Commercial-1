package com.byd.wjx.led.a950

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class A950Preferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readSnapshot())
    val state: StateFlow<A950PreferenceSnapshot> = _state.asStateFlow()

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

    @Synchronized
    fun saveRule(rule: A950LinkageRule): Boolean {
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

    private fun setRules(rules: List<A950LinkageRule>) = update {
        putString(KEY_RULES, A950LinkageRuleCodec.encode(rules))
        putBoolean(KEY_RULES_CUSTOMIZED, true)
    }

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = readSnapshot()
    }

    private fun readSnapshot(): A950PreferenceSnapshot = A950PreferenceSnapshot(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        deviceAddress = prefs.getString(KEY_DEVICE_ADDRESS, "").orEmpty(),
        deviceName = prefs.getString(KEY_DEVICE_NAME, "").orEmpty(),
        brightness = prefs.getInt(KEY_BRIGHTNESS, 70).coerceIn(0, 100),
        rules = prefs.getString(KEY_RULES, null).let { raw ->
            when {
                raw == null -> A950LinkageRuleFactory.defaultRules()
                else -> A950LinkageRuleCodec.decode(raw) ?: emptyList()
            }
        },
    )

    companion object {
        const val PREFS_NAME = "wjx_led_a950"
        internal const val KEY_RULES = "rules"
        private const val KEY_RULES_CUSTOMIZED = "rules_customized"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEVICE_ADDRESS = "device_address"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_BRIGHTNESS = "brightness"
    }
}
