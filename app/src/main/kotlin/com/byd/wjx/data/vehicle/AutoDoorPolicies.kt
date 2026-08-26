package com.byd.wjx.data.vehicle

import java.util.Locale
import kotlin.math.abs

internal fun autoDoorSpeedLimitKmh(commandValue: Int, defaultLimitKmh: Float): Float =
    if (commandValue == 1) minOf(AUTO_DOOR_OPEN_SPEED_LIMIT_KMH, defaultLimitKmh) else defaultLimitKmh

internal fun isAutoDoorCommandBlockedBySpeed(
    commandValue: Int,
    speedKmh: Float,
    defaultLimitKmh: Float,
): Boolean = abs(speedKmh) >= autoDoorSpeedLimitKmh(commandValue, defaultLimitKmh)

internal fun resolveAutoDoorToggleCommand(
    action: Int?,
    percent: Int?,
    fallbackCommand: Int,
): Int = when {
    action == 2 -> 2
    action == 3 -> 1
    percent == 100 -> 2
    percent != null && percent in 0..2 -> 1
    else -> fallbackCommand
}

internal fun resolveAutoDoorDisplayDecision(
    action: Int?,
    percent: Int?,
    previousMotion: AutoDoorMotionDirection?,
): AutoDoorDisplayDecision {
    val validPercent = percent?.takeIf { it in 0..100 }
    return when {
        action == 2 -> AutoDoorDisplayDecision("动作中", 3, AutoDoorMotionDirection.OPENING)
        action == 3 -> AutoDoorDisplayDecision("动作中", 3, AutoDoorMotionDirection.CLOSING)
        validPercent == 100 -> AutoDoorDisplayDecision("已开", 2, null)
        validPercent != null && validPercent <= 2 -> AutoDoorDisplayDecision("已关", 1, null)
        validPercent != null -> AutoDoorDisplayDecision(
            statusText = "半开 $validPercent%",
            nextCommand = if (previousMotion == AutoDoorMotionDirection.OPENING) 2 else 1,
            rememberedMotion = previousMotion,
        )
        else -> AutoDoorDisplayDecision("未知", 1, previousMotion)
    }
}

internal fun autoDoorSafetyBlockReason(
    commandValue: Int,
    speedKmh: Float?,
    slideReady: Int?,
    coverState: Int?,
    defaultLimitKmh: Float,
): String? {
    if (speedKmh == null) return "无法确认车速"
    val speedLimit = autoDoorSpeedLimitKmh(commandValue, defaultLimitKmh)
    if (isAutoDoorCommandBlockedBySpeed(commandValue, speedKmh, defaultLimitKmh)) {
        return if (commandValue == 1) {
            "开门要求车速低于 ${speedLimit.toInt()}km/h（当前 ${"%.2f".format(Locale.US, speedKmh)}km/h）"
        } else {
            "车速达到或超过安全阈值（${"%.2f".format(Locale.US, speedKmh)}km/h >= ${speedLimit}km/h）"
        }
    }
    if (slideReady == null) return "无法确认电滑门准备状态"
    if (slideReady != 1) return "电滑门未就绪，官方要求 BODYWORK_ELEC_SLIDE_STATE=1"
    if (coverState == null) return "无法确认同侧充电口盖状态"
    if (coverState != 1) return "同侧充电口盖未关闭，官方会拦截"
    return null
}

private const val AUTO_DOOR_OPEN_SPEED_LIMIT_KMH = 1.0f
