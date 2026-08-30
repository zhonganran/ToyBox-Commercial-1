package com.byd.wjx.adbrescue

data class AdbRescueValueResult(
    val key: String,
    val desiredValue: String,
    val before: String,
    val writeAttempted: Boolean,
    val writeAccepted: Boolean?,
    val after: String,
    val enabled: Boolean,
)

data class AdbRescueReport(
    val trigger: String,
    val userId: Int,
    val uid: Int,
    val writeSecureSettingsGranted: Boolean,
    val standardAdb: AdbRescueValueResult,
    val bydWirelessEnabled: AdbRescueValueResult,
    val bydWirelessConnection: AdbRescueValueResult,
    val settingsWriteTestSucceeded: Boolean,
    val recordedAtMs: Long = System.currentTimeMillis(),
) {
    val allModesEnabled: Boolean
        get() = standardAdb.enabled && bydWirelessEnabled.enabled && bydWirelessConnection.enabled

    val anyModeEnabled: Boolean
        get() = standardAdb.enabled || bydWirelessEnabled.enabled || bydWirelessConnection.enabled

    val allModesDisabled: Boolean
        get() = !anyModeEnabled

    fun compact(): String = buildString {
        append("adb_enabled{")
        append(standardAdb.compact())
        append("} persist.sys.adb.wiress.enable{")
        append(bydWirelessEnabled.compact())
        append("} sys.connect.adb.wiress{")
        append(bydWirelessConnection.compact())
        append('}')
    }
}

data class AdbRescueRepairOutcome(
    val report: AdbRescueReport,
    val helperAvailable: Boolean,
    val permissionGrantAttempted: Boolean,
    val permissionGrantSucceeded: Boolean,
    val helperModeRepairAttempted: Boolean,
    val helperModeRepairSucceeded: Boolean,
)

data class AdbRescueDiagnosticWriteResult(
    val tickWritten: Boolean,
    val remainingWritten: Boolean,
    val resultWritten: Boolean,
) {
    val allWritten: Boolean
        get() = tickWritten && remainingWritten && resultWritten
}

internal fun AdbRescueValueResult.compact(): String =
    "before=$before attempted=$writeAttempted accepted=${writeAccepted ?: "n/a"} after=$after"

internal fun isTruthyAdbProperty(value: String): Boolean = when (value.trim().lowercase()) {
    "1", "true", "y", "yes", "on" -> true
    else -> false
}
