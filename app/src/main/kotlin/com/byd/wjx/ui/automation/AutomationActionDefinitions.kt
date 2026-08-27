package com.byd.wjx.ui.automation

import android.content.Context
import com.byd.wjx.data.automation.MirrorTiltRequest
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.storage.SoundLibrary
import org.json.JSONObject

// --- Action kind helpers ---

fun newNotificationAction(silent: Boolean, context: Context): ActionDef {
    val name = if (silent)
        "通知（静音）"
    else
        "通知（带声音）"
    return ActionDef(
        command = "",
        displayName = name,
        kind = if (silent) "notification_silent" else "notification_sound",
        payload = """{"title":"","text":""}"""
    )
}

fun ActionDef.notificationTitle(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("title")
} catch (e: Exception) { "" }

fun ActionDef.notificationText(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("text")
} catch (e: Exception) { "" }

fun ActionDef.withNotification(title: String, text: String): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("title", title)
        put("text", text)
    }.toString()
)

// --- App launch helpers ---

fun newAppLaunchAction(context: Context): ActionDef = ActionDef(
    command = "",
    displayName = "启动应用",
    kind = "app_launch",
    payload = """{"packageName":"","appLabel":""}"""
)

fun ActionDef.appLaunchPackageName(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("packageName")
} catch (e: Exception) { "" }

fun ActionDef.appLaunchLabel(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("appLabel")
} catch (e: Exception) { "" }

fun ActionDef.appLaunchMinimize(): Boolean = try {
    org.json.JSONObject(payload ?: "{}").optBoolean("minimize", false)
} catch (e: Exception) { false }

fun ActionDef.withAppLaunch(packageName: String, appLabel: String, minimize: Boolean): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("minimize", minimize)
    }.toString()
)

// --- Call helpers ---

fun newCallAction(context: Context): ActionDef = ActionDef(
    command = "",
    displayName = "拨打电话",
    kind = "call",
    payload = """{"phone":""}"""
)

fun ActionDef.callPhone(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("phone")
} catch (e: Exception) { "" }

fun ActionDef.callName(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("name")
} catch (e: Exception) { "" }

fun ActionDef.callAutoDial(): Boolean = try {
    org.json.JSONObject(payload ?: "{}").optBoolean("autoDial", false)
} catch (e: Exception) { false }

fun ActionDef.withCall(phone: String, name: String, autoDial: Boolean): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("phone", phone)
        put("name", name)
        put("autoDial", autoDial)
    }.toString()
)

// --- Navigate helpers ---

fun newNavigateAction(context: Context): ActionDef = ActionDef(
    command = "",
    displayName = "高德地图路线",
    kind = "navigate",
    payload = """{"lat":0,"lon":0,"name":""}"""
)

fun ActionDef.navigateLat(): Double? = try {
    val d = org.json.JSONObject(payload ?: "{}").optDouble("lat", Double.NaN)
    if (d.isNaN()) null else d
} catch (e: Exception) { null }

fun ActionDef.navigateLon(): Double? = try {
    val d = org.json.JSONObject(payload ?: "{}").optDouble("lon", Double.NaN)
    if (d.isNaN()) null else d
} catch (e: Exception) { null }

fun ActionDef.navigateName(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("name")
} catch (e: Exception) { "" }

fun ActionDef.withNavigate(lat: Double, lon: Double, name: String): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("lat", lat)
        put("lon", lon)
        put("name", name)
    }.toString()
)

// --- 链接动作 ---

fun newUrlAction(context: Context): ActionDef = ActionDef(
    command = "",
    displayName = "打开链接",
    kind = "url",
    payload = """{"url":""}"""
)

fun ActionDef.urlString(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("url")
} catch (e: Exception) { "" }

fun ActionDef.urlMinimize(): Boolean = try {
    org.json.JSONObject(payload ?: "{}").optBoolean("minimize", false)
} catch (e: Exception) { false }

fun ActionDef.withUrl(url: String, minimize: Boolean): ActionDef = copy(
    payload = org.json.JSONObject().apply {
        put("url", url)
        put("minimize", minimize)
    }.toString()
)

