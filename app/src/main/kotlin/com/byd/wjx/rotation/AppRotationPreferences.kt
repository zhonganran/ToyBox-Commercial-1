package com.byd.wjx.rotation

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AppRotationTarget(
    val packageName: String,
    val label: String,
)

data class AppRotationConfig(
    val enabled: Boolean = false,
    val targets: List<AppRotationTarget> = emptyList(),
) {
    fun shouldForceLandscape(packageName: String?): Boolean =
        enabled && packageName != null && targets.any { it.packageName == packageName }
}

@Singleton
class AppRotationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<AppRotationConfig> = _config.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_ENABLED || key == KEY_TARGETS) publish()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        publish()
    }

    fun addTarget(packageName: String, label: String) {
        val normalizedPackage = packageName.trim()
        if (normalizedPackage.isEmpty()) return
        val next = _config.value.targets
            .filterNot { it.packageName == normalizedPackage }
            .plus(AppRotationTarget(normalizedPackage, label.trim().ifBlank { normalizedPackage }))
            .sortedBy { it.label.lowercase() }
        writeTargets(next)
    }

    fun removeTarget(packageName: String) {
        writeTargets(_config.value.targets.filterNot { it.packageName == packageName })
    }

    private fun writeTargets(targets: List<AppRotationTarget>) {
        val array = JSONArray()
        targets.forEach { target ->
            array.put(
                JSONObject()
                    .put("package", target.packageName)
                    .put("label", target.label),
            )
        }
        preferences.edit().putString(KEY_TARGETS, array.toString()).apply()
        publish()
    }

    private fun publish() {
        _config.value = readConfig()
    }

    private fun readConfig(): AppRotationConfig = AppRotationConfig(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        targets = parseTargets(preferences.getString(KEY_TARGETS, null)),
    )

    private fun parseTargets(raw: String?): List<AppRotationTarget> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val packageName = item.optString("package").trim()
                if (packageName.isBlank()) continue
                add(
                    AppRotationTarget(
                        packageName = packageName,
                        label = item.optString("label").trim().ifBlank { packageName },
                    ),
                )
            }
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    companion object {
        const val PREFS_NAME = "wjx_app_rotation"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TARGETS = "targets"
    }
}
