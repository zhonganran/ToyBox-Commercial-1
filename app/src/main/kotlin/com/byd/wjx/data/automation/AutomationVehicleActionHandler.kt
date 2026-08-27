package com.byd.wjx.data.automation

import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.AutoDoorController
import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.log.WjxFileLogger

internal class AutomationVehicleActionHandler(
    private val vehicleApi: VehicleApi,
    private val autoDoorController: AutoDoorController,
    private val sentryModeController: SentryModeController,
    private val reverseMirrorController: ReverseMirrorController,
) {
    suspend fun dispatchParam(action: ActionDef, data: DiParsData?): DispatchResult {
        val blockReason = automationCommandBlockReason(action.command, data?.speed)
        if (blockReason != null) {
            WjxFileLogger.w(TAG, "Blocked '${action.command}': $blockReason")
            return DispatchResult(false, blockReason)
        }
        val result = vehicleApi.dispatch(action.command)
        val success = result.isSuccess
        val reason = if (success) null else result.exceptionOrNull()?.message ?: "执行失败"
        WjxFileLogger.i(
            TAG,
            "param dispatch command=${action.command} success=$success reason=${reason.orEmpty()}",
        )
        return DispatchResult(success, reason)
    }

    suspend fun controlAutoDoor(action: ActionDef): DispatchResult {
        val payload = parseAutomationActionPayload(action.payload)
            ?: return DispatchResult(false, "未设置自动滑门动作")
        val side = payload.optString("side").takeIf(String::isNotBlank)
            ?: return DispatchResult(false, "未设置自动滑门方向")
        val commandValue = payload.optInt("commandValue", -1)
        if (commandValue !in 1..3) {
            return DispatchResult(false, "自动滑门命令无效：$commandValue")
        }
        val sides = when (side) {
            "slide_l", "slide_r" -> listOf(side)
            "both" -> listOf("slide_l", "slide_r")
            else -> return DispatchResult(false, "自动滑门方向无效：$side")
        }
        val result = autoDoorController.runDoors(
            slotKeys = sides,
            commandValue = commandValue,
            groupLabel = action.displayName.ifBlank { "自动滑门" },
        )
        WjxFileLogger.i(
            TAG,
            "auto door action side=$side command=$commandValue " +
                "success=${result.success} reason=${result.message}",
        )
        return DispatchResult(result.success, if (result.success) null else result.message)
    }

    suspend fun setSentryMode(action: ActionDef): DispatchResult {
        val enabled = when (action.payload?.trim()?.lowercase()) {
            "on" -> true
            "off" -> false
            else -> return DispatchResult(false, "哨兵模式动作无效")
        }
        return sentryModeController.setEnabled(enabled)
    }

    suspend fun controlMirror(
        action: ActionDef,
        data: DiParsData?,
        preview: Boolean,
    ): DispatchResult = when (action.kind) {
        "mirror_tilt" -> reverseMirrorController.tilt(action.payload, data, preview)
        "mirror_restore" -> reverseMirrorController.restore(data)
        else -> DispatchResult(false, "未知后视镜动作")
    }

    companion object {
        private const val TAG = "ActionDispatcher"
    }
}
