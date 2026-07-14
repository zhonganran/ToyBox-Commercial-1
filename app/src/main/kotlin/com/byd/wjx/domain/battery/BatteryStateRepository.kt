package com.byd.wjx.domain.battery

import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.data.repository.BatteryHealthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregated battery view used by Dashboard (TopBar warning, BatteryCard,
 * BatteryHealthScreen header).
 *
 * This remains an on-demand health refresh. Fast live vehicle state is supplied
 * separately by VehicleStateRepository.
 */
data class BatteryState(
    val socNow: Float?,
    val voltage12v: Float?,
    val sohPercent: Float?,
    val lifetimeKm: Float?,
    val lifetimeKwh: Float?,
    /** True when the autoservice client returned at least one real value. */
    val autoserviceAvailable: Boolean
)

@Singleton
class BatteryStateRepository @Inject constructor(
    private val vehicleApi: VehicleApi,
    private val batteryHealth: BatteryHealthRepository,
) {
    suspend fun refresh(): BatteryState {
        val r = vehicleApi.readBatterySnapshot()
            ?: return BatteryState(null, null, null, null, null, autoserviceAvailable = false)
        val hasLiveValue = r.socPercent != null ||
            r.voltage12v != null ||
            r.sohPercent != null ||
            r.lifetimeMileageKm != null ||
            r.lifetimeKwh != null
        if (!hasLiveValue) {
            return BatteryState(null, null, null, null, null, autoserviceAvailable = false)
        }

        val sohFromSnapshot = batteryHealth.getLast()?.sohPercent?.toFloat()
        return BatteryState(
            socNow = r.socPercent,
            voltage12v = r.voltage12v,
            sohPercent = r.sohPercent ?: sohFromSnapshot,
            lifetimeKm = r.lifetimeMileageKm,
            lifetimeKwh = r.lifetimeKwh,
            autoserviceAvailable = true
        )
    }
}
