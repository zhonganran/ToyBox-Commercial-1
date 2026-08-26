package com.byd.wjx.data.vehicle

import android.os.Build
import android.os.SystemClock
import com.byd.wjx.log.WjxFileLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

internal class AutoDoorDiagnostics @Inject constructor() {
    fun commandStarted(target: AutoDoorTarget, commandValue: Int) {
        WjxFileLogger.i(TAG, "start command target=${target.key} command=$commandValue")
    }

    fun helperUnavailable(target: AutoDoorTarget, commandLabel: String, commandValue: Int) {
        val body = buildWriteHeader(target, commandLabel, commandValue, helperReady = false) +
            "\nblocked=helper unavailable\n"
        WjxFileLogger.appendSection("电滑门控制", body)
    }

    fun commandBlocked(
        target: AutoDoorTarget,
        commandLabel: String,
        commandValue: Int,
        readings: AutoDoorCommandReadings,
        reason: String,
        startedAt: Long,
    ) {
        val body = buildString {
            append(buildWriteHeader(target, commandLabel, commandValue, helperReady = true))
            appendLine("mode=WRITE_COMMAND")
            appendLine("safety=写入前执行设置2同款前置检查；本次只写一个电滑门、一个命令，不连发冲突命令")
            appendLine("blocked=$reason")
            appendLine("speedRaw=${readings.speedRaw ?: "null"} speedKmh=${readings.speedKmh?.let { "%.2f".format(Locale.US, it) } ?: "null"}")
            appendLine("slideReadyRaw=${readings.slideReadyRaw ?: "null"} decoded=${readings.slideReady ?: "null"}")
            appendLine("chargeCoverRaw=${readings.coverRaw ?: "null"} decoded=${readings.coverState ?: "null"}")
            appendLine("stateBefore=${readings.beforeState ?: "null"} positionBefore=${readings.beforePosition ?: "null"}")
            appendLine("elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
        }
        WjxFileLogger.appendSection("电滑门控制", body)
    }

    fun commandCompleted(
        target: AutoDoorTarget,
        commandLabel: String,
        commandValue: Int,
        readings: AutoDoorCommandReadings,
        write: AutoDoorWriteResult,
        after: AutoDoorStateReadings,
        startedAt: Long,
    ) {
        val accepted = write.status == 1
        val body = buildString {
            append(buildWriteHeader(target, commandLabel, commandValue, helperReady = true))
            appendLine("mode=WRITE_COMMAND")
            appendLine("safety=已通过设置2同款前置检查；只写一个电滑门、一个命令")
            appendLine("speedRaw=${readings.speedRaw} speedKmh=${readings.speedKmh?.let { "%.2f".format(Locale.US, it) } ?: "null"}")
            appendLine("slideReadyRaw=${readings.slideReadyRaw ?: "null"} decoded=${readings.slideReady ?: "null"}")
            appendLine("chargeCoverRaw=${readings.coverRaw ?: "null"} decoded=${readings.coverState ?: "null"}")
            appendLine("write dev=${AutoDoorCatalog.DEV_BODYWORK} fid=${AutoDoorCatalog.fidHex(target.writeFid)}(${target.writeFid}) value=$commandValue")
            appendLine("writeStatus=${write.status} writeAccepted=$accepted writeCostMs=${write.elapsedMs}")
            appendLine("stateFid=${AutoDoorCatalog.fidHex(target.stateFid)} stateBefore=${readings.beforeState ?: "null"} stateAfter=${after.action ?: "null"}")
            appendLine("positionFid=${AutoDoorCatalog.fidHex(target.positionFid)} positionBefore=${readings.beforePosition ?: "null"} positionAfter=${after.percent ?: "null"}")
            appendLine("elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            appendLine("rawStatus=1 表示底层执行了实际动作；0 表示底层接受但本车型/当前状态可能 no-op；null/<0 表示失败。")
            appendLine("note=只把 status=1 视为成功，避免 no-op 被误提示为已成功。")
        }
        WjxFileLogger.appendSection("电滑门控制", body)
    }

    fun groupStarted(
        groupLabel: String,
        requestedKeys: List<String>,
        commandValue: Int,
        commandLabel: String,
    ) {
        WjxFileLogger.i(
            TAG,
            "start electric-slide-door group=$groupLabel keys=$requestedKeys command=$commandValue/$commandLabel",
        )
    }

    fun groupCompleted(
        groupLabel: String,
        requestedKeys: List<String>,
        commandValue: Int,
        commandLabel: String,
        results: List<Pair<String, AutoDoorControlResult>>,
        summary: String,
        startedAt: Long,
    ) {
        val body = buildString {
            appendLine("time=${timestamp()}")
            appendLine("mode=SELECTED_DOORS")
            appendLine("group=$groupLabel keys=$requestedKeys")
            appendLine("command=$commandValue/$commandLabel")
            results.forEach { (key, result) ->
                appendLine("$key success=${result.success} message=${result.message}")
            }
            appendLine("summary=$summary")
            appendLine("elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            appendLine("note=按设置2官方链路尝试已选择的电滑门；不支持或前置条件不满足的一侧会被单独拦截。")
        }
        WjxFileLogger.appendSection("自动电滑门控制", body)
    }

    private fun buildHeader(helperReady: Boolean): String = buildString {
        appendLine("time=${timestamp()}")
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

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date())

    private companion object {
        const val TAG = "AutoDoorDiagnostics"
    }
}
