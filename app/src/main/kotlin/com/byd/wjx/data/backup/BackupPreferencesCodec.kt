package com.byd.wjx.data.backup

import org.json.JSONArray
import org.json.JSONObject

internal object BackupPreferencesCodec {
    fun serialize(data: Map<String, Map<String, Any?>>): String {
        val root = JSONObject()
        for ((fileName, entries) in data) {
            val fileObject = JSONObject()
            for ((key, rawValue) in entries) {
                if (rawValue == null) continue
                val entry = JSONObject()
                when (rawValue) {
                    is String -> entry.putTyped(T_STRING, rawValue)
                    is Int -> entry.putTyped(T_INT, rawValue)
                    is Long -> entry.putTyped(T_LONG, rawValue)
                    is Float -> entry.putTyped(T_FLOAT, rawValue.toDouble())
                    is Boolean -> entry.putTyped(T_BOOLEAN, rawValue)
                    is Set<*> -> {
                        require(rawValue.all { it is String }) {
                            "Unsupported preference StringSet value for $fileName/$key"
                        }
                        val values = JSONArray()
                        rawValue.forEach { values.put(it as String) }
                        entry.putTyped(T_STRING_SET, values)
                    }
                    else -> throw IllegalArgumentException(
                        "Unsupported preference value type for $fileName/$key: " +
                            rawValue::class.java.name,
                    )
                }
                fileObject.put(key, entry)
            }
            root.put(fileName, fileObject)
        }
        return root.toString()
    }

    fun deserialize(json: String): Map<String, Map<String, Any?>> {
        val result = mutableMapOf<String, Map<String, Any?>>()
        val root = JSONObject(json)
        for (fileName in root.keys()) {
            val fileObject = root.getJSONObject(fileName)
            val entries = mutableMapOf<String, Any?>()
            for (key in fileObject.keys()) {
                val entry = fileObject.getJSONObject(key)
                entries[key] = when (val type = entry.getString(TYPE_KEY)) {
                    T_STRING -> entry.getString(VALUE_KEY)
                    T_INT -> entry.getInt(VALUE_KEY)
                    T_LONG -> entry.getLong(VALUE_KEY)
                    T_FLOAT -> entry.getDouble(VALUE_KEY).toFloat()
                    T_BOOLEAN -> entry.getBoolean(VALUE_KEY)
                    T_STRING_SET -> entry.getJSONArray(VALUE_KEY).toStringSet()
                    else -> throw IllegalArgumentException(
                        "Unsupported preference type tag for $fileName/$key: $type",
                    )
                }
            }
            result[fileName] = entries
        }
        return result
    }

    private fun JSONObject.putTyped(type: String, value: Any) {
        put(TYPE_KEY, type)
        put(VALUE_KEY, value)
    }

    private fun JSONArray.toStringSet(): Set<String> = buildSet {
        for (index in 0 until length()) add(getString(index))
    }

    private const val TYPE_KEY = "t"
    private const val VALUE_KEY = "v"
    private const val T_STRING = "String"
    private const val T_INT = "Int"
    private const val T_LONG = "Long"
    private const val T_FLOAT = "Float"
    private const val T_BOOLEAN = "Boolean"
    private const val T_STRING_SET = "StringSet"
}
