package com.byd.wjx.helper

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.IBinder
import android.os.Parcel
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal fun sentryShellRead(op: String, arg: String): CmdResult {
    return when (op) {
        "get_global" -> arg.takeIf(::isSafeSentryKey)
            ?.let { shExec("settings get global \"\$1\"", it) }
            ?: CmdResult(-2, "")
        "get_secure" -> arg.takeIf(::isSafeSentryKey)
            ?.let { shExec("settings get secure \"\$1\"", it) }
            ?: CmdResult(-2, "")
        "get_system" -> arg.takeIf(::isSafeSentryKey)
            ?.let { shExec("settings get system \"\$1\"", it) }
            ?: CmdResult(-2, "")
        "get_prop" -> arg.takeIf(::isSafeSentryKey)
            ?.let { shExec("getprop \"\$1\"", it) }
            ?: CmdResult(-2, "")
        "put_global" -> parseSentryAssignment(arg)?.takeIf { (key, value) ->
            key in SENTRY_WRITABLE_GLOBALS && value in SENTRY_SETTING_VALUES
        }?.let { (key, value) ->
            shExec("settings put global \"\$1\" \"\$2\"", key, value)
        } ?: CmdResult(-2, "")
        "set_prop" -> parseSentryAssignment(arg)?.takeIf { (key, value) ->
            key == SENTRY_STATE_PROP && value in SENTRY_PROP_VALUES
        }?.let { (key, value) ->
            shExec("setprop \"\$1\" \"\$2\"", key, value)
        } ?: CmdResult(-2, "")
        else -> CmdResult(-3, "")
    }
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

private val SENTRY_WRITABLE_GLOBALS = setOf(
    "sentrymode_enabled_switch",
    "sentrymode_enabled",
)
private val SENTRY_SETTING_VALUES = setOf("0", "1")
private const val SENTRY_STATE_PROP = "sys.byd.sentrymode_state"
private val SENTRY_PROP_VALUES = setOf("enable", "disable")

internal fun execShellRead(command: String): DevShellResult {
    val trimmed = command.trim()
    if (trimmed.isBlank()) return DevShellResult(-2, -1, "命令不能为空")
    if (trimmed.length > EXEC_SHELL_MAX_CHARS) {
        return DevShellResult(-2, -1, "命令过长（上限 $EXEC_SHELL_MAX_CHARS 字符）")
    }
    if (EXEC_SHELL_BLOCKLIST.any { it.containsMatchIn(trimmed) }) {
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
            return DevShellResult(0, SHELL_EXEC_TIMEOUT_CODE, "(超时 ${EXEC_SHELL_TIMEOUT_MS / 1000}s，进程已终止)")
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
                it.take(EXEC_SHELL_OUTPUT_MAX_CHARS) + "\n...(输出已截断至 $EXEC_SHELL_OUTPUT_MAX_CHARS 字符)"
            } else {
                it
            }
        }
        DevShellResult(0, process.exitValue(), combined)
    }.getOrElse {
        DevShellResult(-1, -1, "error=${it.javaClass.simpleName}:${it.message.orEmpty()}")
    }
}

internal fun helperLog(tag: String, message: String) {
    val ts = synchronized(HELPER_LOG_DATE) { HELPER_LOG_DATE.format(Date()) }
    val line = "$ts I/$tag $message\n"
    runCatching {
        val dir = File("/sdcard/WJX")
        dir.mkdirs()
        val log = File(dir, "wjx.log")
        trimLogIfNeeded(log)
        log.appendText(line)
    }
}

private fun trimLogIfNeeded(file: File) {
    if (file.exists() && file.length() > HELPER_LOG_MAX_BYTES) {
        val tail = file.readLines().takeLast(HELPER_LOG_KEEP_LINES).joinToString("\n", postfix = "\n")
        file.writeText(tail)
    }
}

/**
 * Performs a single autoservice Binder transact and returns (status, retInt).
 *
 * [tx] is the transact code (5=getInt, 6=setInt, 7=getDouble raw int, 9=getBuffer).
 * [writeValue] controls whether [value] is written into the parcel (true for setInt/tx=6 only).
 *
 * Semantics match the proven logic from the TCP daemon:
 *   avail >= 4 → status = reply.readInt() else -999
 *   avail >= 8 → retInt = reply.readInt() else 0
 */
