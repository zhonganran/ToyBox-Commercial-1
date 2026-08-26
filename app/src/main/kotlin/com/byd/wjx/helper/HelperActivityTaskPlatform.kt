package com.byd.wjx.helper

import android.annotation.SuppressLint
import android.content.ComponentName
import android.graphics.Rect

@SuppressLint("PrivateApi")
private fun activityTaskManager(): Any =
    Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        ?: throw IllegalStateException("ActivityTaskManager.getService() returned null")

private fun fieldByName(target: Any, name: String): java.lang.reflect.Field? {
    var cls: Class<*>? = target.javaClass
    while (cls != null) {
        try {
            return cls.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            cls = cls.superclass
        }
    }
    return null
}

internal fun findTaskId(packageName: String): Int {
    val service = activityTaskManager()
    val getTasks = service.javaClass.getMethod(
        "getTasks",
        Int::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
    )
    val tasks = getTasks.invoke(service, 100, false, false) as? List<*> ?: return -1
    for (task in tasks) {
        if (task == null) continue
        val pkg = listOf("topActivity", "baseActivity").firstNotNullOfOrNull { fieldName ->
            fieldByName(task, fieldName)?.let { field ->
                field.isAccessible = true
                (field.get(task) as? ComponentName)?.packageName
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

/** Prefers moveRootTaskToDisplay and retains the older moveTaskToDisplay fallback. */
internal fun moveTaskToDisplayReflect(taskId: Int, displayId: Int) {
    val service = activityTaskManager()
    val method = service.javaClass.methods.firstOrNull {
        it.name == "moveRootTaskToDisplay" && it.parameterTypes.size == 2
    } ?: service.javaClass.methods.firstOrNull {
        it.name == "moveTaskToDisplay" && it.parameterTypes.size == 2
    } ?: throw NoSuchMethodException("moveTaskToDisplay")
    method.invoke(service, taskId, displayId)
}

internal fun setTaskWindowingModeReflect(taskId: Int, windowingMode: Int) {
    val service = activityTaskManager()
    service.javaClass.getMethod(
        "setTaskWindowingMode",
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
    ).invoke(service, taskId, windowingMode, true)
}

internal fun setTaskBoundsReflect(
    taskId: Int,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
) {
    val service = activityTaskManager()
    val resizeTask = service.javaClass.getMethod(
        "resizeTask",
        Int::class.javaPrimitiveType,
        Rect::class.java,
        Int::class.javaPrimitiveType,
    )
    val bounds = if (left == 0 && top == 0 && right == 0 && bottom == 0) {
        null
    } else {
        Rect(left, top, right, bottom)
    }
    resizeTask.invoke(service, taskId, bounds, 1)
}

internal fun setFocusedTaskReflect(taskId: Int) {
    val service = activityTaskManager()
    service.javaClass.getMethod("setFocusedRootTask", Int::class.javaPrimitiveType)
        .invoke(service, taskId)
}

internal fun launchApp(packageName: String): Boolean {
    val resolve = execShell(
        "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $packageName",
    )
    val component = resolve.lineSequence()
        .firstOrNull { it.contains("/") && !it.startsWith("No ") }
        ?.trim()
    if (component != null && !execShell("am start -n $component").contains("Error")) return true
    if (!execShell("am start -a android.intent.action.MAIN $packageName").contains("Error")) return true
    val monkey = execShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    return !monkey.contains("Error") && !monkey.contains("error")
}

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
    repeat(2) {
        runCatching { moveTaskToDisplayReflect(taskId, displayId) }
        runCatching { setTaskBoundsReflect(taskId, 0, 0, width, height) }
        runCatching { setFocusedTaskReflect(taskId) }
        Thread.sleep(200L)
    }
    return true
}
