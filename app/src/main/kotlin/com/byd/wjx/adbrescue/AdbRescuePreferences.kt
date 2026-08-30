package com.byd.wjx.adbrescue

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbRescuePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isGuardEnabled(): Boolean = preferences.getBoolean(KEY_GUARD_ENABLED, false)

    fun setGuardEnabled(enabled: Boolean): Boolean =
        preferences.edit().putBoolean(KEY_GUARD_ENABLED, enabled).commit()

    companion object {
        const val PREFS_NAME = "adb_rescue_preferences"
        private const val KEY_GUARD_ENABLED = "guard_enabled"
    }
}
