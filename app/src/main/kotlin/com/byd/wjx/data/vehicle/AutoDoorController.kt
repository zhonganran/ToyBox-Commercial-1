package com.byd.wjx.data.vehicle

import android.os.Build
import android.os.SystemClock
import com.byd.wjx.data.autoservice.SentinelDecoder
import com.byd.wjx.log.WjxFileLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class AutoDoorControlResult(
    val success: Boolean,
    val message: String,
)

data class AutoDoorSideSnapshot(
    val key: String,
    val label: String,
    val shortLabel: String,
    val action: Int?,
    val percent: Int?,
    val statusText: String,
    val nextCommand: Int,
    val nextCommandLabel: String,
)

internal enum class AutoDoorMotionDirection {
    OPENING,
    CLOSING,
}

internal data class AutoDoorDisplayDecision(
    val statusText: String,
    val nextCommand: Int,
    val rememberedMotion: AutoDoorMotionDirection?,
)

/**
 * Mirrors Settings' state machine:
 * 100% is fully open, 0..2% is fully closed, and a stopped intermediate door
 * continues in the opposite direction of the motion that was paused.
 */
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

/**
 * Guarded control path for BYD electric sliding doors.
 *
 * Mirrors Settings2.apk's checks before writing:
 * ready state == 1, same-side charging cover closed, speed < 3km/h.
 */
