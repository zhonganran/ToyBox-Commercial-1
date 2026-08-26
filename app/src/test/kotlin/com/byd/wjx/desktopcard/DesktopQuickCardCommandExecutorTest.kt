package com.byd.wjx.desktopcard

import com.byd.wjx.data.automation.ActionDispatcher
import com.byd.wjx.data.automation.DispatchResult
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.AutoDoorControlResult
import com.byd.wjx.data.vehicle.AutoDoorController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopQuickCardCommandExecutorTest {

    @Test
    fun `automation command is delegated with desktop card owner`() = runTest {
        val dispatcher = mockk<ActionDispatcher>()
        val autoDoor = mockk<AutoDoorController>()
        val vehicleState = mockk<VehicleStateRepository>()
        every { vehicleState.fullSnapshot } returns MutableStateFlow(null)
        coEvery { dispatcher.dispatch(any(), null, "desktop_quick_card") } returns DispatchResult(true)

        val result = DesktopQuickCardCommandExecutor(dispatcher, autoDoor, vehicleState).execute(
            item = DesktopQuickCardCommand(
                command = "lock",
                label = "锁车",
                category = "车辆",
                payload = "锁车",
            ),
            owner = "desktop_quick_card",
        )

        assertTrue(result.success)
        coVerify(exactly = 1) { dispatcher.dispatch(any(), null, "desktop_quick_card") }
    }

    @Test
    fun `auto door command is delegated to guarded door controller`() = runTest {
        val dispatcher = mockk<ActionDispatcher>()
        val autoDoor = mockk<AutoDoorController>()
        val vehicleState = mockk<VehicleStateRepository>()
        every { vehicleState.fullSnapshot } returns MutableStateFlow(null)
        coEvery { autoDoor.toggleDoor("slide_l") } returns AutoDoorControlResult(true, "ok")

        val result = DesktopQuickCardCommandExecutor(dispatcher, autoDoor, vehicleState).execute(
            item = DesktopQuickCardCommand(
                command = DESKTOP_QUICK_CARD_LEFT_SLIDING_DOOR,
                label = "左侧滑门",
                category = "电滑门",
                kind = DesktopQuickCardCommandKind.AUTO_DOOR,
                payload = "slide_l",
            ),
            owner = "desktop_quick_card",
        )

        assertTrue(result.success)
        assertEquals("ok", result.message)
        coVerify(exactly = 1) { autoDoor.toggleDoor("slide_l") }
    }

    @Test
    fun `feature commands stay outside generic executor`() = runTest {
        val dispatcher = mockk<ActionDispatcher>()
        val autoDoor = mockk<AutoDoorController>()
        val vehicleState = mockk<VehicleStateRepository>()
        every { vehicleState.fullSnapshot } returns MutableStateFlow(null)

        val result = DesktopQuickCardCommandExecutor(dispatcher, autoDoor, vehicleState).execute(
            item = DesktopQuickCardCommandCatalog.byCommand.getValue(DESKTOP_QUICK_CARD_REALTIME_HAILING),
            owner = "desktop_quick_card",
        )

        assertFalse(result.success)
        assertEquals("该悬浮卡片功能由专用控制器处理", result.message)
    }
}
