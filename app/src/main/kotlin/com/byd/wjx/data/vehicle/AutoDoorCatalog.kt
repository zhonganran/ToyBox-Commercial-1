package com.byd.wjx.data.vehicle

import java.util.Locale

internal object AutoDoorCatalog {
    const val DEV_BODYWORK = 1001
    const val DEV_CHARGING = 1009
    const val DEV_SPEED = 1013
    const val FID_SPEED = -1807745016
    val FID_ELEC_SLIDE_STATE: Int = intFid("0x4B200012")

    val commandLabels: Map<Int, String> = mapOf(
        1 to "开门",
        2 to "关门",
        3 to "暂停",
    )

    val targets: Map<String, AutoDoorTarget> = mapOf(
        "slide_l" to AutoDoorTarget(
            key = "slide_l",
            label = "左后电滑门",
            writeFid = intFid("0x4E400030"),
            stateFid = intFid("0x4B100016"),
            positionFid = intFid("0x4B100018"),
            coverFid = intFid("0x40700028"),
            maxSafeSpeedKmh = 3.0f,
        ),
        "slide_r" to AutoDoorTarget(
            key = "slide_r",
            label = "右后电滑门",
            writeFid = intFid("0x4E400032"),
            stateFid = intFid("0x4B200016"),
            positionFid = intFid("0x4B200018"),
            coverFid = intFid("0x4070002A"),
            maxSafeSpeedKmh = 3.0f,
        ),
    )

    fun fidHex(fid: Int): String = "0x%08X".format(Locale.US, fid)

    private fun intFid(hex: String): Int = hex.removePrefix("0x").toLong(16).toInt()
}
