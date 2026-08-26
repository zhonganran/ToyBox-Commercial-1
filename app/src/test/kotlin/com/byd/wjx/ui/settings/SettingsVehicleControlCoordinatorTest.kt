package com.byd.wjx.ui.settings

import com.byd.wjx.data.vehicle.HeadlightAvailability
import com.byd.wjx.data.vehicle.HeadlightControlResult
import com.byd.wjx.data.vehicle.HeadlightControlSnapshot
import com.byd.wjx.data.vehicle.HeadlightController
import com.byd.wjx.data.vehicle.HeadlightMode
import com.byd.wjx.vehicle.CarProfileDetector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsVehicleControlCoordinatorTest {
    @Test
    fun `profile loading exposes detected controller series`() = runTest {
        val coordinator = coordinator(
            detector = CarProfileDetector(propertyReader = { property ->
                if (property == "apps.setting.product.outswver") "57控-v1" else ""
            }),
        )

        val profile = coordinator.loadProfile()

        assertEquals(57, profile.series)
    }

    @Test
    fun `headlight refresh returns vehicle snapshot unchanged`() = runTest {
        val controller = mockk<HeadlightController>()
        val snapshot = HeadlightControlSnapshot(
            availability = HeadlightAvailability.SUPPORTED,
            mode = HeadlightMode.AUTO,
            message = "当前模式：自动",
        )
        coEvery { controller.refresh() } returns snapshot
        val coordinator = coordinator(controller = controller)

        assertEquals(snapshot, coordinator.refreshHeadlight())
        coVerify(exactly = 1) { controller.refresh() }
    }

    @Test
    fun `headlight setting preserves vehicle readback instead of requested mode`() = runTest {
        val controller = mockk<HeadlightController>()
        val result = HeadlightControlResult(
            success = false,
            snapshot = HeadlightControlSnapshot(
                availability = HeadlightAvailability.SUPPORTED,
                mode = HeadlightMode.AUTO,
                message = "车辆未切换到远光，当前仍为自动",
            ),
        )
        coEvery { controller.setMode(HeadlightMode.HIGH_BEAM) } returns result
        val coordinator = coordinator(controller = controller)

        val actual = coordinator.setHeadlightMode(HeadlightMode.HIGH_BEAM)

        assertFalse(actual.success)
        assertEquals(HeadlightMode.AUTO, actual.snapshot.mode)
        coVerify(exactly = 1) { controller.setMode(HeadlightMode.HIGH_BEAM) }
    }

    private fun coordinator(
        controller: HeadlightController = mockk(relaxed = true),
        detector: CarProfileDetector = CarProfileDetector(propertyReader = { "34" }),
    ) = SettingsVehicleControlCoordinator(controller, detector)
}
