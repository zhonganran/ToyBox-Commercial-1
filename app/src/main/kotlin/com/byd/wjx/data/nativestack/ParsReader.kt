package com.byd.wjx.data.nativestack

import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind

interface ParsReader {
    suspend fun fetchVehicleSnapshot(): VehicleSnapshot?

    suspend fun fetchActionVehicleSnapshot(): VehicleSnapshot? =
        fetchVehicleSnapshot()?.copy(kind = VehicleSnapshotKind.ACTION)

    suspend fun fetchLedMotionVehicleSnapshot(): VehicleSnapshot? = fetchActionVehicleSnapshot()

    suspend fun fetchLedBrakeVehicleSnapshot(): VehicleSnapshot? = fetchActionVehicleSnapshot()

    suspend fun fetchLedBodyVehicleSnapshot(): VehicleSnapshot? = fetchActionVehicleSnapshot()

    suspend fun fetchLedAdasVehicleSnapshot(): VehicleSnapshot? = fetchActionVehicleSnapshot()
}
