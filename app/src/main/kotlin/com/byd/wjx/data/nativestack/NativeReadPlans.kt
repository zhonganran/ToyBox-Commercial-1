package com.byd.wjx.data.nativestack

import com.byd.wjx.data.vehicle.VehicleReadSpec

internal data class NativeReadPlan(
    val entries: List<FidEntry>,
    val specs: List<VehicleReadSpec> = entries.map {
        VehicleReadSpec(it.transact, it.device, it.fid)
    },
)

internal data class NativeActionSelection(
    val fields: Set<String>,
    val plan: NativeReadPlan,
)

internal object NativeReadPlans {
    val full = NativeReadPlan(VehicleReadProfiles.regularFull)
    val coreFull = NativeReadPlan(VehicleReadProfiles.coreFull)
    val detailFull = NativeReadPlan(VehicleReadProfiles.detailFull)
    val ledMotion = NativeReadPlan(VehicleReadProfiles.ledMotion)
    val ledBrake = NativeReadPlan(VehicleReadProfiles.ledBrake)
    val ledBody = NativeReadPlan(VehicleReadProfiles.ledBody)
    val ledAdas = NativeReadPlan(VehicleReadProfiles.ledAdas)
    val tireTelemetry = NativeReadPlan(VehicleReadProfiles.tireTelemetry)
    val powertrainTelemetry = NativeReadPlan(
        VehicleReadProfiles.powertrainTelemetry + FidMap.entries.single { it.field == "gear" },
    )
}
