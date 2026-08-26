package com.byd.wjx.data.backup

import android.content.Context
import android.util.Log
import com.byd.wjx.log.WjxFileLogger

internal class BackupPreferenceStore(
    private val context: Context,
    val fileNames: List<String>,
    private val deviceProtectedFileNames: Set<String>,
) {
    init {
        require(fileNames.distinct().size == fileNames.size) {
            "Preference files must not contain duplicates"
        }
        require(deviceProtectedFileNames.all { it in fileNames }) {
            "Device-protected preference files must also be included in fileNames"
        }
    }

    fun collectNonEmpty(): Map<String, Map<String, Any?>> = buildMap {
        for (fileName in fileNames) {
            val entries = preferencesContext(fileName)
                .getSharedPreferences(fileName, Context.MODE_PRIVATE)
                .all
            if (entries.isNotEmpty()) put(fileName, entries)
        }
    }

    fun requireKnownFiles(preferences: Map<String, Map<String, Any?>>) {
        val unexpectedFiles = preferences.keys - fileNames.toSet()
        if (unexpectedFiles.isNotEmpty()) {
            throw IllegalStateException("备份包含当前版本不识别的配置文件")
        }
    }

    fun replaceAll(preferences: Map<String, Map<String, Any?>>) {
        for (fileName in fileNames) {
            val editor = preferencesContext(fileName)
                .getSharedPreferences(fileName, Context.MODE_PRIVATE)
                .edit()
                .clear()
            preferences[fileName].orEmpty().forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(key, value as Set<String>)
                    }
                }
            }
            if (!editor.commit()) {
                Log.w(TAG, "Failed to commit prefs file during restore: $fileName")
                WjxFileLogger.w(TAG, "Failed to commit prefs file during restore: $fileName")
                throw IllegalStateException("配置恢复写入失败：$fileName")
            }
        }
    }

    private fun preferencesContext(fileName: String): Context =
        if (fileName in deviceProtectedFileNames) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

    private companion object {
        const val TAG = "BackupManager"
    }
}
