package com.byd.wjx.data.local

import android.content.Context

class LocalePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getLanguage(): String? = prefs.getString(KEY_LANG, null)

    fun setLanguage(lang: String) {
        // SharedPreferences updates its in-memory value before scheduling the disk write,
        // so same-process locale bootstrap reads the new language immediately.
        prefs.edit().putString(KEY_LANG, lang).apply()
    }

    companion object {
        const val FILE = "wjx_locale"
        const val KEY_LANG = "app_language"
    }
}
