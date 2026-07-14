package com.byd.wjx.data.legal

import android.content.Context

object DisclaimerPreferences {
    private const val FILE = "wjx_disclaimer"
    private const val KEY_ACCEPTED_VERSION = "accepted_version"
    private const val KEY_ACCEPTED_AT = "accepted_at"
    private const val CURRENT_VERSION = 1

    fun isAccepted(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACCEPTED_VERSION, 0) >= CURRENT_VERSION
    }

    fun accept(context: Context) {
        context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACCEPTED_VERSION, CURRENT_VERSION)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }
}