// --- WJX 音效库动作 ---

fun newInsideSoundAction(context: Context): ActionDef {
    val firstSound = SoundLibrary(context.applicationContext).listSounds().firstOrNull()
    val name = firstSound?.name.orEmpty()
    val path = firstSound?.file?.absolutePath.orEmpty()
    return ActionDef(
        command = "inside_sound",
        displayName = insideSoundDisplayName(name, loop = false),
        kind = "inside_sound",
        payload = org.json.JSONObject().apply {
            put("path", path)
            put("name", name)
            put("loop", false)
        }.toString()
    )
}

fun ActionDef.insideSoundPath(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("path")
} catch (_: Exception) { "" }

fun ActionDef.insideSoundName(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("name")
} catch (_: Exception) { "" }

fun ActionDef.insideSoundLoop(): Boolean = try {
    org.json.JSONObject(payload ?: "{}").optBoolean("loop", false)
} catch (_: Exception) { false }

fun ActionDef.withInsideSound(sound: SoundLibrary.SoundItem): ActionDef = copy(
    displayName = insideSoundDisplayName(sound.name, insideSoundLoop()),
    payload = org.json.JSONObject().apply {
        put("path", sound.file.absolutePath)
        put("name", sound.name)
        put("loop", insideSoundLoop())
    }.toString()
)

fun ActionDef.withInsideSoundLoop(loop: Boolean): ActionDef = copy(
    displayName = insideSoundDisplayName(insideSoundName(), loop),
    payload = org.json.JSONObject(payload ?: "{}").apply {
        put("loop", loop)
    }.toString()
)

private fun insideSoundDisplayName(name: String, loop: Boolean): String {
    val base = if (name.isBlank()) "车内播放音效" else "车内播放：$name"
    return if (loop) "$base（循环）" else base
}

fun newOutsideSoundAction(context: Context): ActionDef {
    val firstSound = SoundLibrary(context.applicationContext).listSounds().firstOrNull()
    val name = firstSound?.name.orEmpty()
    val path = firstSound?.file?.absolutePath.orEmpty()
    return ActionDef(
        command = "outside_sound",
        displayName = if (name.isBlank()) "车外播放音效" else "车外播放：$name",
        kind = "outside_sound",
        payload = org.json.JSONObject().apply {
            put("path", path)
            put("name", name)
            put("loop", false)
        }.toString()
    )
}

fun ActionDef.outsideSoundPath(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("path")
} catch (e: Exception) { "" }

fun ActionDef.outsideSoundName(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("name")
} catch (e: Exception) { "" }

fun ActionDef.outsideSoundLoop(): Boolean = try {
    org.json.JSONObject(payload ?: "{}").optBoolean("loop", false)
} catch (e: Exception) { false }

fun ActionDef.withOutsideSound(sound: SoundLibrary.SoundItem): ActionDef = copy(
    displayName = outsideSoundDisplayName(sound.name, outsideSoundLoop()),
    payload = org.json.JSONObject().apply {
        put("path", sound.file.absolutePath)
        put("name", sound.name)
        put("loop", outsideSoundLoop())
    }.toString()
)

fun ActionDef.withOutsideSoundLoop(loop: Boolean): ActionDef = copy(
    displayName = outsideSoundDisplayName(outsideSoundName(), loop),
    payload = org.json.JSONObject(payload ?: "{}").apply {
        put("loop", loop)
    }.toString()
)

private fun outsideSoundDisplayName(name: String, loop: Boolean): String {
    val base = if (name.isBlank()) "车外播放音效" else "车外播放：$name"
    return if (loop) "$base（循环）" else base
}

// --- 自动滑门动作 ---

fun newAutoDoorAction(): ActionDef = ActionDef(
    command = "auto_door",
    displayName = AUTO_DOOR_ACTIONS.first().label,
    kind = "auto_door",
    payload = autoDoorPayload(AUTO_DOOR_ACTIONS.first())
)

// --- 后视镜位置动作 ---

