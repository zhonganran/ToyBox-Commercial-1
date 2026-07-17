package com.byd.wjx.xiaozhi.wake

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class XiaozhiWakePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<XiaozhiWakeConfig> = _config.asStateFlow()

    fun save(enabled: Boolean, phrase: String) {
        val normalizedPhrase = phrase.trim().ifBlank { XiaozhiWakeConfig.DEFAULT_WAKE_PHRASE }
        val next = XiaozhiWakeConfig(enabled = enabled, phrase = normalizedPhrase)
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_PHRASE, next.phrase)
            .apply()
        _config.value = next
    }

    private fun load(): XiaozhiWakeConfig = XiaozhiWakeConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        phrase = prefs.getString(KEY_PHRASE, XiaozhiWakeConfig.DEFAULT_WAKE_PHRASE)
            .orEmpty()
            .ifBlank { XiaozhiWakeConfig.DEFAULT_WAKE_PHRASE },
    )

    companion object {
        const val PREFS_NAME = "wjx_xiaozhi_wake"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PHRASE = "phrase"
    }
}
