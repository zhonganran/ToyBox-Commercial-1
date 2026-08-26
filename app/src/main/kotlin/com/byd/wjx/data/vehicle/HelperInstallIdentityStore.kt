package com.byd.wjx.data.vehicle

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Stable across APK updates and regenerated after app data is removed by uninstall. */
@Singleton
class HelperInstallIdentityStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun current(): String {
        preferences.getString(KEY_INSTALL_ID, null)?.takeIf(INSTALL_ID::matches)?.let { return it }
        return UUID.randomUUID().toString().also { generated ->
            preferences.edit().putString(KEY_INSTALL_ID, generated).commit()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "helper_runtime_identity"
        const val KEY_INSTALL_ID = "install_id"
        val INSTALL_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }
}
