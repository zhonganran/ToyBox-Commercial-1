package com.byd.wjx.audio

import android.content.Context

enum class LockSoundEvent(val configKey: String, val title: String) {
    Unlock("unlock", "解锁音效"),
    Lock("lock", "锁车音效"),
    Sentry("sentry", "哨兵音效");
}

data class LockSoundConfig(
    val unlockEnabled: Boolean = false,
    val unlockPath: String = "",
    val lockEnabled: Boolean = false,
    val lockPath: String = "",
    val sentryEnabled: Boolean = false,
    val sentryPath: String = "",
) {
    fun enabled(event: LockSoundEvent): Boolean =
        when (event) {
            LockSoundEvent.Unlock -> unlockEnabled
            LockSoundEvent.Lock -> lockEnabled
            LockSoundEvent.Sentry -> sentryEnabled
        }

    fun path(event: LockSoundEvent): String =
        when (event) {
            LockSoundEvent.Unlock -> unlockPath
            LockSoundEvent.Lock -> lockPath
            LockSoundEvent.Sentry -> sentryPath
        }
}

class LockSoundSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs
        get() = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun snapshot(): LockSoundConfig =
        LockSoundConfig(
            unlockEnabled = prefs.getBoolean(KEY_UNLOCK_ENABLED, false),
            unlockPath = prefs.getString(KEY_UNLOCK_PATH, "").orEmpty(),
            lockEnabled = prefs.getBoolean(KEY_LOCK_ENABLED, false),
            lockPath = prefs.getString(KEY_LOCK_PATH, "").orEmpty(),
            sentryEnabled = prefs.getBoolean(KEY_SENTRY_ENABLED, false),
            sentryPath = prefs.getString(KEY_SENTRY_PATH, "").orEmpty(),
        )

    fun setEnabled(event: LockSoundEvent, enabled: Boolean) {
        prefs.edit().putBoolean(enabledKey(event), enabled).apply()
    }

    fun setSoundPath(event: LockSoundEvent, path: String) {
        prefs.edit().putString(pathKey(event), path).apply()
    }

    fun clearIfPath(path: String) {
        val config = snapshot()
        val edit = prefs.edit()
        var changed = false
        LockSoundEvent.entries.forEach { event ->
            if (config.path(event) == path) {
                edit.putBoolean(enabledKey(event), false)
                edit.putString(pathKey(event), "")
                changed = true
            }
        }
        if (changed) edit.apply()
    }

    private fun enabledKey(event: LockSoundEvent): String =
        when (event) {
            LockSoundEvent.Unlock -> KEY_UNLOCK_ENABLED
            LockSoundEvent.Lock -> KEY_LOCK_ENABLED
            LockSoundEvent.Sentry -> KEY_SENTRY_ENABLED
        }

    private fun pathKey(event: LockSoundEvent): String =
        when (event) {
            LockSoundEvent.Unlock -> KEY_UNLOCK_PATH
            LockSoundEvent.Lock -> KEY_LOCK_PATH
            LockSoundEvent.Sentry -> KEY_SENTRY_PATH
        }

    companion object {
        private const val PREF_NAME = "lock_sound"
        private const val KEY_UNLOCK_ENABLED = "unlock_enabled"
        private const val KEY_UNLOCK_PATH = "unlock_path"
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_LOCK_PATH = "lock_path"
        private const val KEY_SENTRY_ENABLED = "sentry_enabled"
        private const val KEY_SENTRY_PATH = "sentry_path"
    }
}
