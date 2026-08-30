package com.byd.wjx.adbrescue

internal object AdbRescueShellCommands {
    fun grantWriteSecureSettings(packageName: String, userId: Int): String {
        require(packageName.matches(PACKAGE_NAME)) { "Invalid package name" }
        require(userId >= 0) { "Invalid Android user" }
        return buildString {
            append("pm grant --user 0 ")
            append(packageName)
            append(" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1 || true; ")
            append("pm grant --user ")
            append(userId)
            append(' ')
            append(packageName)
            append(" android.permission.WRITE_SECURE_SETTINGS")
        }
    }

    const val ENABLE_BOTH_MODES =
        "status=0; " +
            "settings put global adb_enabled 1 || status=1; " +
            "setprop persist.sys.adb.wiress.enable true || status=1; " +
            "setprop sys.connect.adb.wiress 1 || status=1; " +
            "exit \$status"

    const val DISABLE_BOTH_MODES =
        "status=0; " +
            "setprop sys.connect.adb.wiress 0 || status=1; " +
            "setprop persist.sys.adb.wiress.enable false || status=1; " +
            "settings put global adb_enabled 0 || status=1; " +
            "exit \$status"

    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
}
