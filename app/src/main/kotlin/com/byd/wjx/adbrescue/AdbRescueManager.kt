package com.byd.wjx.adbrescue

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapted from BYD-ADB-Unlock / BYD ADB Rescue by Stephan Schreiber (MIT).
 * Writes are always followed by a readback; a successful call is not treated as proof of effect.
 */
@Singleton
class AdbRescueManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext

    @Volatile
    internal var propertyAccess: AdbRescueSystemPropertyAccess =
        ReflectiveAdbRescueSystemPropertyAccess

    fun hasWriteSecureSettingsPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun inspect(trigger: String, runWriteTest: Boolean = true): AdbRescueReport =
        buildReport(trigger, enable = false, runWriteTest = runWriteTest)

    @Synchronized
    fun enableBoth(trigger: String, runWriteTest: Boolean = true): AdbRescueReport =
        buildReport(trigger, enable = true, runWriteTest = runWriteTest)

    @Synchronized
    fun disableBoth(trigger: String): AdbRescueReport {
        val permission = hasWriteSecureSettingsPermission()
        if (permission) {
            runCatching { Settings.Global.putInt(appContext.contentResolver, ADB_ENABLED, 0) }
                .onFailure { WjxFileLogger.w(TAG, "Unable to disable standard ADB: ${it.message}") }
        }
        runCatching { propertyAccess.set(BYD_WIRELESS_CONNECTION, "0") }
            .onFailure { WjxFileLogger.w(TAG, "Unable to disable BYD connection mode: ${it.message}") }
        runCatching { propertyAccess.set(BYD_WIRELESS_ENABLED, "false") }
            .onFailure { WjxFileLogger.w(TAG, "Unable to disable BYD persistent mode: ${it.message}") }
        return buildReport(trigger, enable = false, runWriteTest = false)
    }

    fun recordEvent(message: String) {
        record(message)
    }

    fun recordRescueProgress(
        report: AdbRescueReport,
        attempt: Int,
        remainingSeconds: Int,
    ): AdbRescueDiagnosticWriteResult {
        if (!hasWriteSecureSettingsPermission()) {
            return AdbRescueDiagnosticWriteResult(false, false, false)
        }
        val resolver = appContext.contentResolver
        return AdbRescueDiagnosticWriteResult(
            tickWritten = runCatching {
                Settings.Global.putInt(resolver, KEY_RESCUE_TICK, attempt)
            }.getOrDefault(false),
            remainingWritten = runCatching {
                Settings.Global.putInt(resolver, KEY_RESCUE_REMAINING, remainingSeconds)
            }.getOrDefault(false),
            resultWritten = runCatching {
                Settings.Global.putString(resolver, KEY_RESCUE_RESULT, report.compact())
            }.getOrDefault(false),
        )
    }

    private fun buildReport(
        trigger: String,
        enable: Boolean,
        runWriteTest: Boolean,
    ): AdbRescueReport {
        val permission = hasWriteSecureSettingsPermission()
        val standard = standardAdbResult(enable, permission)
        val wirelessEnabled = propertyResult(
            key = BYD_WIRELESS_ENABLED,
            desiredValue = "true",
            enable = enable,
            matcher = ::isTruthyAdbProperty,
        )
        val wirelessConnection = propertyResult(
            key = BYD_WIRELESS_CONNECTION,
            desiredValue = "1",
            enable = enable,
            matcher = { it.trim() == "1" },
        )
        val report = AdbRescueReport(
            trigger = trigger,
            userId = currentUserId(),
            uid = Process.myUid(),
            writeSecureSettingsGranted = permission,
            standardAdb = standard,
            bydWirelessEnabled = wirelessEnabled,
            bydWirelessConnection = wirelessConnection,
            settingsWriteTestSucceeded = runWriteTest && permission && harmlessSettingsWriteTest(),
        )
        record("$trigger permission=$permission ${report.compact()}")
        return report
    }

    private fun standardAdbResult(enable: Boolean, permission: Boolean): AdbRescueValueResult {
        val before = readAdbEnabled()
        val alreadyEnabled = before == 1
        if (!enable || alreadyEnabled || !permission) {
            return AdbRescueValueResult(
                key = ADB_ENABLED,
                desiredValue = "1",
                before = before.toString(),
                writeAttempted = false,
                writeAccepted = null,
                after = before.toString(),
                enabled = alreadyEnabled,
            )
        }
        val accepted = runCatching {
            Settings.Global.putInt(appContext.contentResolver, ADB_ENABLED, 1)
        }.getOrDefault(false)
        val after = readAdbEnabled()
        return AdbRescueValueResult(
            key = ADB_ENABLED,
            desiredValue = "1",
            before = before.toString(),
            writeAttempted = true,
            writeAccepted = accepted,
            after = after.toString(),
            enabled = after == 1,
        )
    }

    private fun propertyResult(
        key: String,
        desiredValue: String,
        enable: Boolean,
        matcher: (String) -> Boolean,
    ): AdbRescueValueResult {
        val before = propertyAccess.get(key, UNKNOWN_PROPERTY)
        val alreadyEnabled = matcher(before)
        if (!enable || alreadyEnabled) {
            return AdbRescueValueResult(
                key = key,
                desiredValue = desiredValue,
                before = before,
                writeAttempted = false,
                writeAccepted = null,
                after = before,
                enabled = alreadyEnabled,
            )
        }
        val accepted = propertyAccess.set(key, desiredValue)
        val after = propertyAccess.get(key, UNKNOWN_PROPERTY)
        return AdbRescueValueResult(
            key = key,
            desiredValue = desiredValue,
            before = before,
            writeAttempted = true,
            writeAccepted = accepted,
            after = after,
            enabled = matcher(after),
        )
    }

    private fun harmlessSettingsWriteTest(): Boolean = runCatching {
        val resolver = appContext.contentResolver
        val write = Settings.Global.putInt(resolver, KEY_TEST, 4242)
        val read = Settings.Global.getInt(resolver, KEY_TEST, -1)
        val delete = Settings.Global.putString(resolver, KEY_TEST, null)
        write && read == 4242 && delete
    }.getOrDefault(false)

    private fun readAdbEnabled(): Int = runCatching {
        Settings.Global.getInt(appContext.contentResolver, ADB_ENABLED, UNKNOWN_SETTING)
    }.getOrDefault(UNKNOWN_SETTING)

    private fun record(message: String) {
        runCatching { WjxFileLogger.i(TAG, message) }
        runCatching {
            val storage = appContext.createDeviceProtectedStorageContext()
            val preferences = storage.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val entry = String.format(
                Locale.US,
                "%d user=%d %s\n---\n",
                System.currentTimeMillis(),
                currentUserId(),
                message,
            )
            val history = (preferences.getString(KEY_HISTORY, "").orEmpty() + entry)
                .takeLast(HISTORY_LIMIT)
            preferences.edit().putString(KEY_HISTORY, history).apply()
        }.onFailure { WjxFileLogger.w(TAG, "history write failed: ${it.message}") }
    }

    private fun currentUserId(): Int = Process.myUid() / PER_USER_RANGE

    companion object {
        const val ADB_ENABLED = "adb_enabled"
        const val BYD_WIRELESS_ENABLED = "persist.sys.adb.wiress.enable"
        const val BYD_WIRELESS_CONNECTION = "sys.connect.adb.wiress"
        const val KEY_RESCUE_TICK = "wjx_adb_rescue_tick"
        const val KEY_RESCUE_REMAINING = "wjx_adb_rescue_remaining"
        const val KEY_RESCUE_RESULT = "wjx_adb_rescue_last_result"

        private const val TAG = "AdbRescue"
        private const val KEY_TEST = "wjx_adb_rescue_permission_test"
        private const val UNKNOWN_PROPERTY = "<unavailable>"
        private const val UNKNOWN_SETTING = -99
        private const val PREFERENCES = "adb_rescue_diagnostics"
        private const val KEY_HISTORY = "history"
        private const val HISTORY_LIMIT = 32_768
        private const val PER_USER_RANGE = 100_000
    }
}
