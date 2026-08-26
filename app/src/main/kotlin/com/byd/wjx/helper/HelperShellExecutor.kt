package com.byd.wjx.helper

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

internal class CmdResult(val code: Int, val stdout: String)

internal data class DevShellResult(val status: Int, val exitCode: Int, val output: String)

internal data class SentryShellCommand(val script: String, val args: List<String>)

internal data class SentryShellPlan(
    val command: SentryShellCommand? = null,
    val rejectionCode: Int? = null,
)

internal fun sentryShellRead(op: String, arg: String): CmdResult {
    val plan = planSentryShellCommand(op, arg)
    val command = plan.command ?: return CmdResult(plan.rejectionCode ?: -3, "")
    return shExec(command.script, *command.args.toTypedArray())
}

internal fun planSentryShellCommand(op: String, arg: String): SentryShellPlan = when (op) {
    "get_global" -> planSentryRead("settings get global \"\$1\"", arg)
    "get_secure" -> planSentryRead("settings get secure \"\$1\"", arg)
    "get_system" -> planSentryRead("settings get system \"\$1\"", arg)
    "get_prop" -> planSentryRead("getprop \"\$1\"", arg)
    "put_global" -> parseSentryAssignment(arg)?.takeIf { (key, value) ->
        key in SENTRY_WRITABLE_GLOBALS && value in SENTRY_SETTING_VALUES
    }?.let { (key, value) ->
        SentryShellPlan(SentryShellCommand("settings put global \"\$1\" \"\$2\"", listOf(key, value)))
    } ?: SentryShellPlan(rejectionCode = -2)
    "set_prop" -> parseSentryAssignment(arg)?.takeIf { (key, value) ->
        key == SENTRY_STATE_PROP && value in SENTRY_PROP_VALUES
    }?.let { (key, value) ->
        SentryShellPlan(SentryShellCommand("setprop \"\$1\" \"\$2\"", listOf(key, value)))
    } ?: SentryShellPlan(rejectionCode = -2)
    else -> SentryShellPlan(rejectionCode = -3)
}

private fun planSentryRead(script: String, arg: String): SentryShellPlan =
    if (isSafeSentryKey(arg)) {
        SentryShellPlan(SentryShellCommand(script, listOf(arg)))
    } else {
        SentryShellPlan(rejectionCode = -2)
    }

private fun isSafeSentryKey(value: String): Boolean =
    value.matches(Regex("""[A-Za-z0-9_.:-]{1,96}"""))

private fun parseSentryAssignment(value: String): Pair<String, String>? {
    val separator = value.indexOf('=')
    if (separator !in 1 until value.lastIndex) return null
    val key = value.substring(0, separator)
    val setting = value.substring(separator + 1)
    if (!isSafeSentryKey(key) || !setting.matches(Regex("""[A-Za-z0-9_.:-]{1,32}"""))) return null
    return key to setting
}

internal fun execShellRead(command: String): DevShellResult {
    val trimmed = command.trim()
    if (trimmed.isBlank()) return DevShellResult(-2, -1, "命令不能为空")
    if (trimmed.length > EXEC_SHELL_MAX_CHARS) {
        return DevShellResult(-2, -1, "命令过长（上限 $EXEC_SHELL_MAX_CHARS 字符）")
    }
    if (isBlockedDevShellCommand(trimmed)) {
        return DevShellResult(-2, -1, "已拦截：疑似破坏性命令")
    }
    helperLog("ExecShell", "run ${trimmed.lineSequence().firstOrNull().orEmpty().take(180)}")
    return runCatching {
        val process = ProcessBuilder("sh", "-c", trimmed).start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutThread = Thread({ process.inputStream.use { it.copyTo(stdout) } }, "WJX-ExecShell-stdout")
        val stderrThread = Thread({ process.errorStream.use { it.copyTo(stderr) } }, "WJX-ExecShell-stderr")
        stdoutThread.isDaemon = true
        stderrThread.isDaemon = true
        stdoutThread.start()
        stderrThread.start()
        val completed = process.waitFor(EXEC_SHELL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            stdoutThread.interrupt()
            stderrThread.interrupt()
            return DevShellResult(
                0,
                SHELL_EXEC_TIMEOUT_CODE,
                "(超时 ${EXEC_SHELL_TIMEOUT_MS / 1000}s，进程已终止)",
            )
        }
        stdoutThread.join(500L)
        stderrThread.join(500L)
        val out = stdout.toString(Charsets.UTF_8.name()).trim()
        val err = stderr.toString(Charsets.UTF_8.name()).trim()
        val combined = buildString {
            if (out.isNotBlank()) append(out)
            if (err.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append("[stderr] ")
                append(err)
            }
            if (isEmpty()) append("(无输出)")
        }.let {
            if (it.length > EXEC_SHELL_OUTPUT_MAX_CHARS) {
                it.take(EXEC_SHELL_OUTPUT_MAX_CHARS) +
                    "\n...(输出已截断至 $EXEC_SHELL_OUTPUT_MAX_CHARS 字符)"
            } else {
                it
            }
        }
        DevShellResult(0, process.exitValue(), combined)
    }.getOrElse {
        DevShellResult(-1, -1, "error=${it.javaClass.simpleName}:${it.message.orEmpty()}")
    }
}