internal fun newMirrorTiltAction(): ActionDef {
    val request = MirrorTiltRequest(
        leftEnabled = true,
        rightEnabled = true,
        leftHorizontalOffset = MirrorTiltRequest.DEFAULT_HORIZONTAL_OFFSET,
        leftVerticalOffset = MirrorTiltRequest.DEFAULT_VERTICAL_OFFSET,
        rightHorizontalOffset = MirrorTiltRequest.DEFAULT_HORIZONTAL_OFFSET,
        rightVerticalOffset = MirrorTiltRequest.DEFAULT_VERTICAL_OFFSET,
    )
    return ActionDef(
        command = "mirror_tilt",
        displayName = mirrorTiltDisplayName(request),
        kind = "mirror_tilt",
        payload = MirrorTiltRequest.payload(request),
    )
}

internal fun newMirrorRestoreAction(): ActionDef = ActionDef(
    command = "mirror_restore",
    displayName = "恢复后视镜位置",
    kind = "mirror_restore",
)

internal fun ActionDef.mirrorTiltRequest(): MirrorTiltRequest? = MirrorTiltRequest.parse(payload)

internal fun ActionDef.withMirrorTiltRequest(request: MirrorTiltRequest): ActionDef = copy(
    command = "mirror_tilt",
    displayName = mirrorTiltDisplayName(request),
    kind = "mirror_tilt",
    payload = MirrorTiltRequest.payload(request),
)

private fun mirrorTiltDisplayName(request: MirrorTiltRequest): String {
    val sides = buildList {
        if (request.leftEnabled) {
            add("左 ${request.leftHorizontalOffset.axisOffset("横")} ${request.leftVerticalOffset.axisOffset("纵")}")
        }
        if (request.rightEnabled) {
            add("右 ${request.rightHorizontalOffset.axisOffset("横")} ${request.rightVerticalOffset.axisOffset("纵")}")
        }
    }.joinToString(" · ")
    return if (sides.isBlank()) "后视镜下翻" else "后视镜下翻：$sides"
}

private fun Float.axisOffset(axis: String): String {
    val value = toInt()
    return "$axis${if (value > 0) "+$value" else value.toString()}"
}

fun newStopOutsideSoundAction(): ActionDef = ActionDef(
    command = "stop_outside_sound",
    displayName = "停止车外音效",
    kind = "stop_outside_sound",
)

fun newStopInsideSoundAction(): ActionDef = ActionDef(
    command = "stop_inside_sound",
    displayName = "停止车内音效",
    kind = "stop_inside_sound",
)

fun newGoHomeAction(): ActionDef = ActionDef(
    command = "go_home",
    displayName = "返回桌面",
    kind = "go_home",
)

fun newHttpAction(): ActionDef = ActionDef(
    command = "http",
    displayName = "HTTP GET",
    kind = "http",
    payload = """{"method":"GET","url":"","body":"","contentType":"application/json"}""",
)

fun newPlayMusicAction(): ActionDef = ActionDef(
    command = "play_music",
    displayName = "播放音乐",
    kind = "play_music",
    payload = """{"path":"","fileName":"","volume":30,"loop":false}""",
)

fun newStopMusicAction(): ActionDef = ActionDef(
    command = "stop_music",
    displayName = "暂停播放音乐",
    kind = "stop_music",
)

fun newPlayVideoAction(): ActionDef = ActionDef(
    command = "play_video",
    displayName = "播放视频",
    kind = "play_video",
    payload = """{"path":"","fileName":"","volume":30}""",
)

fun newStopVideoAction(): ActionDef = ActionDef(
    command = "stop_video",
    displayName = "结束视频播放",
    kind = "stop_video",
)

fun newSentryModeAction(enabled: Boolean = true): ActionDef = ActionDef(
    command = if (enabled) "sentry_on" else "sentry_off",
    displayName = if (enabled) "开启哨兵模式" else "关闭哨兵模式",
    kind = "sentry_mode",
    payload = if (enabled) "on" else "off",
)

fun ActionDef.automationMediaPath(): String = try {
    JSONObject(payload ?: "{}").optString("path")
} catch (_: Exception) { "" }

