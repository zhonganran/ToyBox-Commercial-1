package com.byd.wjx.helper

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

internal const val SENTRY_LOG_CURSOR_PATH = "/data/local/tmp/wjx_sentry_msg3_cursor.txt"
internal const val SENTRY_PROP_CURSOR_PATH = "/data/local/tmp/wjx_sentry_alarm_ts_cursor.txt"

internal class WjxSentryEvidenceReader(
    private val readVehicleValue: (dev: Int, fid: Int) -> Int?,
    private val shellOutput: (command: String) -> String = { command -> shExec(command).stdout },
    private val logCursor: File = File(SENTRY_LOG_CURSOR_PATH),
    private val alarmCursor: File = File(SENTRY_PROP_CURSOR_PATH),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun readWarning(): SentryWarningReading {
        val candidates = arrayOf(
            1049 to 1128267797,
            1049 to 1128267829,
            1049 to 861929534,
            1049 to 861929530,
        )
        candidates.forEach { (dev, fid) ->
            readVehicleValue(dev, fid)?.takeIf { it in 0..SENTRY_WARN_ALARM }?.let { value ->
                return SentryWarningReading(value, "fid:$dev/$fid")
            }
        }
        val setting = shellOutput("settings get global sentrymode_warning_level")
            .trim()
            .toIntOrNull()
            ?.takeIf { it in 0..SENTRY_WARN_ALARM }
        return SentryWarningReading(
            level = setting,
            source = if (setting != null) "global:sentrymode_warning_level" else "unavailable",
        )
    }

    fun isGuarding(): Boolean {
        val enabled = shellOutput("settings get global sentrymode_enabled").trim()
        val state = shellOutput("getprop sys.byd.sentrymode_state").trim()
        return enabled == "1" || state.equals("enabled", ignoreCase = true)
    }

    fun consumeAlarmTimestamp(value: String, allowTrigger: Boolean): Boolean {
        if (value.isBlank() || value == "0" || value.equals("null", ignoreCase = true)) return false
        val previous = runCatching {
            alarmCursor.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        }.getOrDefault("")
        if (previous.isBlank()) {
            runCatching { alarmCursor.writeText(value) }
            if (allowTrigger && isRecentSentryAlarmTimestamp(value, nowMs())) {
                helperLog(TAG, "sentry prop first fresh=$value")
                return true
            }
            helperLog(TAG, "sentry prop baseline=$value allowTrigger=$allowTrigger")
            return false
        }
        if (value == previous) return false
        runCatching { alarmCursor.writeText(value) }
        if (!allowTrigger) {
            helperLog(TAG, "sentry prop baseline update=$value")
            return false
        }
        if (!isRecentSentryAlarmTimestamp(value, nowMs())) {
            helperLog(TAG, "sentry prop changed but stale previous=$previous current=$value")
            return false
        }
        helperLog(TAG, "sentry prop changed previous=$previous current=$value")
        return true
    }

    fun readFreshLogMarker(allowTrigger: Boolean): String {
        val out = shellOutput("logcat -d -v threadtime -t 300 -s SENTRYMODE:D")
        val marker = out.lineSequence()
            .lastOrNull(::isHighRiskSentryLogLine)
            .orEmpty()
        if (marker.isBlank()) return ""
        val currentTime = nowMs()
        val recent = isRecentSentryLogLine(marker, currentTime)
        val previous = runCatching {
            logCursor.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        }.getOrDefault("")
        if (previous.isBlank()) {
            runCatching { logCursor.writeText(marker) }
            if (allowTrigger && recent) {
                helperLog(TAG, "sentry logcat first fresh=${marker.take(120)}")
                return marker
            }
            helperLog(TAG, "sentry logcat skip backlog recent=$recent cursor=${marker.take(80)}")
            return ""
        }
        if (marker == previous) return ""
        runCatching { logCursor.writeText(marker) }
        if (!allowTrigger) {
            helperLog(TAG, "sentry logcat baseline update=${marker.take(80)}")
            return ""
        }
        if (!recent) {
            helperLog(TAG, "sentry logcat skip stale marker=${marker.take(100)}")
            return ""
        }
        helperLog(TAG, "sentry logcat high marker=${marker.take(120)}")
        return marker
    }

    fun readAlarmTimestamp(): String =
        shellOutput("getprop sys.byd.sentry_alarm_timestamp").trim()

    private companion object {
        private const val TAG = "LockSound"
    }
}

internal fun isRecentSentryAlarmTimestamp(value: String, nowMs: Long): Boolean {
    val raw = value.toLongOrNull() ?: return true
    val seconds = if (raw > 10_000_000_000L) raw / 1_000L else raw
    val nowSeconds = nowMs / 1_000L
    return abs(nowSeconds - seconds) <= SENTRY_PROP_FIRST_FRESH_SECONDS
}

internal fun isHighRiskSentryLogLine(line: String): Boolean {
    val lower = line.lowercase(Locale.US)
    return lower.contains("lockvideohandler") &&
        SENTRY_MSG3_REGEX.containsMatchIn(lower) &&
        !lower.contains("responseinform warn:")
}

internal fun isRecentSentryLogLine(line: String, nowMs: Long): Boolean {
    val timestamp = parseSentryLogTimestamp(line, nowMs) ?: return false
    return timestamp >= nowMs - SENTRY_LOG_FRESH_MS && timestamp <= nowMs + SENTRY_LOG_FUTURE_SKEW_MS
}

internal fun parseSentryLogTimestamp(line: String, nowMs: Long): Long? {
    val trimmed = line.trimStart()
    FULL_LOG_TIME_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { raw ->
        return parseLogTime("yyyy-MM-dd HH:mm:ss.SSS", raw)
    }
    val short = SHORT_LOG_TIME_REGEX.find(trimmed)?.groupValues ?: return null
    val monthDay = short.getOrNull(1) ?: return null
    val time = short.getOrNull(2) ?: return null
    val currentYear = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.YEAR)
    var parsed = parseLogTime("yyyy-MM-dd HH:mm:ss.SSS", "$currentYear-$monthDay $time") ?: return null
    if (parsed > nowMs + ONE_DAY_MS) {
        parsed = parseLogTime("yyyy-MM-dd HH:mm:ss.SSS", "${currentYear - 1}-$monthDay $time") ?: parsed
    }
    return parsed
}

private fun parseLogTime(pattern: String, raw: String): Long? =
    runCatching {
        SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(raw)?.time
    }.getOrNull()

private const val SENTRY_PROP_FIRST_FRESH_SECONDS = 120L
private const val SENTRY_LOG_FRESH_MS = 120_000L
private const val SENTRY_LOG_FUTURE_SKEW_MS = 10_000L
private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
private val SENTRY_MSG3_REGEX = Regex("msg\\.what\\s*=\\s*3(?:\\D|$)")
private val FULL_LOG_TIME_REGEX = Regex("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})")
private val SHORT_LOG_TIME_REGEX = Regex("^(\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2}\\.\\d{3})")
