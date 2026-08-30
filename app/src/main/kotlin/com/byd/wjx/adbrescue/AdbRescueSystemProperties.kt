package com.byd.wjx.adbrescue

import com.byd.wjx.log.WjxFileLogger
import java.lang.reflect.InvocationTargetException

internal interface AdbRescueSystemPropertyAccess {
    fun get(key: String, fallback: String): String
    fun set(key: String, value: String): Boolean
}

internal object ReflectiveAdbRescueSystemPropertyAccess : AdbRescueSystemPropertyAccess {
    override fun get(key: String, fallback: String): String = runCatching {
        val type = Class.forName(CLASS_NAME)
        val method = type.getDeclaredMethod("get", String::class.java, String::class.java)
        method.isAccessible = true
        method.invoke(null, key, fallback)?.toString() ?: fallback
    }.getOrElse { error ->
        WjxFileLogger.w(TAG, "Unable to read system property $key: ${unwrap(error).message}")
        fallback
    }

    override fun set(key: String, value: String): Boolean = runCatching {
        val type = Class.forName(CLASS_NAME)
        val method = type.getDeclaredMethod("set", String::class.java, String::class.java)
        method.isAccessible = true
        method.invoke(null, key, value)
        true
    }.getOrElse { error ->
        WjxFileLogger.w(TAG, "Unable to write system property $key: ${unwrap(error).message}")
        false
    }

    private fun unwrap(error: Throwable): Throwable =
        if (error is InvocationTargetException && error.cause != null) error.cause!! else error

    private const val CLASS_NAME = "android.os.SystemProperties"
    private const val TAG = "AdbRescueProperties"
}