internal fun isBlockedDevShellCommand(command: String): Boolean =
    EXEC_SHELL_BLOCKLIST.any { it.containsMatchIn(command) }

/**
 * Runs [script] under sh with [args] bound to positional params ($1, $2, …) so untrusted values
 * are passed as argv and never re-parsed by the shell. Stderr is discarded to avoid a second
 * pipe that could deadlock or corrupt the stdout protocol.
 */
internal fun shExec(script: String, vararg args: String): CmdResult =
    shExecTimed(SHELL_EXEC_TIMEOUT_MS, script, *args)

private fun shExecTimed(timeoutMs: Long, script: String, vararg args: String): CmdResult {
    val cmd = arrayListOf("sh", "-c", script, "sh")
    cmd.addAll(args)
    val process = ProcessBuilder(cmd)
        .redirectError(ProcessBuilder.Redirect.to(java.io.File("/dev/null")))
        .start()
    val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (!completed) {
        process.destroyForcibly()
        process.waitFor(200L, TimeUnit.MILLISECONDS)
    }
    val out = process.inputStream.bufferedReader().use { it.readText().trim() }
    return CmdResult(if (completed) process.exitValue() else SHELL_EXEC_TIMEOUT_CODE, out)
}

/** Runs a shell command and returns combined stdout/stderr. Mirrors CarControlImpl.exec. */
internal fun execShell(command: String): String {
    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
    val out = process.inputStream.bufferedReader().use { it.readText().trim() }
    val err = process.errorStream.bufferedReader().use { it.readText().trim() }
    process.waitFor()
    return buildString {
        if (out.isNotBlank()) append(out)
        if (err.isNotBlank()) {
            if (isNotEmpty()) append(" | STDERR: ")
            append(err)
        }
    }.ifEmpty { "OK" }
}

/** Enables and force-rebinds the steering-wheel accessibility services under the shell uid. */
internal fun enableAccessibilityService(): Boolean {
    val components = listOf(
        HelperBinderProtocol.STAR_ACCESSIBILITY_SERVICE_COMPONENT,
        HelperBinderProtocol.KEEPALIVE_ACCESSIBILITY_SERVICE_COMPONENT,
    )
    val targets = components.map(::canonicalComponent).toSet()
    val current = readSecure("enabled_accessibility_services") ?: return false
    val others = current.split(':').filter {
        it.isNotEmpty() && canonicalComponent(it) !in targets
    }
    if (shExec(
            "settings put secure enabled_accessibility_services \"\$1\"",
            others.joinToString(":"),
        ).code != 0
    ) {
        return false
    }
    Thread.sleep(200L)
    if (shExec(
            "settings put secure enabled_accessibility_services \"\$1\"",
            (others + components).joinToString(":"),
        ).code != 0
    ) {
        return false
    }
    if (shExec("settings put secure accessibility_enabled 1").code != 0) return false
    val after = (readSecure("enabled_accessibility_services") ?: return false)
        .split(':')
        .filter { it.isNotEmpty() }
    val afterSet = after.map(::canonicalComponent).toSet()
    return targets.all { it in afterSet } && readSecure("accessibility_enabled") == "1"
}

internal fun canonicalComponent(flattened: String): String {
    val sep = flattened.indexOf('/')
    if (sep < 0) return flattened
    val pkg = flattened.substring(0, sep)
    val cls = flattened.substring(sep + 1)
    val fqcn = if (cls.startsWith(".")) pkg + cls else cls
    return "$pkg/$fqcn"
}

private fun readSecure(key: String): String? {
    val result = shExec("settings get secure \"\$1\"", key)
    if (result.code != 0) return null
    return if (result.stdout == "null") "" else result.stdout
}

private val SENTRY_WRITABLE_GLOBALS = setOf(
    "sentrymode_enabled_switch",
    "sentrymode_enabled",
)
private val SENTRY_SETTING_VALUES = setOf("0", "1")
private const val SENTRY_STATE_PROP = "sys.byd.sentrymode_state"
private val SENTRY_PROP_VALUES = setOf("enable", "disable")
private const val SHELL_EXEC_TIMEOUT_MS = 3000L
private const val SHELL_EXEC_TIMEOUT_CODE = -124
private const val EXEC_SHELL_TIMEOUT_MS = 600_000L
private const val EXEC_SHELL_MAX_CHARS = 4096
private const val EXEC_SHELL_OUTPUT_MAX_CHARS = 65_536
private val EXEC_SHELL_BLOCKLIST = listOf(
    Regex("""rm\s+-[^\n]*\s+/\s"""),
    Regex("""rm\s+-[^\n]*\s+/\*"""),
    Regex("""\bdd\b[^\n]*\bof=/dev/block"""),
    Regex("""\bmkfs\b"""),
)
