package com.byd.wjx.data.backup

import android.content.Context

/** One-shot status shown after the process restart required by a restore operation. */
object BackupRestoreNoticeStore {
    const val PREFS_NAME = "backup_restore_notice"

    fun save(context: Context, message: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MESSAGE, message.take(MAX_MESSAGE_LENGTH))
            .commit()
    }

    fun consume(context: Context): String? {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val message = preferences.getString(KEY_MESSAGE, null)?.takeIf(String::isNotBlank)
        if (message != null) preferences.edit().remove(KEY_MESSAGE).commit()
        return message
    }

    private const val KEY_MESSAGE = "message"
    private const val MAX_MESSAGE_LENGTH = 500
}
