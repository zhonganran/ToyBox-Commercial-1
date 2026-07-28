package com.byd.wjx.helper

import com.byd.wjx.storage.WjxDirectories
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shell-uid watchdog that survives the app process and the helper daemon.
 *
 * The helper installs this after registering its Binder service. From that point the
 * watchdog independently restores both sides of the runtime: it launches the detached
 * helper start script when `wjx_helper` disappears and explicitly wakes the app through
 * [com.byd.wjx.service.ShellKeepAliveReceiver] when the app or its foreground keepalive
 * service is missing.
 */
internal object WjxRuntimeWatchdog {

    fun enable() {
        runCatching { install() }
            .onFailure { helperLog(TAG, "watchdog install failed: ${throwableSummary(it)}") }
    }

    private fun install() {
        cleanupLegacyLockSoundWatchdog()

        val pidFile = File(PID_PATH)
        val versionFile = File(VERSION_PATH)
        val installedVersion = runCatching { versionFile.readText().trim().toInt() }.getOrNull()
        if (installedVersion != VERSION) {
            stopPidFrom(pidFile)
            pidFile.delete()
        } else if (isPidAlive(pidFile)) {
            helperLog(TAG, "watchdog already running pid=${pidFile.readText().trim()}")
            return
        }

        val script = File(SCRIPT_PATH)
        script.writeText(buildScript(WjxDirectories.baseDir.absolutePath))
        versionFile.writeText("$VERSION\n")
        Runtime.getRuntime().exec(arrayOf("chmod", "755", SCRIPT_PATH))
            .waitFor(2, TimeUnit.SECONDS)
        Runtime.getRuntime().exec(
            arrayOf("sh", "-c", "setsid sh $SCRIPT_PATH </dev/null >/dev/null 2>&1 &"),
        )
        helperLog(TAG, "watchdog installed path=$SCRIPT_PATH version=$VERSION")
    }

    private fun cleanupLegacyLockSoundWatchdog() {
        val legacyPid = File(LEGACY_PID_PATH)
        stopPidFrom(legacyPid)
        listOf(
            LEGACY_SCRIPT_PATH,
            LEGACY_PID_PATH,
            LEGACY_VERSION_PATH,
            LEGACY_DISABLED_PATH,
        ).forEach { runCatching { File(it).delete() } }
    }

    private fun stopPidFrom(pidFile: File) {
        val pid = runCatching { pidFile.readText().trim() }.getOrDefault("")
        if (!pid.matches(PID_REGEX)) return
        runCatching {
            Runtime.getRuntime().exec(arrayOf("kill", pid)).waitFor(2, TimeUnit.SECONDS)
        }
    }