@Singleton
class AutoDoorController @Inject constructor(
    private val helperBootstrap: HelperBootstrap,
    private val helper: HelperClient,
) {
    private val lastMotionBySide = ConcurrentHashMap<String, AutoDoorMotionDirection>()

    suspend fun runDoorCommand(slotKey: String, commandValue: Int): AutoDoorControlResult = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        val target = TARGETS[slotKey] ?: return@withContext AutoDoorControlResult(
            success = false,
            message = "电滑门控制失败：未知车门 $slotKey",
        )
        val commandLabel = COMMAND_LABELS[commandValue] ?: return@withContext AutoDoorControlResult(
            success = false,
            message = "电滑门控制失败：未知命令 $commandValue",
        )

        WjxFileLogger.i(TAG, "start command target=${target.key} command=$commandValue")
        val helperReady = runCatching { helperBootstrap.ensureCurrent() }
            .onFailure { WjxFileLogger.w(TAG, "helper bootstrap failed: ${it.message}", it) }
            .getOrDefault(false)

        if (!helperReady) {
            val body = buildWriteHeader(target, commandLabel, commandValue, false) +
                "\nblocked=helper unavailable\n"
            WjxFileLogger.appendSection("电滑门控制", body)
            return@withContext AutoDoorControlResult(
                success = false,
                message = "电滑门控制失败：helper 未连接，日志已写入 ${WjxFileLogger.logFilePath}",
            )
        }

        val speedRaw = readRaw(DEV_SPEED, FID_SPEED, 7)
        val speed = speedRaw?.toInt()?.let(SentinelDecoder::parseFloatFromShellInt)
        val slideReadyRaw = readRaw(DEV_BODYWORK, FID_ELEC_SLIDE_STATE, 5)
        val slideReady = slideReadyRaw?.toInt()?.let(SentinelDecoder::decodeInt)
        val coverRaw = readRaw(DEV_CHARGING, target.coverFid, 5)
        val cover = coverRaw?.toInt()?.let(SentinelDecoder::decodeInt)
        val beforeState = readDecoded(DEV_BODYWORK, target.stateFid, 5)
        val beforePosition = readDecoded(DEV_BODYWORK, target.positionFid, 5)

        fun blockedResult(reason: String): AutoDoorControlResult {
            val body = buildString {
                append(buildWriteHeader(target, commandLabel, commandValue, true))
                appendLine("mode=WRITE_COMMAND")
                appendLine("safety=写入前执行设置2同款前置检查；本次只写一个电滑门、一个命令，不连发冲突命令")
                appendLine("blocked=$reason")
                appendLine("speedRaw=${speedRaw ?: "null"} speedKmh=${speed?.let { "%.2f".format(Locale.US, it) } ?: "null"}")
                appendLine("slideReadyRaw=${slideReadyRaw ?: "null"} decoded=${slideReady ?: "null"}")
                appendLine("chargeCoverRaw=${coverRaw ?: "null"} decoded=${cover ?: "null"}")
                appendLine("stateBefore=${beforeState ?: "null"} positionBefore=${beforePosition ?: "null"}")
                appendLine("elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            }
            WjxFileLogger.appendSection("电滑门控制", body)
            return AutoDoorControlResult(
                success = false,
                message = "电滑门控制已拦截：$reason，日志已写入 ${WjxFileLogger.logFilePath}",
            )
        }

        if (speed == null) return@withContext blockedResult("无法确认车速")
        if (abs(speed) >= target.maxSafeSpeedKmh) {
            return@withContext blockedResult("车速达到或超过官方安全阈值（${"%.2f".format(Locale.US, speed)}km/h >= ${target.maxSafeSpeedKmh}km/h）")
        }
        if (slideReady == null) return@withContext blockedResult("无法确认电滑门准备状态")
        if (slideReady != 1) return@withContext blockedResult("电滑门未就绪，官方要求 BODYWORK_ELEC_SLIDE_STATE=1")
        if (cover == null) return@withContext blockedResult("无法确认同侧充电口盖状态")
        if (cover != 1) return@withContext blockedResult("同侧充电口盖未关闭，官方会拦截")

        val writeStartedAt = SystemClock.elapsedRealtime()
        val writeStatus = runCatching { helper.writeStatus(DEV_BODYWORK, target.writeFid, commandValue) }
            .onFailure { WjxFileLogger.w(TAG, "write failed ${target.key}/$commandValue: ${it.message}", it) }
            .getOrNull()
        val accepted = writeStatus == 1
        if (accepted) {
            when (commandValue) {
                1 -> lastMotionBySide[target.key] = AutoDoorMotionDirection.OPENING
                2 -> lastMotionBySide[target.key] = AutoDoorMotionDirection.CLOSING
            }
        }
        val writeCost = SystemClock.elapsedRealtime() - writeStartedAt

        delay(600L)
        val afterState = readDecoded(DEV_BODYWORK, target.stateFid, 5)
        val afterPosition = readDecoded(DEV_BODYWORK, target.positionFid, 5)

        val body = buildString {
            append(buildWriteHeader(target, commandLabel, commandValue, true))
            appendLine("mode=WRITE_COMMAND")
            appendLine("safety=已通过设置2同款前置检查；只写一个电滑门、一个命令")
            appendLine("speedRaw=$speedRaw speedKmh=${"%.2f".format(Locale.US, speed)}")
            appendLine("slideReadyRaw=${slideReadyRaw ?: "null"} decoded=${slideReady ?: "null"}")
            appendLine("chargeCoverRaw=${coverRaw ?: "null"} decoded=${cover ?: "null"}")
            appendLine("write dev=$DEV_BODYWORK fid=${fidHex(target.writeFid)}(${target.writeFid}) value=$commandValue")
            appendLine("writeStatus=$writeStatus writeAccepted=$accepted writeCostMs=$writeCost")
            appendLine("stateFid=${fidHex(target.stateFid)} stateBefore=${beforeState ?: "null"} stateAfter=${afterState ?: "null"}")
            appendLine("positionFid=${fidHex(target.positionFid)} positionBefore=${beforePosition ?: "null"} positionAfter=${afterPosition ?: "null"}")
            appendLine("elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            appendLine("rawStatus=1 表示底层执行了实际动作；0 表示底层接受但本车型/当前状态可能 no-op；null/<0 表示失败。")
            appendLine("note=只把 status=1 视为成功，避免 no-op 被误提示为已成功。")
        }
        WjxFileLogger.appendSection("电滑门控制", body)
        AutoDoorControlResult(
            success = accepted,
            message = when (writeStatus) {
                1 -> "电滑门控制已执行：${target.label} $commandLabel，日志已写入 ${WjxFileLogger.logFilePath}"
                0 -> "电滑门控制未确认动作：${target.label} $commandLabel 底层返回 no-op，日志已写入 ${WjxFileLogger.logFilePath}"
                null -> "电滑门控制发送失败：${target.label} $commandLabel，helper 无返回，日志已写入 ${WjxFileLogger.logFilePath}"
                else -> "电滑门控制发送失败：${target.label} $commandLabel，status=$writeStatus，日志已写入 ${WjxFileLogger.logFilePath}"
            },
        )
    }

    suspend fun runBothDoors(commandValue: Int): AutoDoorControlResult =
        runDoors(listOf("slide_l", "slide_r"), commandValue, "双门")

    suspend fun readDoorStates(slotKeys: List<String> = listOf("slide_l", "slide_r")): List<AutoDoorSideSnapshot> = withContext(Dispatchers.IO) {
        val requestedKeys = slotKeys.distinct().filter { it in TARGETS }
        if (requestedKeys.isEmpty()) return@withContext emptyList()

        val helperReady = runCatching { helperBootstrap.ensureCurrent() }
            .onFailure { WjxFileLogger.w(TAG, "read door states bootstrap failed: ${it.message}", it) }
            .getOrDefault(false)

        if (!helperReady) {
            return@withContext requestedKeys.map { key ->
                TARGETS.getValue(key).toSnapshot(action = null, percent = null, statusOverride = "离线")
            }
        }

        requestedKeys.map { key ->
            val target = TARGETS.getValue(key)
            val action = readDecoded(DEV_BODYWORK, target.stateFid, 5)
            val percent = readDecoded(DEV_BODYWORK, target.positionFid, 5)
            target.toSnapshot(action = action, percent = percent)
        }
    }

    suspend fun runDoors(
        slotKeys: List<String>,
        commandValue: Int,
        groupLabel: String = "自动电滑门",
    ): AutoDoorControlResult {
        val commandLabel = COMMAND_LABELS[commandValue] ?: return AutoDoorControlResult(
            success = false,
            message = "自动电滑门控制失败：未知命令 $commandValue",
        )
        val requestedKeys = slotKeys.distinct().filter { it in TARGETS }
        if (requestedKeys.isEmpty()) {
            return AutoDoorControlResult(
                success = false,
                message = "自动电滑门$commandLabel 未发送成功：未选择有效车门",
            )
        }
        val startedAt = SystemClock.elapsedRealtime()
        WjxFileLogger.i(TAG, "start electric-slide-door group=$groupLabel keys=$requestedKeys command=$commandValue/$commandLabel")
        val results = mutableListOf<Pair<String, AutoDoorControlResult>>()
        requestedKeys.forEachIndexed { index, key ->
            if (index > 0) delay(120L)
            results += key to runDoorCommand(key, commandValue)
        }
        val success = results.any { it.second.success }
        val sideSummary = results.joinToString("，") { (key, result) ->
            sideResult(TARGETS[key]?.shortLabel ?: key, result)
        }
        val body = buildString {
            appendLine("time=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date())}")
            appendLine("mode=SELECTED_DOORS")
            appendLine("group=$groupLabel keys=$requestedKeys")
            appendLine("command=$commandValue/$commandLabel")
            results.forEach { (key, result) ->
                appendLine("$key success=${result.success} message=${result.message}")
            }
            appendLine("summary=$sideSummary")
            appendLine("elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            appendLine("note=按设置2官方链路尝试已选择的电滑门；不支持或前置条件不满足的一侧会被单独拦截。")
        }
        WjxFileLogger.appendSection("自动电滑门控制", body)
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
            nextCommandLabel = COMMAND_LABELS.getValue(nextCommand),
        )
    }

    private suspend fun readRaw(dev: Int, fid: Int, tx: Int): Long? =
        runCatching { helper.read(dev, fid, tx) }
            .onFailure { WjxFileLogger.w(TAG, "read failed dev=$dev fid=${fidHex(fid)} tx=$tx: ${it.message}", it) }
            .getOrNull()

    private suspend fun readDecoded(dev: Int, fid: Int, tx: Int): Int? =
        readRaw(dev, fid, tx)?.toInt()?.let(SentinelDecoder::decodeInt)

    private fun buildHeader(helperReady: Boolean): String = buildString {
        appendLine("time=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date())}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        appendLine("fingerprint=${Build.FINGERPRINT}")
        appendLine("helperReady=$helperReady")
    }

    private fun buildWriteHeader(
        target: AutoDoorTarget,
        commandLabel: String,
        commandValue: Int,
        helperReady: Boolean,
    ): String = buildString {
        append(buildHeader(helperReady))
        appendLine("target=${target.key}/${target.label}")
        appendLine("command=$commandValue/$commandLabel")
    }

    private data class AutoDoorTarget(
        val key: String,
        val label: String,
        val writeFid: Int,
        val stateFid: Int,
        val positionFid: Int,
        val coverFid: Int,
        val maxSafeSpeedKmh: Float,
    ) {
        val shortLabel: String
            get() = label.removeSuffix("电滑门")
    }

    private companion object {
        private const val TAG = "AutoDoorController"
        private const val DEV_BODYWORK = 1001
        private const val DEV_CHARGING = 1009
        private const val DEV_SPEED = 1013
        private const val FID_SPEED = -1807745016
        private val FID_ELEC_SLIDE_STATE = i("0x4B200012")
        private val FID_CHARGING_LEFT_COVER_STATE = i("0x40700028")
        private val FID_CHARGING_RIGHT_COVER_STATE = i("0x4070002A")

        private fun i(hex: String): Int = hex.removePrefix("0x").toLong(16).toInt()
        private fun fidHex(fid: Int): String = "0x%08X".format(Locale.US, fid)

        private val COMMAND_LABELS = mapOf(
            1 to "开门",
            2 to "关门",
            3 to "暂停",
        )

        private val TARGETS = mapOf(
            "slide_l" to AutoDoorTarget("slide_l", "左后电滑门", i("0x4E400030"), i("0x4B100016"), i("0x4B100018"), FID_CHARGING_LEFT_COVER_STATE, 3.0f),
            "slide_r" to AutoDoorTarget("slide_r", "右后电滑门", i("0x4E400032"), i("0x4B200016"), i("0x4B200018"), FID_CHARGING_RIGHT_COVER_STATE, 3.0f),
        )
    }
}
