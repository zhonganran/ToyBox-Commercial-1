package com.byd.wjx.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoParkingSoundSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs
        get() = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    var soundPath: String
        get() = prefs.getString(KEY_SOUND_PATH, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SOUND_PATH, value).apply()
        }

    fun clearIfPath(path: String) {
        if (soundPath == path) {
            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .putString(KEY_SOUND_PATH, "")
                .apply()
        }
    }

    companion object {
        private const val PREF_NAME = "auto_parking_sound"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SOUND_PATH = "sound_path"
    }
}
