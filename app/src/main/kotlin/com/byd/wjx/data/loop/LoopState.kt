package com.byd.wjx.data.loop

import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.nativestack.BydSignalSemantics

enum class LoopState { DRIVE, CHARGE, PARKED, IDLE }

data class CadenceConfig(
    val driveMs: Long,
    val chargeMs: Long,
    val parkedMs: Long,
    val idleMs: Long,
    val actionMs: Long,
    val chargeActionMs: Long,
    val idleActionMs: Long,
) {
    fun intervalFor(state: LoopState): Long = when (state) {
        LoopState.DRIVE -> driveMs
        LoopState.CHARGE -> chargeMs
        LoopState.PARKED -> parkedMs
        LoopState.IDLE -> idleMs
    }

    /** Fast actions stay responsive while moving/parking; sleeping/charging polls are cheaper. */
    fun actionIntervalFor(state: LoopState): Long = when (state) {
        LoopState.DRIVE, LoopState.PARKED -> actionMs
        LoopState.CHARGE -> chargeActionMs
        LoopState.IDLE -> idleActionMs
    }

    companion object {
        const val MAX_POLL_INTERVAL_MS = 60_000L
        fun default() = CadenceConfig(
            driveMs = 1_000L,
            chargeMs = 3_000L,
            parkedMs = 3_000L,
            idleMs = 30_000L,
            actionMs = 300L,
            chargeActionMs = 1_000L,
            idleActionMs = 1_000L,
        )
    }
}

object LoopFsm {
    // powerState: BYD BODYWORK_POWER_LEVEL 0=OFF, 1=ACC, 2=ON, 3=OK, 4=FAKE_OK.
    // gear: 1=P, 4=D. speed: Int? km/h.
    fun classify(data: com.byd.wjx.data.remote.DiParsData): LoopState {
        if (ChargeGunState.isConnected(data.chargeGunState)) return LoopState.CHARGE
        if (data.powerState == null && data.gear == 1) return LoopState.PARKED
        val ignitionActive = when {
            data.powerState != null -> BydSignalSemantics.isPowered(data.powerState)
            else -> data.gear == 4 || ((data.speed ?: 0) > 0)
        }
        if (!ignitionActive) return LoopState.IDLE
        val moving = (data.speed ?: 0) > 0
        return if (moving) LoopState.DRIVE else LoopState.PARKED
    }
}