internal fun autoserviceTransact(
    svc: IBinder,
    autoIface: String,
    tx: Int,
    dev: Int,
    fid: Int,
    value: Int,
    writeValue: Boolean
): Pair<Int, Int> {
    val data2 = Parcel.obtain()
    val reply2 = Parcel.obtain()
    return try {
        data2.writeInterfaceToken(autoIface)
        data2.writeInt(dev)
        data2.writeInt(fid)
        if (writeValue) data2.writeInt(value)
        svc.transact(tx, data2, reply2, 0)
        val avail = reply2.dataAvail()
        val status = if (avail >= 4) reply2.readInt() else -999
        val retInt = if (avail >= 8) reply2.readInt() else 0
        status to retInt
    } finally {
        data2.recycle()
        reply2.recycle()
    }
}

/** Resolves IActivityTaskManager via ActivityTaskManager.getService() (hidden API, ok under app_process). */
private fun activityTaskManager(): Any =
    Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        ?: throw IllegalStateException("ActivityTaskManager.getService() returned null")

/** Walks the superclass chain for a declared field [name] (mirrors CarControlImpl.findField). */
private fun fieldByName(target: Any, name: String): java.lang.reflect.Field? {
    var cls: Class<*>? = target.javaClass
    while (cls != null) {
        try { return cls.getDeclaredField(name) } catch (e: NoSuchFieldException) { cls = cls.superclass }
    }
    return null
}

/**
 * Finds the running task id of [packageName]. Reconstructed from CarControlImpl.getTopActivityPackage:
 * iAtm.getTasks(maxNum, false, false) -> List<RunningTaskInfo>; match topActivity/baseActivity package;
 * read int field "taskId" (fallback "id"). Returns -1 when not found.
 * NOTE: field names validated on-car in Phase 2.
 */
internal fun findTaskId(packageName: String): Int {
    val iAtm = activityTaskManager()
    val getTasks = iAtm.javaClass.getMethod(
        "getTasks", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
    )
    val tasks = getTasks.invoke(iAtm, 100, false, false) as? List<*> ?: return -1
    for (task in tasks) {
        if (task == null) continue
        val pkg = listOf("topActivity", "baseActivity").firstNotNullOfOrNull { fieldName ->
            fieldByName(task, fieldName)?.let { f ->
                f.isAccessible = true
                (f.get(task) as? android.content.ComponentName)?.packageName
            }
        }
        if (pkg == packageName) {
            val idField = fieldByName(task, "taskId") ?: fieldByName(task, "id") ?: continue
            idField.isAccessible = true
            return idField.getInt(task)
        }
    }
    return -1
}

/** moveRootTaskToDisplay(int,int) preferred, fallback moveTaskToDisplay(int,int). */
internal fun moveTaskToDisplayReflect(taskId: Int, displayId: Int) {
    val iAtm = activityTaskManager()
    val m = iAtm.javaClass.methods.firstOrNull { it.name == "moveRootTaskToDisplay" && it.parameterTypes.size == 2 }
        ?: iAtm.javaClass.methods.firstOrNull { it.name == "moveTaskToDisplay" && it.parameterTypes.size == 2 }
        ?: throw NoSuchMethodException("moveTaskToDisplay")
    m.invoke(iAtm, taskId, displayId)
}

