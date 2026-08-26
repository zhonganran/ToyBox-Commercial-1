package com.byd.wjx.helper

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import java.lang.reflect.Modifier

@SuppressLint("PrivateApi")
internal fun acquireSystemContext(): Context? = try {
    val activityThreadClass = Class.forName("android.app.ActivityThread")
    val activityThread = activityThreadClass.getMethod("systemMain").invoke(null)
    activityThreadClass.getMethod("getSystemContext").invoke(activityThread) as? Context
} catch (error: Throwable) {
    System.err.println("WARN: systemContext unavailable: ${error.message}")
    null
}

@SuppressLint("PrivateApi")
internal fun readVehicleVinFromBodywork(systemContext: Context?): String {
    val out = StringBuilder()
    out.append(HELPER_VIN_READER_MARKER).append('\n')
    out.append("systemContext=").append(systemContext?.javaClass?.name ?: "null").append('\n')
    val context = systemContext ?: return out.append("bodywork=skip:no_context").toString()
    val clazz = try {
        Class.forName(BYD_BODYWORK_DEVICE_CLASS)
    } catch (error: Throwable) {
        return out.append("bodyworkClass=missing:").append(throwableSummary(error)).toString()
    }
    out.append("bodyworkClass=").append(clazz.name).append('\n')
    buildBodyworkContextCandidates(context).forEachIndexed { index, candidate ->
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

internal fun buildBodyworkContextCandidates(systemContext: Context): List<Context> {
    val baseContexts = mutableListOf<Context>()
    baseContexts += systemContext
    BYD_CONTEXT_PACKAGES.forEach { packageName ->
        runCatching {
            systemContext.createPackageContext(
                packageName,
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
    (this as? ContextWrapper)?.baseContext
        ?.let { "${it.javaClass.name}:${it.packageName}" }
        .orEmpty()

internal class PermissionBypassContext(base: Context) : ContextWrapper(base) {
    override fun checkCallingOrSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED

    override fun checkCallingPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED

    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
        PackageManager.PERMISSION_GRANTED

    override fun enforceCallingOrSelfPermission(permission: String, message: String?) = Unit

    override fun enforceCallingPermission(permission: String, message: String?) = Unit

    override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) = Unit
}

internal fun createBydBodyworkInstance(clazz: Class<*>, context: Context, out: StringBuilder): Any? {
    val contextMethods = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
        .filter {
            it.name == "getInstance" &&
                Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].isAssignableFrom(context.javaClass)
        }
        .distinctBy {
            "${it.declaringClass.name}:${it.name}:" +
                it.parameterTypes.joinToString(",") { type -> type.name }
        }
        .sortedBy { inheritanceDistance(context.javaClass, it.parameterTypes[0]) }
        .toList()
    contextMethods.forEach { method ->
        try {
            method.isAccessible = true
            val instance = method.invoke(null, context)
            if (instance != null) return instance
        } catch (error: Throwable) {
            out.append("getInstance(").append(context.javaClass.simpleName).append(")=error:")
                .append(throwableSummary(error)).append('\n')
        }
    }
    val noArgMethod = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence()).firstOrNull {
        it.name == "getInstance" && Modifier.isStatic(it.modifiers) && it.parameterTypes.isEmpty()
    } ?: return null
    return try {
        noArgMethod.isAccessible = true
        noArgMethod.invoke(null)
    } catch (error: Throwable) {
        out.append("getInstance()=error:").append(throwableSummary(error)).append('\n')
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
    var clazz: Class<*>? = instance.javaClass
    while (clazz != null) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == methodName && it.parameterTypes.isEmpty()
        }
        if (method != null) {
            return try {
                method.isAccessible = true
                method.invoke(instance)?.toString()
            } catch (error: Throwable) {
                "ERR:${throwableSummary(error)}"
            }
        }
        clazz = clazz.superclass
    }
    return try {
        instance.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterTypes.isEmpty()
        }?.invoke(instance)?.toString()
    } catch (error: Throwable) {
        "ERR:${throwableSummary(error)}"
    }
}

internal fun rawHasVin(raw: String): Boolean =
    VIN_TOKEN.findAll(raw).any { it.value.startsWith("L", ignoreCase = true) }

private const val BYD_BODYWORK_DEVICE_CLASS =
    "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
private const val HELPER_VIN_READER_MARKER = "vinReader=v2"
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