fun ActionDef.automationMediaFileName(): String = try {
    JSONObject(payload ?: "{}").optString("fileName")
} catch (_: Exception) { "" }

fun ActionDef.automationMediaVolume(): Int = try {
    JSONObject(payload ?: "{}").optInt("volume", 30).coerceIn(0, 100)
} catch (_: Exception) { 30 }

fun ActionDef.automationMediaLoop(): Boolean = try {
    JSONObject(payload ?: "{}").optBoolean("loop", false)
} catch (_: Exception) { false }

fun ActionDef.withAutomationMedia(path: String, fileName: String): ActionDef = copy(
    displayName = when (kind) {
        "play_video" -> "播放视频：$fileName"
        else -> "播放音乐：$fileName"
    },
    payload = automationMediaPayload().apply {
        put("path", path)
        put("fileName", fileName)
        if (!has("volume")) put("volume", 30)
        if (kind == "play_music" && !has("loop")) put("loop", false)
    }.toString(),
)

fun ActionDef.withAutomationMediaVolume(volume: Int): ActionDef = copy(
    payload = automationMediaPayload().apply {
        put("volume", volume.coerceIn(0, 100))
    }.toString(),
)

fun ActionDef.withAutomationMediaLoop(loop: Boolean): ActionDef = copy(
    payload = automationMediaPayload().apply { put("loop", loop) }.toString(),
)

private fun ActionDef.automationMediaPayload(): JSONObject = try {
    JSONObject(payload ?: "{}")
} catch (_: Exception) {
    JSONObject()
}

fun ActionDef.withSentryMode(enabled: Boolean): ActionDef = copy(
    command = if (enabled) "sentry_on" else "sentry_off",
    displayName = if (enabled) "开启哨兵模式" else "关闭哨兵模式",
    payload = if (enabled) "on" else "off",
)

fun ActionDef.httpMethod(): String = try {
    JSONObject(payload ?: "{}").optString("method", "GET").uppercase()
} catch (_: Exception) { "GET" }

fun ActionDef.httpUrl(): String = try {
    JSONObject(payload ?: "{}").optString("url")
} catch (_: Exception) { "" }

fun ActionDef.withHttp(method: String, url: String): ActionDef = copy(
    displayName = "HTTP ${method.uppercase()}",
    payload = JSONObject(payload ?: "{}").apply {
        put("method", method.uppercase())
        put("url", url)
        if (!has("body")) put("body", "")
        if (!has("contentType")) put("contentType", "application/json")
    }.toString(),
)

fun ActionDef.autoDoorSide(): String = try {
    org.json.JSONObject(payload ?: "{}").optString("side")
} catch (e: Exception) { "" }

fun ActionDef.autoDoorCommandValue(): Int = try {
    org.json.JSONObject(payload ?: "{}").optInt("commandValue", -1)
} catch (e: Exception) { -1 }

fun ActionDef.autoDoorActionOption(): AutoDoorActionOption? =
    AUTO_DOOR_ACTIONS.firstOrNull {
        it.side == autoDoorSide() && it.commandValue == autoDoorCommandValue()
    }

fun ActionDef.withAutoDoorAction(option: AutoDoorActionOption): ActionDef = copy(
    command = "auto_door",
    displayName = option.label,
    kind = "auto_door",
    payload = autoDoorPayload(option)
)

private fun autoDoorPayload(option: AutoDoorActionOption): String =
    org.json.JSONObject().apply {
        put("side", option.side)
        put("commandValue", option.commandValue)
    }.toString()

/**
 * Pure list reorder used by the automation editor's up/down arrows: returns a new
 * list with the item at [index] swapped with its neighbour. No-op (returns the same
 * order) when [index] is at the boundary or out of range.
 */
internal fun <T> List<T>.moveItem(index: Int, up: Boolean): List<T> {
    val target = if (up) index - 1 else index + 1
    if (index !in indices || target !in indices) return this
    return toMutableList().apply {
        val tmp = this[index]; this[index] = this[target]; this[target] = tmp
    }
}
