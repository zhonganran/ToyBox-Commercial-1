package com.byd.wjx.cluster

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class KeyShortcutPressType(val storageValue: String, val label: String) {
    Short("short", "点按"),
    Double("double", "双击"),
    Long("long", "长按");

    companion object {
        fun fromStorage(value: String?): KeyShortcutPressType =
            entries.firstOrNull { it.storageValue == value } ?: Short
    }
}

enum class KeyShortcutActionType(val storageValue: String, val label: String) {
    PlaySound("play_sound", "播放音效"),
    RealtimeHailing("realtime_hailing", "实时喊话"),
    StreamingRearMirror("streaming_rear_mirror", "流媒体后视镜");

    companion object {
        fun fromStorage(value: String?): KeyShortcutActionType? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class KeyShortcutBinding(
    val id: String = UUID.randomUUID().toString(),
    val keyCode: Int = 0,
    val pressType: KeyShortcutPressType = KeyShortcutPressType.Short,
    val actionType: KeyShortcutActionType = KeyShortcutActionType.PlaySound,
    val soundPath: String = "",
    val soundName: String = "",
    val enabled: Boolean = true,
) {
    val isConfigured: Boolean
        get() = keyCode > 0 && when (actionType) {
            KeyShortcutActionType.PlaySound -> soundPath.isNotBlank()
            KeyShortcutActionType.RealtimeHailing -> true
            KeyShortcutActionType.StreamingRearMirror -> true
        }
}

class KeyShortcutSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<KeyShortcutBinding> {
        val raw = prefs.getString(KEY_BINDINGS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val actionType = KeyShortcutActionType.fromStorage(item.optString("actionType"))
                        ?: continue
                    add(
                        KeyShortcutBinding(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            keyCode = item.optInt("keyCode", 0),
                            pressType = KeyShortcutPressType.fromStorage(item.optString("pressType")),
                            actionType = actionType,
                            soundPath = item.optString("soundPath"),
                            soundName = item.optString("soundName"),
                            enabled = item.optBoolean("enabled", true),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun loadEnabled(): List<KeyShortcutBinding> =
        load().filter { it.enabled && it.isConfigured && isAssignable(it.keyCode) }

    fun save(bindings: List<KeyShortcutBinding>) {
        val normalized = bindings
            .filter { it.keyCode >= 0 }
            .distinctBy { it.keyCode to it.pressType }
        prefs.edit().putString(KEY_BINDINGS, normalized.toJson()).apply()
    }

    fun upsert(binding: KeyShortcutBinding): List<KeyShortcutBinding> {
        val next = load()
            .filterNot { it.id == binding.id || (it.keyCode == binding.keyCode && it.pressType == binding.pressType) } +
            binding
        save(next)
        return load()
    }

    fun delete(id: String): List<KeyShortcutBinding> {
        save(load().filterNot { it.id == id })
        return load()
    }

    private fun List<KeyShortcutBinding>.toJson(): String {
        val array = JSONArray()
        forEach { binding ->
            array.put(
                JSONObject()
                    .put("id", binding.id)
                    .put("keyCode", binding.keyCode)
                    .put("pressType", binding.pressType.storageValue)
                    .put("actionType", binding.actionType.storageValue)
                    .put("soundPath", binding.soundPath)
                    .put("soundName", binding.soundName)
                    .put("enabled", binding.enabled)
            )
        }
        return array.toString()
    }

    companion object {
        const val PREFS_NAME = "key_shortcuts"
        private const val KEY_BINDINGS = "bindings_json"
    }
}
