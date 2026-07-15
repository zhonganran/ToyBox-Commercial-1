package com.byd.wjx.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Device-local cache for remotely supplied after-sales QR metadata. */
@Singleton
class ServiceQrCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readJson(): String? = preferences.getString(KEY_JSON, null)

    fun writeJson(json: String) {
        preferences.edit().putString(KEY_JSON, json).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "service_qr_cache"
        private const val KEY_JSON = "service_qr_json"
    }
}
