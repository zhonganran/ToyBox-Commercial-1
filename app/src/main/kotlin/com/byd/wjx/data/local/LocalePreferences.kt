package com.byd.wjx.data.local

import android.content.Context

class LocalePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getLanguage(): String? = prefs.getString(KEY_LANG, null)

    fun setLanguage(lang: String) {
        // commit = true: synchronous write so bootstrap never races with a read.
        prefs.edit().putString(KEY_LANG, lang).commit()
    }

    companion object {
        const val FILE = "wjx_locale"
        const val KEY_LANG = "app_language"
    }
}
