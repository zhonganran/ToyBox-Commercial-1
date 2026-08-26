package com.byd.wjx.ui.settings

import com.byd.wjx.vehicle.CarProfileDetector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsVehicleProfileCoordinatorTest {
    @Test
    fun `profile loading exposes detected controller series`() = runTest {
        val coordinator = SettingsVehicleProfileCoordinator(
            CarProfileDetector(propertyReader = { property ->
                if (property == "apps.setting.product.outswver") "57控-v1" else ""
            }),
        )

        val profile = coordinator.loadProfile()

        assertEquals(57, profile.series)
    }
}
