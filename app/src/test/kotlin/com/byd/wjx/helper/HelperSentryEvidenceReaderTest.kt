package com.byd.wjx.helper

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HelperSentryEvidenceReaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `warning reader prefers vehicle fid and falls back to global setting`() {
        val vehicleReader = reader(
            readVehicleValue = { _, fid -> if (fid == 1128267829) 2 else null },
            shellOutput = { "1" },
        )
        assertEquals(SentryWarningReading(2, "fid:1049/1128267829"), vehicleReader.readWarning())

        val settingsReader = reader(
            readVehicleValue = { _, _ -> null },
            shellOutput = { command ->
                if (command == "settings get global sentrymode_warning_level") "2\n" else ""
            },
        )
        assertEquals(
            SentryWarningReading(2, "global:sentrymode_warning_level"),
            settingsReader.readWarning(),
        )
    }

    @Test
    fun `guarding accepts enabled setting or enabled system property`() {
        assertTrue(reader(shellOutput = { command -> if (command.contains("sentrymode_enabled")) "1" else "disabled" }).isGuarding())
        assertTrue(reader(shellOutput = { command -> if (command.contains("sentrymode_enabled")) "0" else "ENABLED" }).isGuarding())
        assertFalse(reader(shellOutput = { "0" }).isGuarding())
    }

    @Test
    fun `alarm timestamp cursor triggers once for fresh values and records stale changes`() {
        val now = 1_700_000_000_000L
        val alarmCursor = temporaryFolder.newFile("alarm-cursor.txt")
        val evidence = reader(nowMs = now, alarmCursor = alarmCursor)
        val fresh = (now / 1_000L).toString()

        assertTrue(evidence.consumeAlarmTimestamp(fresh, allowTrigger = true))
        assertFalse(evidence.consumeAlarmTimestamp(fresh, allowTrigger = true))

        val stale = (now / 1_000L - 121L).toString()
        assertFalse(evidence.consumeAlarmTimestamp(stale, allowTrigger = true))
        assertEquals(stale, alarmCursor.readText())
    }

    @Test
    fun `alarm timestamp baseline does not trigger until a new value arrives`() {
        val now = 1_700_000_000_000L
        val evidence = reader(nowMs = now)
        val first = (now / 1_000L - 1L).toString()
        val second = (now / 1_000L).toString()

        assertFalse(evidence.consumeAlarmTimestamp(first, allowTrigger = false))
        assertTrue(evidence.consumeAlarmTimestamp(second, allowTrigger = true))
        assertFalse(evidence.consumeAlarmTimestamp("0", allowTrigger = true))
        assertTrue(isRecentSentryAlarmTimestamp("vendor-format", now))
    }

    @Test
    fun `log marker skips baseline and emits only a new recent high risk line`() {
        val now = 1_700_000_000_000L
        var logOutput = highRiskLine(now)
        val logCursor = temporaryFolder.newFile("log-cursor.txt")
        val evidence = reader(
            nowMs = now,
            logCursor = logCursor,
            shellOutput = { command -> if (command.startsWith("logcat")) logOutput else "" },
        )

        assertEquals("", evidence.readFreshLogMarker(allowTrigger = false))
        assertEquals("", evidence.readFreshLogMarker(allowTrigger = true))

        logOutput = highRiskLine(now + 1_000L)
        assertEquals(logOutput, evidence.readFreshLogMarker(allowTrigger = true))
        assertEquals(logOutput, logCursor.readText())
    }

    @Test
    fun `high risk log parser rejects warning responses and rolls december into previous year`() {
        assertFalse(isHighRiskSentryLogLine("LockVideoHandler responseInform warn: msg.what = 3"))
        assertFalse(isHighRiskSentryLogLine("LockVideoHandler msg.what = 30"))
        assertTrue(isHighRiskSentryLogLine("LockVideoHandler msg.what = 3 alarm"))

        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply { isLenient = false }
        val now = parser.parse("2026-01-01 00:00:05.000")!!.time
        val parsed = parseSentryLogTimestamp("12-31 23:59:59.000 LockVideoHandler msg.what = 3", now)

        assertEquals("2025-12-31 23:59:59.000", parsed?.let { parser.format(Date(it)) })
        assertNull(parseSentryLogTimestamp("not a log line", now))
    }

    private fun reader(
        nowMs: Long = 1_700_000_000_000L,
        readVehicleValue: (Int, Int) -> Int? = { _, _ -> null },
        shellOutput: (String) -> String = { "" },
        logCursor: java.io.File = temporaryFolder.newFile("log-${System.nanoTime()}.txt"),
        alarmCursor: java.io.File = temporaryFolder.newFile("alarm-${System.nanoTime()}.txt"),
    ): WjxSentryEvidenceReader =
        WjxSentryEvidenceReader(
            readVehicleValue = readVehicleValue,
            shellOutput = shellOutput,
            logCursor = logCursor,
            alarmCursor = alarmCursor,
            nowMs = { nowMs },
        )

    private fun highRiskLine(timestampMs: Long): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
        return "$timestamp  100  100 D SENTRYMODE: LockVideoHandler msg.what = 3 alarm"
    }
}
