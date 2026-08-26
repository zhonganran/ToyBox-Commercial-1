package com.byd.wjx.ui.settings

import com.byd.wjx.vehicle.CarProfile
import com.byd.wjx.vehicle.CarProfileDetector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SettingsVehicleProfileCoordinator @Inject constructor(
    private val carProfileDetector: CarProfileDetector,
) {
    suspend fun loadProfile(): CarProfile = withContext(Dispatchers.IO) {
        carProfileDetector.detect()
    }
}
