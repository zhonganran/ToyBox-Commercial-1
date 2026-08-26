package com.byd.wjx.data.automation

import org.json.JSONObject

internal const val AUTOMATION_MAX_DELAY_MS = 30_000L
private const val AUTO_PARKING_ACTIVE_STATUS = 4
private val POSITION_OPEN = Regex("打开(\\d+)")
private val WINDOW_SUBJECTS = listOf("车窗", "天窗", "主驾", "副驾", "后左", "后右", "遮阳帘")
private val OPEN_WORDS = listOf("全开", "半开", "通风")
private val BLOCKED_COMMAND_PATTERNS = listOf("发送CAN", "执行SHELL", "下电")

internal fun isAutomationWindowOpenCommand(command: String): Boolean {
    if (WINDOW_SUBJECTS.none(command::contains)) return false
    if (command.contains("关")) return false
    val opensViaPosition = POSITION_OPEN.find(command)
        ?.let { match -> match.groupValues[1].toInt() > 0 }
        ?: command.contains("打开")
    return opensViaPosition || OPEN_WORDS.any(command::contains)
}

internal fun automationWindowOpenBlockReason(command: String, speedKmh: Int?): String? {
    if (!isAutomationWindowOpenCommand(command)) return null
    val speed = speedKmh ?: return "当前车速未知"
    return if (speed > 80) "车速 $speed km/h，已阻止开窗（>80）" else null
}

internal fun automationCommandBlockReason(command: String, speedKmh: Int?): String? {
    if (BLOCKED_COMMAND_PATTERNS.any(command::contains)) return "禁止执行的命令"
    return automationWindowOpenBlockReason(command, speedKmh)
}

internal fun shouldSkipAutomationOutsideSound(
    owner: String?,
    parkingStatus: Int?,
    allowDuringAutoParking: Boolean,
): Boolean = owner?.startsWith("automation:") == true &&
    parkingStatus == AUTO_PARKING_ACTIVE_STATUS &&
    !allowDuringAutoParking

internal fun clampAutomationVolume(level: Int, max: Int): Int =
    level.coerceIn(0, max.coerceAtLeast(0))

internal fun parseAutomationActionPayload(payload: String?): JSONObject? {
    if (payload.isNullOrBlank()) return null
    return runCatching { JSONObject(payload) }.getOrNull()
}
