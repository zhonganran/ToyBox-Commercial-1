package com.byd.wjx.clustercard

import com.byd.wjx.data.autoservice.FidRegistry
import com.byd.wjx.data.autoservice.SentinelDecoder
import com.byd.wjx.data.vehicle.VehicleGateway
import com.byd.wjx.data.vehicle.VehicleReadSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the live day/night state used by BYD's original display stack. */
@Singleton
class ClusterCardDayNightSignalReader @Inject constructor(
    private val gateway: VehicleGateway,
) {
    suspend fun readRawMode(): Int? = gateway.readRaw(READ_SPEC)
        ?.value
        ?.let(SentinelDecoder::decodeInt)

    internal companion object {
        const val LIGHTS_DEVICE = 1004
        const val LIGHT_DAY_NIGHT_MODE_FID = 0x3A700042
        val READ_SPEC = VehicleReadSpec(
            transaction = FidRegistry.TX_GET_INT,
            device = LIGHTS_DEVICE,
            fid = LIGHT_DAY_NIGHT_MODE_FID,
        )
    }
}
