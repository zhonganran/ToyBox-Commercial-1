package com.byd.wjx.data.nativestack

import com.byd.wjx.data.remote.DiParsData
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind

interface ParsReader {
    suspend fun fetch(): DiParsData?
    suspend fun fetchAction(): DiParsData? = fetch()

    suspend fun fetchVehicleSnapshot(): VehicleSnapshot? =
        fetch()?.let { VehicleSnapshot.legacy(it, VehicleSnapshotKind.FULL) }

    suspend fun fetchActionVehicleSnapshot(): VehicleSnapshot? =
        fetchAction()?.let { VehicleSnapshot.legacy(it, VehicleSnapshotKind.ACTION) }

    suspend fun fetchLedMotionVehicleSnapshot(): VehicleSnapshot? = fetchActionVehicleSnapshot()

    suspend fun fetchLedBrakeVehicleSnapshot(): VehicleSnapshot? = fetchActionVehicleSnapshot()

    suspend fun fetchLedBodyVehicleSnapshot(): VehicleSnapshot? = fetchActionVehicleSnapshot()

    suspend fun fetchLedAdasVehicleSnapshot(): VehicleSnapshot? = fetchActionVehicleSnapshot()
}
