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

    init {
        migrateLegacyBinding()
    }

    private val _state = MutableStateFlow(readSnapshot())
    val state: StateFlow<LedPreferenceSnapshot> = _state.asStateFlow()

    fun setSelectedProtocol(protocol: LedProtocolKind) = update {
        putString(KEY_SELECTED_PROTOCOL, protocol.name)
    }

    fun setEnabled(protocol: LedProtocolKind, enabled: Boolean) = update {
        putBoolean(key(protocol, FIELD_ENABLED), enabled)
    }

    fun bindDetectedDevice(
        protocol: LedProtocolKind,
        address: String,
        name: String,
        enable: Boolean = true,
    ) = update {
        putString(KEY_SELECTED_PROTOCOL, protocol.name)
        putString(key(protocol, FIELD_DEVICE_ADDRESS), address)
        putString(key(protocol, FIELD_DEVICE_NAME), name)
        putBoolean(key(protocol, FIELD_ENABLED), enable)
    }

    fun clearDevice(protocol: LedProtocolKind) = update {
        remove(key(protocol, FIELD_DEVICE_ADDRESS))
        remove(key(protocol, FIELD_DEVICE_NAME))
    }

    fun setBrightness(protocol: LedProtocolKind, percent: Int) = update {
        putInt(key(protocol, FIELD_BRIGHTNESS), percent.coerceIn(0, 100))
    }

    fun saveRule(rule: LedLinkageRule): Boolean {
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

    fun deleteRule(id: String) = setRules(_state.value.rules.filterNot { it.id == id })

    fun setRuleEnabled(id: String, enabled: Boolean) = setRules(
        _state.value.rules.map { if (it.id == id) it.copy(enabled = enabled) else it }
    )

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

    private fun setRules(rules: List<LedLinkageRule>) = update {
        putString(KEY_RULES_V2, LedLinkageRuleCodec.encode(rules))
    }

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = readSnapshot()
    }

    private fun readSnapshot(): LedPreferenceSnapshot = LedPreferenceSnapshot(
        selectedProtocol = prefs.getString(KEY_SELECTED_PROTOCOL, null)
            ?.let { stored -> LedProtocolKind.entries.firstOrNull { it.name == stored } }
            ?: LedProtocolKind.IPIXEL_FA,
        ipixelFa = readProtocol(LedProtocolKind.IPIXEL_FA),
        a950 = readProtocol(LedProtocolKind.A950),
        rules = prefs.getString(KEY_RULES_V2, null)
            ?.let(LedLinkageRuleCodec::decode)
            ?: LedLinkageDefaults.rules,
    )

    private fun readProtocol(protocol: LedProtocolKind): LedProtocolPreference = LedProtocolPreference(
        enabled = prefs.getBoolean(key(protocol, FIELD_ENABLED), false),
        deviceAddress = prefs.getString(key(protocol, FIELD_DEVICE_ADDRESS), "").orEmpty(),
        deviceName = prefs.getString(key(protocol, FIELD_DEVICE_NAME), "").orEmpty(),
        brightness = prefs.getInt(key(protocol, FIELD_BRIGHTNESS), 70).coerceIn(0, 100),
    )

    private fun migrateLegacyBinding() {
        if (prefs.getBoolean(KEY_PROTOCOL_SPLIT_MIGRATED, false)) return
        val storedKind = prefs.getString(LEGACY_DEVICE_KIND, null)
        val protocol = if (storedKind == LedProtocolKind.A950.name) {
            LedProtocolKind.A950
        } else {
            LedProtocolKind.IPIXEL_FA
        }
        val editor = prefs.edit()
            .putString(KEY_SELECTED_PROTOCOL, protocol.name)
            .putBoolean(key(protocol, FIELD_ENABLED), prefs.getBoolean(LEGACY_ENABLED, false))
            .putString(
                key(protocol, FIELD_DEVICE_ADDRESS),
                prefs.getString(LEGACY_DEVICE_ADDRESS, "").orEmpty(),
            )
            .putString(
                key(protocol, FIELD_DEVICE_NAME),
                prefs.getString(LEGACY_DEVICE_NAME, "").orEmpty(),
            )
            .putInt(key(protocol, FIELD_BRIGHTNESS), prefs.getInt(LEGACY_BRIGHTNESS, 70))
            .putBoolean(KEY_PROTOCOL_SPLIT_MIGRATED, true)
        listOf(
            LEGACY_ENABLED,
            LEGACY_DEVICE_ADDRESS,
            LEGACY_DEVICE_NAME,
            LEGACY_DEVICE_KIND,
            LEGACY_BRIGHTNESS,
        ).forEach(editor::remove)
        editor.commit()
    }

    private fun key(protocol: LedProtocolKind, field: String): String =
        "${protocol.name.lowercase()}_$field"

    companion object {
        const val PREFS_NAME = "wjx_led_link"
        internal const val KEY_SELECTED_PROTOCOL = "selected_protocol"
        internal const val KEY_PROTOCOL_SPLIT_MIGRATED = "protocol_split_migrated"
        internal const val KEY_RULES_V2 = "rules_v2"

        private const val FIELD_ENABLED = "enabled"
        private const val FIELD_DEVICE_ADDRESS = "device_address"
        private const val FIELD_DEVICE_NAME = "device_name"
        private const val FIELD_BRIGHTNESS = "brightness"

        private const val LEGACY_ENABLED = "enabled"
        private const val LEGACY_DEVICE_ADDRESS = "device_address"
        private const val LEGACY_DEVICE_NAME = "device_name"
        private const val LEGACY_DEVICE_KIND = "device_kind"
        private const val LEGACY_BRIGHTNESS = "brightness"
    }
}
