package com.byd.wjx.desktopcard

import com.byd.wjx.data.automation.ActionDispatcher
import com.byd.wjx.data.local.entity.ActionDef
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.AutoDoorController
import javax.inject.Inject

/** Executes vehicle actions selected from the desktop card without knowing about overlay UI. */
class DesktopQuickCardCommandExecutor @Inject constructor(
    private val actionDispatcher: ActionDispatcher,
    private val autoDoorController: AutoDoorController,
    private val vehicleStateRepository: VehicleStateRepository,
) {
    suspend fun execute(item: DesktopQuickCardCommand, owner: String): DesktopQuickCardCommandResult =
        when (item.kind) {
            DesktopQuickCardCommandKind.AUTOMATION -> {
                val data = vehicleStateRepository.fullSnapshot.value?.data
                val result = actionDispatcher.dispatch(
                    action = ActionDef(command = item.payload, displayName = item.label),
                    data = data,
                    owner = owner,
                )
                DesktopQuickCardCommandResult(result.success, result.reason)
            }

            DesktopQuickCardCommandKind.AUTO_DOOR -> {
                val result = autoDoorController.toggleDoor(item.payload)
                DesktopQuickCardCommandResult(result.success, result.message)
            }

            else -> DesktopQuickCardCommandResult(
                success = false,
                message = "该悬浮卡片功能由专用控制器处理",
            )
        }
}

data class DesktopQuickCardCommandResult(
    val success: Boolean,
    val message: String?,
)
