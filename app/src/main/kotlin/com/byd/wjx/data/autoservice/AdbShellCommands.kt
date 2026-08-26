package com.byd.wjx.data.autoservice

/** Strict command catalog for the small set of shell operations the app permits. */
internal object AdbShellCommands {
    const val HELPER_PROCESS_NAME = "wjx_helper"

    fun getProperty(name: String): String {
        require(name.matches(PROPERTY_NAME)) { "AdbOnDeviceClient: refused getprop name: $name" }
        return "getprop $name"
    }

    fun getSecureSetting(name: String): String {
        require(name.matches(SETTING_NAME)) {
            "AdbOnDeviceClient: refused secure setting name: $name"
        }
        return "settings get secure $name"
    }

    fun grantUsageStats(packageName: String): String {
        require(packageName.matches(APP_PACKAGE_NAME)) {
            "grantUsageStatsAppop: refused package $packageName"
        }
        return "appops set $packageName GET_USAGE_STATS allow"
    }

    fun grantMusicNotificationListener(): String {
        val component = "com.byd.wjx/com.byd.wjx.lyrics.MusicNotificationListenerService"
        val quoted = shellQuote(component)
        return "current=\$(settings get secure enabled_notification_listeners 2>/dev/null); " +
            "case \"\$current\" in *$component*) ;; null|\"\") " +
            "settings put secure enabled_notification_listeners $quoted ;; " +
            "*) settings put secure enabled_notification_listeners \"\$current:$component\" ;; esac; " +
            "cmd notification allow_listener $quoted 2>/dev/null || true; echo ok"
    }

    fun killHelper(): String = "pkill -9 -f $HELPER_PROCESS_NAME 2>/dev/null; sleep 0.5"

    fun helperHeartbeat(): String = "ps -A -o NAME"

    fun spawnHelper(
        packageCodePath: String,
        callerUid: Int,
        helperLogPath: String,
        helperLogDirectory: String,
    ): String {
        val quotedLogPath = shellQuote(helperLogPath)
        val classpathScript = helperClasspath(packageCodePath)
        val startScript = shellQuote(helperStartScript(classpathScript, quotedLogPath, callerUid))
        return "mkdir -p ${shellQuote(helperLogDirectory)}; " +
            "printf '%s\\n' '[wjx_helper spawn]' >>$quotedLogPath; " +
            "printf '%s\\n' $startScript >/data/local/tmp/wjx_helper_start.sh; " +
            "chmod 755 /data/local/tmp/wjx_helper_start.sh; " +
            "sh /data/local/tmp/wjx_helper_start.sh"
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun helperStartScript(
        classpathScript: String,
        helperLogPath: String,
        callerUid: Int,
    ): String = """
        #!/system/bin/sh
        mkdir -p /sdcard/WJX 2>/dev/null
        printf '%s\n' '[wjx_helper start-script]' >>$helperLogPath
        $classpathScript
        CLASSPATH="${'$'}HELPER_CP" setsid app_process /system/bin \
          --nice-name=$HELPER_PROCESS_NAME com.byd.wjx.helper.HelperDaemon \
          $callerUid </dev/null >>$helperLogPath 2>&1 &
        for i in 1 2 3; do
          service list 2>/dev/null | grep -q $HELPER_PROCESS_NAME && break
          sleep 1
        done
    """.trimIndent()

    private fun helperClasspath(packageCodePath: String): String {
        val packageLoop = HELPER_CLASSPATH_PACKAGES.joinToString(" ", transform = ::shellQuote)
        val fileLoop = HELPER_CLASSPATH_FILES.joinToString(" ", transform = ::shellQuote)
        return "HELPER_CP=${shellQuote(packageCodePath)}; " +
            "for pkg in $packageLoop; do " +
            "path=\$(pm path \"\$pkg\" 2>/dev/null | sed -n 's/^package://p' | head -1); " +
            "[ -n \"\$path\" ] && HELPER_CP=\"\$HELPER_CP:\$path\"; " +
            "done; " +
            "for path in $fileLoop; do " +
            "[ -f \"\$path\" ] && HELPER_CP=\"\$HELPER_CP:\$path\"; " +
            "done"
    }

    private val PROPERTY_NAME = Regex("""^[A-Za-z0-9_.-]{1,96}$""")
    private val SETTING_NAME = Regex("""^[A-Za-z0-9_.-]{1,96}$""")
    private val APP_PACKAGE_NAME = Regex("""^com\.byd\.wjx$""")
    private val HELPER_CLASSPATH_PACKAGES = listOf(
        "com.byd.launcher",
        "com.byd.car.server",
        "com.byd.clusterdebug",
        "com.byd.vehicleconfig",
        "com.xy.instrumentnavigation",
        "com.android.launcher3",
    )
    private val HELPER_CLASSPATH_FILES = listOf(
        "/system/app/BydAutoVoiceTTS/BydAutoVoiceTTS.apk",
    )
}
