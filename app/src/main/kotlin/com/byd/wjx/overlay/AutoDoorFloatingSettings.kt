package com.byd.wjx.overlay

import android.content.Context

class AutoDoorFloatingSettings(context: Context) {
    private val prefs = storageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    var x: Int
        get() = prefs.getInt(KEY_X, Int.MIN_VALUE)
        set(value) {
            prefs.edit().putInt(KEY_X, value).apply()
        }

    var y: Int
        get() = prefs.getInt(KEY_Y, Int.MIN_VALUE)
        set(value) {
            prefs.edit().putInt(KEY_Y, value).apply()
        }

    var alpha: Float
        get() = prefs.getFloat(KEY_ALPHA, DEFAULT_ALPHA).coerceIn(ALPHA_MIN, ALPHA_MAX)
        set(value) {
            prefs.edit().putFloat(KEY_ALPHA, value.coerceIn(ALPHA_MIN, ALPHA_MAX)).apply()
        }

    var scale: Float
        get() = prefs.getFloat(KEY_SCALE, DEFAULT_SCALE).coerceIn(SCALE_MIN, SCALE_MAX)
        set(value) {
            prefs.edit().putFloat(KEY_SCALE, value.coerceIn(SCALE_MIN, SCALE_MAX)).apply()
        }

    var locked: Boolean
        get() = prefs.getBoolean(KEY_LOCKED, DEFAULT_LOCKED)
        set(value) {
            prefs.edit().putBoolean(KEY_LOCKED, value).apply()
        }

    fun savePosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    fun resetPosition() {
        savePosition(Int.MIN_VALUE, Int.MIN_VALUE)
    }

    companion object {
        const val ALPHA_MIN = 0.35f
        const val ALPHA_MAX = 1.0f
        const val DEFAULT_ALPHA = 0.94f
        const val SCALE_MIN = 0.75f
        const val SCALE_MAX = 1.35f
        const val DEFAULT_SCALE = 1.0f
        const val DEFAULT_LOCKED = true

        private const val PREFS_NAME = "wjx_auto_door_floating"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_ALPHA = "alpha"
        private const val KEY_SCALE = "scale"
        private const val KEY_LOCKED = "locked"

        private fun storageContext(context: Context): Context {
            val appContext = context.applicationContext
            return appContext.createDeviceProtectedStorageContext()
        }
    }
}
