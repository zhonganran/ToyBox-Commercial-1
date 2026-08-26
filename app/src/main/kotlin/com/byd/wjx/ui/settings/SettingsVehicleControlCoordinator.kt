package com.byd.wjx.ui.settings

import com.byd.wjx.data.vehicle.HeadlightControlResult
import com.byd.wjx.data.vehicle.HeadlightControlSnapshot
import com.byd.wjx.data.vehicle.HeadlightController
import com.byd.wjx.data.vehicle.HeadlightMode
import com.byd.wjx.vehicle.CarProfile
import com.byd.wjx.vehicle.CarProfileDetector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SettingsVehicleControlCoordinator @Inject constructor(
    private val headlightController: HeadlightController,
    private val carProfileDetector: CarProfileDetector,
) {
    suspend fun loadProfile(): CarProfile = withContext(Dispatchers.IO) {
        carProfileDetector.detect()
    }

    suspend fun refreshHeadlight(): HeadlightControlSnapshot =
        headlightController.refresh()

    suspend fun setHeadlightMode(mode: HeadlightMode): HeadlightControlResult =
        headlightController.setMode(mode)
}
