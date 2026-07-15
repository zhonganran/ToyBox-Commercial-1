package com.byd.wjx.service

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BootDiagnosticsSnapshot(
    val lastBootTimestamp: Long,
    val lastBootMethod: String,
    val lastBootAction: String?,
    val chainLog: String?,
)

/** Single schema and storage-context owner for direct-boot startup diagnostics. */
object BootDiagnosticsStore {
    fun recordBootEvent(context: Context, action: String): Boolean = runCatching {
        preferences(context).edit()
            .putLong(KEY_LAST_BOOT_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_LAST_BOOT_ACTION, action)
            .apply()
    }.isSuccess

    fun updateBootMethod(context: Context, method: String): Boolean = runCatching {
        preferences(context).edit()
            .putString(KEY_LAST_BOOT_METHOD, method)
            .apply()
    }.isSuccess

    @Synchronized
    fun appendChainEntry(context: Context, entry: String): Boolean = runCatching {
        val preferences = preferences(context)
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val existing = preferences.getString(KEY_CHAIN_LOG, "").orEmpty()
        val retainedLines = existing.lineSequence()
            .filter(String::isNotBlank)
            .toList()
            .takeLast(MAX_CHAIN_ENTRIES - 1)
        preferences.edit()
            .putString(KEY_CHAIN_LOG, (retainedLines + "$timestamp $entry").joinToString("\n"))
            .apply()
    }.isSuccess

    fun read(context: Context): BootDiagnosticsSnapshot? = runCatching {
        val preferences = preferences(context)
        BootDiagnosticsSnapshot(
            lastBootTimestamp = preferences.getLong(KEY_LAST_BOOT_TIMESTAMP, 0L),
            lastBootMethod = preferences.getString(KEY_LAST_BOOT_METHOD, "?") ?: "?",
            lastBootAction = preferences.getString(KEY_LAST_BOOT_ACTION, null),
            chainLog = preferences.getString(KEY_CHAIN_LOG, null)?.takeIf(String::isNotBlank),
        )
    }.getOrNull()

    private fun preferences(context: Context) =
        BootState.deviceProtectedContext(context).getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    const val PREFERENCES_NAME = "boot_log"
    private const val KEY_LAST_BOOT_TIMESTAMP = "last_boot_ts"
    private const val KEY_LAST_BOOT_METHOD = "last_boot_method"
    private const val KEY_LAST_BOOT_ACTION = "last_boot_action"
    private const val KEY_CHAIN_LOG = "chain_log"
    private const val MAX_CHAIN_ENTRIES = 20
}