internal fun setTaskWindowingModeReflect(taskId: Int, windowingMode: Int) {
    val iAtm = activityTaskManager()
    iAtm.javaClass.getMethod("setTaskWindowingMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        .invoke(iAtm, taskId, windowingMode, true)
}

internal fun setTaskBoundsReflect(taskId: Int, left: Int, top: Int, right: Int, bottom: Int) {
    val iAtm = activityTaskManager()
    val resizeTask = iAtm.javaClass.getMethod("resizeTask", Int::class.javaPrimitiveType, Rect::class.java, Int::class.javaPrimitiveType)
    val rect = if (left == 0 && top == 0 && right == 0 && bottom == 0) null else Rect(left, top, right, bottom)
    resizeTask.invoke(iAtm, taskId, rect, 1)
}

internal fun setFocusedTaskReflect(taskId: Int) {
    val iAtm = activityTaskManager()
    iAtm.javaClass.getMethod("setFocusedRootTask", Int::class.javaPrimitiveType).invoke(iAtm, taskId)
}

/**
 * Acquires a system Context via ActivityThread, exactly as OpenBYD's EntryPoint does.
 * Returns null on failure — read paths that need it degrade to an error status rather
 * than crashing the daemon (autoservice read/write do NOT need a Context and keep working).
 *
 * Reflection (no HiddenApiBypass): the daemon runs under app_process, where hidden-API
 * enforcement is inactive.
 */
internal fun acquireSystemContext(): Context? = try {
    val atCls = Class.forName("android.app.ActivityThread")
    val activityThread = atCls.getMethod("systemMain").invoke(null)
    atCls.getMethod("getSystemContext").invoke(activityThread) as? Context
} catch (e: Throwable) {
    System.err.println("WARN: systemContext unavailable: ${e.message}")
    null
}

/**
 * Shell-side VIN read. This mirrors Modi's key detail: use the system/shell process Context to
 * create BYDAutoBodyworkDevice, then read both getRealAutoVIN and getAutoVIN.
 */
internal fun readVehicleVinFromBodywork(systemContext: Context?): String {
    val out = StringBuilder()
    out.append(HELPER_VIN_READER_MARKER).append('\n')
    out.append("systemContext=").append(systemContext?.javaClass?.name ?: "null").append('\n')
    val ctx = systemContext ?: return out.append("bodywork=skip:no_context").toString()
    val clazz = try {
        Class.forName(BYD_BODYWORK_DEVICE_CLASS)
    } catch (e: Throwable) {
        return out.append("bodyworkClass=missing:").append(throwableSummary(e)).toString()
    }
    out.append("bodyworkClass=").append(clazz.name).append('\n')
    val contexts = buildBodyworkContextCandidates(ctx)

    contexts.forEachIndexed { index, candidate ->
        out.append("context[").append(index).append("]=")
            .append(candidate.packageName).append('/').append(candidate.javaClass.name).append('\n')
        val instance = createBydBodyworkInstance(clazz, candidate, out)
        if (instance == null) {
            out.append("instance[").append(index).append("]=null").append('\n')
            return@forEachIndexed
        }
        out.append("instance[").append(index).append("]=").append(instance.javaClass.name).append('\n')
        BYD_VIN_METHODS.forEach { methodName ->
            val value = invokeBodyworkNoArg(instance, methodName)
            out.append(methodName).append('=').append(value.orEmpty()).append('\n')
        }
    }
    return out.toString().take(4000)
}

private fun buildBodyworkContextCandidates(systemContext: Context): List<Context> {
    val baseContexts = mutableListOf<Context>()
    baseContexts += systemContext
    BYD_CONTEXT_PACKAGES.forEach { pkg ->
        runCatching {
            systemContext.createPackageContext(
                pkg,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
        }.getOrNull()?.let { baseContexts += it }
    }
    runCatching {
        systemContext.createPackageContext("android", Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull()?.let { baseContexts += it }
    runCatching {
        systemContext.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull()?.let { baseContexts += it }

    return baseContexts
        .distinctBy { "${it.javaClass.name}:${it.packageName}" }
        .flatMap { listOf(it, PermissionBypassContext(it)) }
        .distinctBy { "${it.javaClass.name}:${it.packageName}:${it.baseContextKey()}" }
}

private fun Context.baseContextKey(): String =
    (this as? ContextWrapper)?.baseContext?.let { "${it.javaClass.name}:${it.packageName}" }.orEmpty()

private class PermissionBypassContext(base: Context) : ContextWrapper(base) {
    override fun checkCallingOrSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED

    override fun checkCallingPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED

    override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_GRANTED

    override fun enforceCallingOrSelfPermission(permission: String, message: String?) = Unit

    override fun enforceCallingPermission(permission: String, message: String?) = Unit

    override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) = Unit
}

internal fun createBydBodyworkInstance(clazz: Class<*>, context: Context, out: StringBuilder): Any? {
    val contextMethods = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
        .filter {
            it.name == "getInstance" &&
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].isAssignableFrom(context.javaClass)
        }
        .distinctBy { "${it.declaringClass.name}:${it.name}:${it.parameterTypes.joinToString(",") { type -> type.name }}" }
        .sortedBy { inheritanceDistance(context.javaClass, it.parameterTypes[0]) }
        .toList()
    contextMethods.forEach { contextMethod ->
        try {
            contextMethod.isAccessible = true
            val instance = contextMethod.invoke(null, context)
            if (instance != null) return instance
        } catch (e: Throwable) {
            out.append("getInstance(").append(context.javaClass.simpleName).append(")=error:")
                .append(throwableSummary(e)).append('\n')
        }
    }
    val noArgMethod = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence()).firstOrNull {
        it.name == "getInstance" &&
            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
            it.parameterTypes.isEmpty()
    } ?: return null
    return try {
        noArgMethod.isAccessible = true
        noArgMethod.invoke(null)
    } catch (e: Throwable) {
        out.append("getInstance()=error:").append(throwableSummary(e)).append('\n')
        null
    }
}

private fun inheritanceDistance(child: Class<*>, parent: Class<*>): Int {
    var distance = 0
    var current: Class<*>? = child
    while (current != null) {
        if (current == parent) return distance
        distance++
        current = current.superclass
    }
    return Int.MAX_VALUE
}

private fun invokeBodyworkNoArg(instance: Any, methodName: String): String? {
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        val method = cls.declaredMethods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
        if (method != null) {
            return try {
                method.isAccessible = true
                method.invoke(instance)?.toString()
            } catch (e: Throwable) {
                "ERR:${throwableSummary(e)}"
            }
        }
        cls = cls.superclass
    }
    return try {
        instance.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
            ?.invoke(instance)
            ?.toString()
    } catch (e: Throwable) {
        "ERR:${throwableSummary(e)}"
    }
}

internal fun rawHasVin(raw: String): Boolean =
    VIN_TOKEN.findAll(raw).any { it.value.startsWith("L", ignoreCase = true) }

internal fun throwableSummary(error: Throwable): String {
    val unwrapped = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
    return "${unwrapped.javaClass.simpleName}:${unwrapped.message.orEmpty()}".take(220)
}

/**
 * Creates a VirtualDisplay backed by [surface]. Uses a com.android.shell package Context so the
 * shell uid's privilege applies to the requested [flags] (incl. TRUSTED / SYSTEM_DECORATIONS).
 * Returns the new displayId (>0) on success, or -1 (releasing any invalid display). Mirrors
 * CarControlImpl.createVirtualDisplay.
 */
internal fun createVirtualDisplay(
    ctx: Context,
    store: ConcurrentHashMap<Int, VirtualDisplay>,
    name: String, width: Int, height: Int, density: Int, surface: Surface, flags: Int,
): Int {
    val shellCtx = ctx.createPackageContext("com.android.shell", 0)
    val dm = shellCtx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val vd = dm.createVirtualDisplay(name, width, height, density, surface, flags) ?: return -1
    val id = vd.display?.displayId ?: -1
    if (id <= 0) { vd.release(); return -1 }
    store[id] = vd
    return id
}

/** Runs a shell command (shell uid) and returns combined stdout/stderr. Mirrors CarControlImpl.exec. */
private fun execShell(command: String): String {
    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
    val out = process.inputStream.bufferedReader().use { it.readText().trim() }
    val err = process.errorStream.bufferedReader().use { it.readText().trim() }
    process.waitFor()
    return buildString {
        if (out.isNotBlank()) append(out)
        if (err.isNotBlank()) { if (isNotEmpty()) append(" | STDERR: "); append(err) }
    }.ifEmpty { "OK" }
}

internal class CmdResult(val code: Int, val stdout: String)
internal data class DevShellResult(val status: Int, val exitCode: Int, val output: String)

/**
 * Runs [script] under sh with [args] bound to positional params ($1, $2, …) so untrusted values
 * (e.g. an existing accessibility list read off the device) are passed as argv and NEVER re-parsed
 * by the shell — no injection, no quote-breakage. Returns ONLY stdout + exit code (stderr is sent
 * to /dev/null, so there is no second pipe to deadlock on and stdout can never be corrupted by an
 * stderr line — unlike [execShell], which merges them). Use this whenever success/output matters.
 */
internal fun shExec(script: String, vararg args: String): CmdResult {
    return shExecTimed(SHELL_EXEC_TIMEOUT_MS, script, *args)
}

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

/**
 * Enables our steering-wheel accessibility service so it starts filtering steering-wheel keys.
 * DiLink has no a11y settings UI, so the user cannot toggle it; we do it under shell uid via the
 * `settings` binary (the in-process Settings.Secure ContentResolver does not stick for an
 * app_process-spawned daemon).
 *
 * Force re-bind, mirroring OpenBYD AccessibilitySetupHelper.buildEnableCommands: write the list
 * WITHOUT our component, pause, then write it back WITH our component, then accessibility_enabled=1.
 * A plain append does NOT make the framework bind a service that is already listed but not running
 * (the common case after an APK reinstall/upgrade) — the remove+re-add is what triggers the bind.
 * Read-modify-write preserves other apps' services (our component is the only one we ever touch).
 * App-scoped, reversible Secure settings only; touches nothing on the vehicle (no autoservice/CAN).
 */
internal fun enableAccessibilityService(): Boolean {
    val components = listOf(
        HelperBinderProtocol.STAR_ACCESSIBILITY_SERVICE_COMPONENT,
        HelperBinderProtocol.KEEPALIVE_ACCESSIBILITY_SERVICE_COMPONENT,
    )
    val targets = components.map { canonicalComponent(it) }.toSet()
    // Abort on a failed READ — never write a guessed/garbled list back, that would clobber others.
    val current = readSecure("enabled_accessibility_services") ?: return false
    // Strip EVERY spelling of our component. The short ("pkg/.Cls") and full ("pkg/pkg.Cls") forms
    // both canonicalise to the same ComponentName, so a literal-string filter would leave the other
    // form behind. The framework would then see no change to the enabled SET and never re-bind a
    // crashed service — the exact failure that kept star control dead after a reboot.
    val others = current.split(':').filter { it.isNotEmpty() && canonicalComponent(it) !in targets }
    // $1 = the list, passed as argv (not interpolated) — safe even if an existing entry is odd.
    if (shExec("settings put secure enabled_accessibility_services \"\$1\"", others.joinToString(":")).code != 0) return false
    Thread.sleep(200L)  // let the framework observe the removal before we re-add (OpenBYD uses 0.2s)
    if (shExec("settings put secure enabled_accessibility_services \"\$1\"", (others + components).joinToString(":")).code != 0) return false
    if (shExec("settings put secure accessibility_enabled 1").code != 0) return false
    // Verify read-back: our component is now listed AND accessibility is enabled.
    val after = (readSecure("enabled_accessibility_services") ?: return false).split(':').filter { it.isNotEmpty() }
    val afterSet = after.map { canonicalComponent(it) }.toSet()
    return targets.all { it in afterSet } && readSecure("accessibility_enabled") == "1"
}

/**
 * Expands a flattened component string to a canonical `pkg/fully.qualified.Class`, mirroring
 * ComponentName.unflattenFromString's leading-dot rule (`pkg/.A.B` -> `pkg/pkg.A.B`). Lets the short
 * and full spellings of one service compare equal. Returns the input unchanged when it has no '/'.
 */
internal fun canonicalComponent(flattened: String): String {
    val sep = flattened.indexOf('/')
    if (sep < 0) return flattened
    val pkg = flattened.substring(0, sep)
    val cls = flattened.substring(sep + 1)
    val fqcn = if (cls.startsWith(".")) pkg + cls else cls
    return "$pkg/$fqcn"
}

/**
 * Reads a secure setting via the `settings` binary: "" when unset (`settings get` prints "null"),
 * or null on a non-zero exit so callers can abort instead of acting on a bad read.
 */
private fun readSecure(key: String): String? {
    val r = shExec("settings get secure \"\$1\"", key)
    if (r.code != 0) return null
    return if (r.stdout == "null") "" else r.stdout
}

/**
 * Launches [packageName] via am/monkey strategies. Returns true if a launch command ran without an
 * obvious "Error". Mirrors CarControlImpl.launchApp (simplified to a boolean).
 */
internal fun launchApp(packageName: String): Boolean {
    val resolve = execShell("cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $packageName")
    val component = resolve.lineSequence().firstOrNull { it.contains("/") && !it.startsWith("No ") }?.trim()
    if (component != null) {
        val r = execShell("am start -n $component")
        if (!r.contains("Error")) return true
    }
    val r2 = execShell("am start -a android.intent.action.MAIN $packageName")
    if (!r2.contains("Error")) return true
    val r3 = execShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    return !r3.contains("Error") && !r3.contains("error")
}

/**
 * Launches [packageName] on [displayId] and pins it there with a short persistence loop
 * (move -> bounds -> focus, x2). Returns true once redirection ran. Mirrors CarControlImpl.launchAndForce.
 * Blocking (Thread.sleep) — runs on a binder threadpool thread; the app side uses a 15s timeout.
 */
internal fun launchAndForce(packageName: String, displayId: Int, width: Int, height: Int): Boolean {
    var taskId = findTaskId(packageName)
    if (taskId <= 0) {
        launchApp(packageName)
        var attempt = 1
        while (attempt < 16) {
            taskId = findTaskId(packageName)
            if (taskId > 0) break
            Thread.sleep(500L)
            attempt++
        }
        Thread.sleep(1500L)
    }
    if (taskId <= 0) return false
    // Each redirect op is best-effort, mirroring CarControlImpl (every reflective call there returns
    // a status string and swallows its own exception). resizeTask in particular throws "not allowed"
    // on a fullscreen task — that must NOT abort the move/focus or bubble up as a launchAndForce
    // failure, otherwise the caller tears down the VirtualDisplay Navi was just moved onto before it
    // can render. The VD size (mini=640 / full=1280) already sets the geometry, so a resize failure
    // is harmless.
    repeat(2) {
        runCatching { moveTaskToDisplayReflect(taskId, displayId) }
        runCatching { setTaskBoundsReflect(taskId, 0, 0, width, height) }
        runCatching { setFocusedTaskReflect(taskId) }
        Thread.sleep(200L)
    }
    return true
}

private const val BYD_BODYWORK_DEVICE_CLASS = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
private const val HELPER_VIN_READER_MARKER = "vinReader=v2"
private const val SHELL_EXEC_TIMEOUT_MS = 3000L
private const val SHELL_EXEC_TIMEOUT_CODE = -124
private const val EXEC_SHELL_TIMEOUT_MS = 600_000L
private const val EXEC_SHELL_MAX_CHARS = 4096
private const val EXEC_SHELL_OUTPUT_MAX_CHARS = 65_536
private const val HELPER_LOG_MAX_BYTES = 8 * 1024 * 1024L
private const val HELPER_LOG_KEEP_LINES = 8_000
private val HELPER_LOG_DATE = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)
private val EXEC_SHELL_BLOCKLIST = listOf(
    Regex("""rm\s+-[^\n]*\s+/\s"""),
    Regex("""rm\s+-[^\n]*\s+/\*"""),
    Regex("""\bdd\b[^\n]*\bof=/dev/block"""),
    Regex("""\bmkfs\b"""),
)
private val BYD_CONTEXT_PACKAGES = listOf(
    "com.byd.launcher",
    "com.byd.car.server",
    "com.byd.clusterdebug",
    "com.byd.vehicleconfig",
    "com.xy.instrumentnavigation",
    "com.android.launcher3",
)
private val BYD_VIN_METHODS = listOf("getRealAutoVIN", "getAutoVIN")
private val VIN_TOKEN = Regex("""[A-HJ-NPR-Z0-9]{17}""", RegexOption.IGNORE_CASE)
