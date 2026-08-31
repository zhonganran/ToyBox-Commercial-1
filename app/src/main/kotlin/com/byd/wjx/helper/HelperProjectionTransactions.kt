package com.byd.wjx.helper

import android.os.Parcel
import android.view.Surface

internal fun HelperDaemonRuntime.handleProjectionTransaction(
    code: Int,
    data: Parcel,
    reply: Parcel?,
): Boolean = when (code) {
    HelperBinderProtocol.TX_GET_TASK_ID -> twoIntReply(reply) {
        0 to findTaskId(data.readString().orEmpty())
    }

    HelperBinderProtocol.TX_MOVE_TASK_TO_DISPLAY -> twoIntReply(reply) {
        moveTaskToDisplayReflect(data.readInt(), data.readInt())
        0 to 0
    }

    HelperBinderProtocol.TX_SET_TASK_BOUNDS -> twoIntReply(reply) {
        val taskId = data.readInt()
        val left = data.readInt()
        val top = data.readInt()
        val right = data.readInt()
        val bottom = data.readInt()
        setTaskBoundsReflect(taskId, left, top, right, bottom)
        0 to 0
    }

    HelperBinderProtocol.TX_SET_FOCUSED_TASK -> twoIntReply(reply) {
        setFocusedTaskReflect(data.readInt())
        0 to 0
    }

    HelperBinderProtocol.TX_SET_TASK_WINDOWING_MODE -> twoIntReply(reply) {
        setTaskWindowingModeReflect(data.readInt(), data.readInt())
        0 to 0
    }

    HelperBinderProtocol.TX_CREATE_VIRTUAL_DISPLAY -> handleCreateVirtualDisplay(data, reply)

    HelperBinderProtocol.TX_RELEASE_VIRTUAL_DISPLAY -> twoIntReply(reply) {
        virtualDisplays.release(data.readInt())
        0 to 0
    }

    HelperBinderProtocol.TX_LAUNCH_APP -> twoIntReply(reply) {
        (if (launchApp(data.readString().orEmpty())) 0 else -1) to 0
    }

    HelperBinderProtocol.TX_GRANT_OVERLAY_PERMISSION -> twoIntReply(reply) {
        val packageName = HelperBinderProtocol.APP_PACKAGE
        val overlay = shExec("appops set \"\$1\" SYSTEM_ALERT_WINDOW allow", packageName)
        val projection = shExec("appops set \"\$1\" PROJECT_MEDIA allow", packageName)
        helperLog("Permission", "grant overlay=${overlay.code} project=${projection.code}")
        (if (overlay.code == 0 && projection.code == 0) 0 else -1) to 0
    }

    HelperBinderProtocol.TX_LAUNCH_AND_FORCE -> twoIntReply(reply) {
        val packageName = data.readString().orEmpty()
        val displayId = data.readInt()
        val width = data.readInt()
        val height = data.readInt()
        (if (launchAndForce(packageName, displayId, width, height)) 0 else -1) to 0
    }

    HelperBinderProtocol.TX_ENABLE_ACCESSIBILITY -> twoIntReply(reply) {
        (if (enableAccessibilityService()) 0 else -1) to 0
    }

    HelperBinderProtocol.TX_SET_CLUSTER_CONTAINER_MODE -> twoIntReply(reply) {
        val enabled = data.readInt() != 0
        (if (setClusterContainerMode(enabled)) 0 else -1) to 0
    }

    else -> error("projection transaction routed incorrectly: $code")
}

/**
 * Controls BYD's cluster compositor through the only three commands used by the camera flow.
 * DiLink generations expose the service under two names, so the second name is a narrow fallback.
 */
internal fun setClusterContainerMode(
    enabled: Boolean,
    call: (Int) -> Boolean = ::autoContainerCall,
    pause: (Long) -> Unit = Thread::sleep,
): Boolean {
    if (enabled) return call(AUTO_CONTAINER_ENABLE)
    val detached = call(AUTO_CONTAINER_DETACH)
    pause(AUTO_CONTAINER_DETACH_DELAY_MS)
    return call(AUTO_CONTAINER_DISABLE) && detached
}

internal fun autoContainerCall(
    command: Int,
    exec: (String, String) -> Int = { script, arg -> shExec(script, arg).code },
): Boolean {
    require(command in AUTO_CONTAINER_COMMANDS) { "auto_container command $command is not allowed" }
    if (exec(AUTO_CONTAINER_SERVICE_CALL, command.toString()) == 0) return true
    return exec(AUTO_CONTAINER_FALLBACK_SERVICE_CALL, command.toString()) == 0
}

private const val AUTO_CONTAINER_ENABLE = 16
private const val AUTO_CONTAINER_DETACH = 18
private const val AUTO_CONTAINER_DISABLE = 0
private const val AUTO_CONTAINER_DETACH_DELAY_MS = 1_000L
private val AUTO_CONTAINER_COMMANDS = setOf(
    AUTO_CONTAINER_ENABLE,
    AUTO_CONTAINER_DETACH,
    AUTO_CONTAINER_DISABLE,
)
private const val AUTO_CONTAINER_SERVICE_CALL =
    "service call auto_container 2 i32 1000 i32 \"\$1\" s16 \"\""
private const val AUTO_CONTAINER_FALLBACK_SERVICE_CALL =
    "service call AutoContainer 2 i32 1000 i32 \"\$1\" s16 \"\""

private fun HelperDaemonRuntime.handleCreateVirtualDisplay(data: Parcel, reply: Parcel?): Boolean {
    var surface: Surface? = null
    return runCatching {
        val name = data.readString() ?: "SuperToyBox_VD"
        val width = data.readInt()
        val height = data.readInt()
        val density = data.readInt()
        val flags = data.readInt()
        surface = Surface.CREATOR.createFromParcel(data)
        val currentSurface = surface ?: error("surface unavailable")
        val context = systemContext
        val displayId = if (context == null || !currentSurface.isValid) {
            -1
        } else {
            createVirtualDisplay(
                context,
                virtualDisplays,
                name,
                width,
                height,
                density,
                currentSurface,
                flags,
            )
        }
        if (displayId > 0) {
            reply?.writeInt(0)
            reply?.writeInt(displayId)
        } else {
            currentSurface.release()
            surface = null
            reply?.writeInt(-1)
            reply?.writeInt(0)
        }
        true
    }.getOrElse {
        surface?.release()
        reply?.writeInt(-1)
        reply?.writeInt(0)
        true
    }
}

private inline fun twoIntReply(reply: Parcel?, block: () -> Pair<Int, Int>): Boolean =
    runCatching {
        val (status, value) = block()
        reply?.writeInt(status)
        reply?.writeInt(value)
        true
    }.getOrElse {
        reply?.writeInt(-1)
        reply?.writeInt(0)
        true
    }