    private fun isPidAlive(pidFile: File): Boolean {
        val pid = runCatching { pidFile.readText().trim() }.getOrDefault("")
        if (!pid.matches(PID_REGEX)) return false
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("kill", "-0", pid))
            process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)
    }

    internal fun buildScript(logDir: String): String =
        """
        #!/system/bin/sh
        PACKAGE="com.byd.wjx"
        HELPER_NAME="wjx_helper"
        HELPER_SCRIPT="/data/local/tmp/wjx_helper_start.sh"
        PID_FILE="$PID_PATH"
        LOG="$logDir/wjx.log"
        KICK_COMPONENT="com.byd.wjx/com.byd.wjx.service.ShellKeepAliveReceiver"
        KICK_ACTION="com.byd.wjx.action.SHELL_KEEPALIVE"

        mkdir -p "$logDir" 2>/dev/null
        if [ -f "${'$'}PID_FILE" ]; then
          OLD=${'$'}(cat "${'$'}PID_FILE" 2>/dev/null)
          if [ -n "${'$'}OLD" ] && kill -0 "${'$'}OLD" 2>/dev/null; then exit 0; fi
        fi
        echo ${'$'}${'$'} > "${'$'}PID_FILE"
        trap 'rm -f "${'$'}PID_FILE" 2>/dev/null; exit 0' TERM INT EXIT

        append_csv_setting() {
          NS="${'$'}1"
          KEY="${'$'}2"
          VALUE="${'$'}3"
          CUR=${'$'}(settings get "${'$'}NS" "${'$'}KEY" 2>/dev/null)
          [ "${'$'}CUR" = "null" ] && CUR=""
          case ",${'$'}CUR," in
            *,"${'$'}VALUE",*) ;;
            *) settings put "${'$'}NS" "${'$'}KEY" "${'$'}{CUR:+${'$'}CUR,}${'$'}VALUE" >/dev/null 2>&1 ;;
          esac
        }

        apply_keepalive_policy() {
          appops set "${'$'}PACKAGE" RUN_IN_BACKGROUND allow >/dev/null 2>&1
          appops set "${'$'}PACKAGE" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
          appops set "${'$'}PACKAGE" WAKE_LOCK allow >/dev/null 2>&1
          cmd appops set "${'$'}PACKAGE" BOOT_COMPLETED allow >/dev/null 2>&1
          dumpsys deviceidle whitelist +"${'$'}PACKAGE" >/dev/null 2>&1
          cmd deviceidle whitelist +"${'$'}PACKAGE" >/dev/null 2>&1
          append_csv_setting global ssc_whitelist "${'$'}PACKAGE"
          append_csv_setting secure ssc_whitelist "${'$'}PACKAGE"
          content call --uri content://com.byd.appstartup/whitelist --method add \
            --arg "${'$'}PACKAGE" >/dev/null 2>&1
        }

        kick_app() {
          REASON="${'$'}1"
          echo "${'$'}(date) W/RuntimeWatchdog app kick reason=${'$'}REASON" >> "${'$'}LOG"
          am broadcast -f 0x20 -n "${'$'}KICK_COMPONENT" -a "${'$'}KICK_ACTION" \
            --es reason "${'$'}REASON" >/dev/null 2>&1
        }

        start_helper() {
          if [ -f "${'$'}HELPER_SCRIPT" ]; then
            sh "${'$'}HELPER_SCRIPT" >/dev/null 2>&1
          else
            kick_app "helper_script_missing"
          fi
        }

        apply_keepalive_policy
        LAST_PACKAGE_CHECK=0
        LAST_APP_CHECK=0
        LAST_POLICY_CHECK=${'$'}(date +%s)

        while true; do
          NOW=${'$'}(date +%s)

          if [ ${'$'}((NOW - LAST_PACKAGE_CHECK)) -ge 300 ]; then
            APK=${'$'}(pm path "${'$'}PACKAGE" 2>/dev/null | head -1 | cut -d: -f2)
            if [ -z "${'$'}APK" ]; then
              echo "${'$'}(date) I/RuntimeWatchdog package missing; exiting" >> "${'$'}LOG"
              for PID in ${'$'}(pidof "${'$'}HELPER_NAME" 2>/dev/null); do
                kill -9 "${'$'}PID" 2>/dev/null
              done
              rm -f "${'$'}PID_FILE" "$SCRIPT_PATH" "$VERSION_PATH" \
                /data/local/tmp/wjx_helper.lock /data/local/tmp/wjx_helper_start.sh \
                /data/local/tmp/wjx_remote_standby.enabled 2>/dev/null
              exit 0
            fi
            LAST_PACKAGE_CHECK=${'$'}NOW
          fi

          PIDS=${'$'}(pidof "${'$'}HELPER_NAME" 2>/dev/null)
          COUNT=${'$'}(echo "${'$'}PIDS" | tr ' ' '\n' | grep -c '^[0-9][0-9]*${'$'}')
          if [ "${'$'}COUNT" -gt 1 ]; then
            echo "${'$'}(date) W/RuntimeWatchdog duplicate helper pids=${'$'}PIDS" >> "${'$'}LOG"
            for PID in ${'$'}PIDS; do kill -9 "${'$'}PID" 2>/dev/null; done
            rm -f /data/local/tmp/wjx_helper.lock 2>/dev/null
            sleep 1
            COUNT=0
          fi
          if [ "${'$'}COUNT" -eq 0 ]; then
            echo "${'$'}(date) W/RuntimeWatchdog helper missing; restarting" >> "${'$'}LOG"
            start_helper
            sleep 2
          fi

          if [ ${'$'}((NOW - LAST_APP_CHECK)) -ge 15 ]; then
            APP_PID=${'$'}(pidof "${'$'}PACKAGE" 2>/dev/null)
            SERVICE_ALIVE=0
            dumpsys activity services "${'$'}PACKAGE" 2>/dev/null \
              | grep -Eq 'com[.]byd[.]wjx/[.]service[.]KeepAliveService|com[.]byd[.]wjx[.]service[.]KeepAliveService' \
              && SERVICE_ALIVE=1
            if [ -z "${'$'}APP_PID" ]; then
              kick_app "app_process_missing"
            elif [ "${'$'}SERVICE_ALIVE" -ne 1 ]; then
              kick_app "keepalive_service_missing"
            fi
            LAST_APP_CHECK=${'$'}NOW
          fi

          if [ ${'$'}((NOW - LAST_POLICY_CHECK)) -ge 1800 ]; then
            apply_keepalive_policy
            LAST_POLICY_CHECK=${'$'}NOW
          fi

          sleep 5
        done
        """.trimIndent()

    private const val TAG = "RuntimeWatchdog"
    private const val SCRIPT_PATH = "/data/local/tmp/wjx_runtime_watchdog.sh"
    private const val PID_PATH = "/data/local/tmp/wjx_runtime_watchdog.pid"
    private const val VERSION_PATH = "/data/local/tmp/wjx_runtime_watchdog.version"
    private const val LEGACY_SCRIPT_PATH = "/data/local/tmp/wjx_lock_watch.sh"
    private const val LEGACY_PID_PATH = "/data/local/tmp/wjx_lock_watch.pid"
    private const val LEGACY_VERSION_PATH = "/data/local/tmp/wjx_lock_watch.version"
    private const val LEGACY_DISABLED_PATH = "/data/local/tmp/wjx_lock_watch.disabled"
    private const val VERSION = 2
    private val PID_REGEX = Regex("""\d+""")
}
