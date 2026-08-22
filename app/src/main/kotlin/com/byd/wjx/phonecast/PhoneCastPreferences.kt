package com.byd.wjx.phonecast

import android.content.Context

data class PhoneCastSnapshot(
    val url: String,
    val scaleMode: PhoneCastScaleMode,
)

class PhoneCastPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(): PhoneCastSnapshot = PhoneCastSnapshot(
        url = prefs.getString(KEY_URL, "").orEmpty(),
        scaleMode = PhoneCastScaleMode.fromStored(prefs.getString(KEY_SCALE_MODE, null)),
    )

    fun save(url: String, scaleMode: PhoneCastScaleMode) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_SCALE_MODE, scaleMode.storedValue)
            .apply()
    }

    fun setScaleMode(scaleMode: PhoneCastScaleMode) {
        prefs.edit().putString(KEY_SCALE_MODE, scaleMode.storedValue).apply()
    }

    companion object {
        const val PREFS_NAME = "phone_cast"
        private const val KEY_URL = "rtsp_url"
        private const val KEY_SCALE_MODE = "scale_mode"
    }
}
