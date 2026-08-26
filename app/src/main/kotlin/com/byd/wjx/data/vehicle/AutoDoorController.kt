package com.byd.wjx.data.vehicle

import android.os.SystemClock
import com.byd.wjx.log.WjxFileLogger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Guarded control path for BYD electric sliding doors.
 *
 * Mirrors Settings2.apk's checks before writing:
 * ready state == 1, same-side charging cover closed, speed < 3km/h.
 */
@Singleton
class AutoDoorController @Inject internal constructor(
    private val hardwareGateway: AutoDoorHardwareGateway,
    private val diagnostics: AutoDoorDiagnostics,
) {
    private val lastMotionBySide = ConcurrentHashMap<String, AutoDoorMotionDirection>()

    suspend fun toggleDoor(slotKey: String): AutoDoorControlResult {
        val snapshot = readDoorStates(listOf(slotKey)).firstOrNull()
            ?: return AutoDoorControlResult(
                success = false,
                message = "电滑门控制失败：未知车门 $slotKey",
            )
        val command = resolveAutoDoorToggleCommand(
            action = snapshot.action,
            percent = snapshot.percent,
            fallbackCommand = snapshot.nextCommand,
        )
        return runDoorCommand(slotKey, command)
    }

    suspend fun runDoorCommand(slotKey: String, commandValue: Int): AutoDoorControlResult = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        val target = AutoDoorCatalog.targets[slotKey] ?: return@withContext AutoDoorControlResult(
            success = false,
            message = "电滑门控制失败：未知车门 $slotKey",
        )
        val commandLabel = AutoDoorCatalog.commandLabels[commandValue] ?: return@withContext AutoDoorControlResult(
            success = false,
            message = "电滑门控制失败：未知命令 $commandValue",
        )

        diagnostics.commandStarted(target, commandValue)
        val helperReady = hardwareGateway.ensureReady("helper bootstrap")

        if (!helperReady) {
            diagnostics.helperUnavailable(target, commandLabel, commandValue)
            return@withContext AutoDoorControlResult(
                success = false,
                message = "电滑门控制失败：helper 未连接，日志已写入 ${WjxFileLogger.logFilePath}",
            )
        }

        val readings = hardwareGateway.readCommandReadings(target)

        fun blockedResult(reason: String): AutoDoorControlResult {
            diagnostics.commandBlocked(
                target = target,
                commandLabel = commandLabel,
                commandValue = commandValue,
                readings = readings,
                reason = reason,
                startedAt = startedAt,
            )
            return AutoDoorControlResult(
                success = false,
                message = "电滑门控制已拦截：$reason，日志已写入 ${WjxFileLogger.logFilePath}",
            )
        }

        autoDoorSafetyBlockReason(
            commandValue = commandValue,
            speedKmh = readings.speedKmh,
            slideReady = readings.slideReady,
            coverState = readings.coverState,
            defaultLimitKmh = target.maxSafeSpeedKmh,
        )?.let { reason -> return@withContext blockedResult(reason) }

        val write = hardwareGateway.writeCommand(target, commandValue)
        val accepted = write.status == 1
        if (accepted) {
            when (commandValue) {
                1 -> lastMotionBySide[target.key] = AutoDoorMotionDirection.OPENING
                2 -> lastMotionBySide[target.key] = AutoDoorMotionDirection.CLOSING
            }
        }
        delay(600L)
        val after = hardwareGateway.readState(target)
        diagnostics.commandCompleted(
            target = target,
            commandLabel = commandLabel,
            commandValue = commandValue,
            readings = readings,
            write = write,
            after = after,
            startedAt = startedAt,
        )
        AutoDoorControlResult(
            success = accepted,
            message = when (write.status) {
                1 -> "电滑门控制已执行：${target.label} $commandLabel，日志已写入 ${WjxFileLogger.logFilePath}"
                0 -> "电滑门控制未确认动作：${target.label} $commandLabel 底层返回 no-op，日志已写入 ${WjxFileLogger.logFilePath}"
                null -> "电滑门控制发送失败：${target.label} $commandLabel，helper 无返回，日志已写入 ${WjxFileLogger.logFilePath}"
                else -> "电滑门控制发送失败：${target.label} $commandLabel，status=${write.status}，日志已写入 ${WjxFileLogger.logFilePath}"
            },
        )
    }

    suspend fun readDoorStates(slotKeys: List<String> = listOf("slide_l", "slide_r")): List<AutoDoorSideSnapshot> = withContext(Dispatchers.IO) {
        val requestedKeys = slotKeys.distinct().filter { it in AutoDoorCatalog.targets }
        if (requestedKeys.isEmpty()) return@withContext emptyList()

        val helperReady = hardwareGateway.ensureReady("read door states bootstrap")

        if (!helperReady) {
            return@withContext requestedKeys.map { key ->
                AutoDoorCatalog.targets.getValue(key).toSnapshot(action = null, percent = null, statusOverride = "离线")
            }
        }

        requestedKeys.map { key ->
            val target = AutoDoorCatalog.targets.getValue(key)
            val readings = hardwareGateway.readState(target)
            target.toSnapshot(action = readings.action, percent = readings.percent)
        }
    }

    suspend fun runDoors(
        slotKeys: List<String>,
        commandValue: Int,
        groupLabel: String = "自动电滑门",
    ): AutoDoorControlResult {
        val commandLabel = AutoDoorCatalog.commandLabels[commandValue] ?: return AutoDoorControlResult(
            success = false,
            message = "自动电滑门控制失败：未知命令 $commandValue",
        )
        val requestedKeys = slotKeys.distinct().filter { it in AutoDoorCatalog.targets }
        if (requestedKeys.isEmpty()) {
            return AutoDoorControlResult(
                success = false,
                message = "自动电滑门$commandLabel 未发送成功：未选择有效车门",
            )
        }
        val startedAt = SystemClock.elapsedRealtime()
        diagnostics.groupStarted(groupLabel, requestedKeys, commandValue, commandLabel)
        val results = mutableListOf<Pair<String, AutoDoorControlResult>>()
        requestedKeys.forEachIndexed { index, key ->
            if (index > 0) delay(120L)
            results += key to runDoorCommand(key, commandValue)
        }
        val success = results.any { it.second.success }
        val sideSummary = results.joinToString("，") { (key, result) ->
            sideResult(AutoDoorCatalog.targets[key]?.shortLabel ?: key, result)
        }
        diagnostics.groupCompleted(
            groupLabel = groupLabel,
            requestedKeys = requestedKeys,
            commandValue = commandValue,
            commandLabel = commandLabel,
            results = results,
            summary = sideSummary,
            startedAt = startedAt,
        )
        return AutoDoorControlResult(
            success = success,
            message = if (success) {
                "自动电滑门$commandLabel 已发送（$groupLabel）：$sideSummary，日志已写入 ${WjxFileLogger.logFilePath}"
            } else {
                "自动电滑门$commandLabel 未发送成功（$groupLabel）：$sideSummary，日志已写入 ${WjxFileLogger.logFilePath}"
            },
        )
    }

    private fun sideResult(label: String, result: AutoDoorControlResult): String =
        "$label${if (result.success) "成功" else "未成功"}"

    private fun AutoDoorTarget.toSnapshot(
        action: Int?,
        percent: Int?,
        statusOverride: String? = null,
    ): AutoDoorSideSnapshot {
        val decision = resolveAutoDoorDisplayDecision(action, percent, lastMotionBySide[key])
        decision.rememberedMotion?.let { motion ->
            lastMotionBySide[key] = motion
        } ?: lastMotionBySide.remove(key)
        val nextCommand = decision.nextCommand
        val validPercent = percent?.takeIf { it in 0..100 }
        val status = statusOverride ?: decision.statusText
        if (statusOverride != null && action == null && percent == null) {
            lastMotionBySide.remove(key)
        }
        return AutoDoorSideSnapshot(
            key = key,
            label = label,
            shortLabel = shortLabel,
            action = action,
            percent = validPercent,
            statusText = status,
            nextCommand = nextCommand,
            nextCommandLabel = AutoDoorCatalog.commandLabels.getValue(nextCommand),
        )
    }

}
