package com.byd.wjx.helper

import com.byd.wjx.storage.WjxDirectories
import java.io.File
import java.util.concurrent.TimeUnit

internal object WjxLockSoundWatchdog {

    fun enable() {
        runCatching { File(DISABLED_PATH).delete() }
        install()
    }

    fun disable() {
        runCatching { File(DISABLED_PATH).writeText("disabled\n") }
    }

    private fun install() {
        runCatching {
            val logDir = WjxDirectories.baseDir.absolutePath
            val pidFile = File(PID_PATH)
            val versionFile = File(VERSION_PATH)
            val installedVersion = runCatching { versionFile.readText().trim().toInt() }.getOrNull()
            if (installedVersion != VERSION) {
                val oldPid = runCatching { pidFile.readText().trim() }.getOrDefault("")
                if (oldPid.matches(Regex("""\d+"""))) {
                    runCatching {
                        Runtime.getRuntime().exec(arrayOf("kill", oldPid)).waitFor(2, TimeUnit.SECONDS)
                    }
                }
                pidFile.delete()
            }
            val script = File(SCRIPT_PATH)
            script.writeText(
                """
                #!/system/bin/sh
                mkdir -p "$logDir" 2>/dev/null
                LOG="$logDir/wjx.log"
                if [ -f "/data/local/tmp/wjx_lock_watch.pid" ]; then
                  OLD=${'$'}(cat "/data/local/tmp/wjx_lock_watch.pid" 2>/dev/null)
                  if [ -n "${'$'}OLD" ] && kill -0 "${'$'}OLD" 2>/dev/null; then exit 0; fi
                fi
                echo ${'$'}${'$'} > "/data/local/tmp/wjx_lock_watch.pid"
                LAST_PACKAGE_CHECK=0
                while true; do
                  if [ -f "/data/local/tmp/wjx_lock_watch.disabled" ]; then
                    rm -f /data/local/tmp/wjx_lock_watch.pid 2>/dev/null
                    exit 0
                  fi
                  NOW=${'$'}(date +%s)
                  if [ ${'$'}((NOW - LAST_PACKAGE_CHECK)) -ge 300 ]; then
                    APK=${'$'}(pm path com.byd.wjx 2>/dev/null | head -1 | cut -d: -f2)
                    if [ -z "${'$'}APK" ]; then
                      echo "${'$'}(date) I/LockSoundWatch package missing; cleanup wjx helper artifacts" >> "${'$'}LOG"
                      pkill -9 -f wjx_helper 2>/dev/null
                      rm -f /data/local/tmp/wjx_helper.lock /data/local/tmp/wjx_lock_watch.pid /data/local/tmp/wjx_lock_watch.version /data/local/tmp/wjx_lock_sound.json 2>/dev/null
                      exit 0
                    fi
                    LAST_PACKAGE_CHECK=${'$'}NOW
                  fi
                  PIDS=${'$'}(pidof wjx_helper 2>/dev/null)
                  COUNT=${'$'}(echo "${'$'}PIDS" | tr ' ' '\n' | grep -c '^[0-9][0-9]*${'$'}')
                  if [ "${'$'}COUNT" -gt 1 ]; then
                    echo "${'$'}(date) I/LockSoundWatch duplicate helper pids=${'$'}PIDS; restart single owner" >> "${'$'}LOG"
                    pkill -9 -f wjx_helper 2>/dev/null
                    rm -f /data/local/tmp/wjx_helper.lock 2>/dev/null
                    sleep 1
                    COUNT=0
                  fi
                  if [ "${'$'}COUNT" -eq 0 ]; then
                    if [ -f "/data/local/tmp/wjx_helper_last_restart" ]; then
                      PREV=${'$'}(cat "/data/local/tmp/wjx_helper_last_restart" 2>/dev/null)
                      if [ -n "${'$'}PREV" ]; then
                        AGE=${'$'}((NOW - PREV))
                        if [ "${'$'}AGE" -lt 15 ]; then
                          sleep 2
                          continue
                        fi
                      fi
                    fi
                    if [ -f "/data/local/tmp/wjx_helper_start.sh" ]; then
                      sh "/data/local/tmp/wjx_helper_start.sh" >/dev/null 2>&1
                    else
                      am start-foreground-service -n com.byd.wjx/com.byd.wjx.service.KeepAliveService --es reason lock_watch >/dev/null 2>&1 \
                        || am startservice -n com.byd.wjx/com.byd.wjx.service.KeepAliveService --es reason lock_watch >/dev/null 2>&1
                    fi
                    date +%s > "/data/local/tmp/wjx_helper_last_restart"
                    sleep 2
                    HPID2=${'$'}(pidof wjx_helper 2>/dev/null)
                    if [ -n "${'$'}HPID2" ]; then
                      echo "${'$'}(date) I/LockSoundWatch helper restarted pid=${'$'}HPID2" >> "${'$'}LOG"
                    else
                      echo "${'$'}(date) I/LockSoundWatch helper restart requested via service" >> "${'$'}LOG"
                    fi
                  fi
                  sleep 5
                done
                """.trimIndent(),
            )
            versionFile.writeText("$VERSION\n")
            Runtime.getRuntime().exec(arrayOf("chmod", "755", SCRIPT_PATH)).waitFor(2, TimeUnit.SECONDS)
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "setsid sh $SCRIPT_PATH </dev/null >/dev/null 2>&1 &"))
            helperLog(TAG, "watchdog installed path=$SCRIPT_PATH")
        }.onFailure {
            helperLog(TAG, "watchdog install failed: ${throwableSummary(it)}")
        }
    }

    private const val TAG = "LockSound"
    private const val SCRIPT_PATH = "/data/local/tmp/wjx_lock_watch.sh"
    private const val PID_PATH = "/data/local/tmp/wjx_lock_watch.pid"
    private const val VERSION_PATH = "/data/local/tmp/wjx_lock_watch.version"
    private const val DISABLED_PATH = "/data/local/tmp/wjx_lock_watch.disabled"
    private const val VERSION = 3
}
