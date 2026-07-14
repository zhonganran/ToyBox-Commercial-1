package com.byd.wjx.data.loop

import com.byd.wjx.data.autoservice.ChargeGunState

enum class LoopState { DRIVE, CHARGE, PARKED, IDLE }

data class CadenceConfig(
    val driveMs: Long,
    val chargeMs: Long,
    val parkedMs: Long,
    val idleMs: Long,
    val actionMs: Long,
) {
    fun intervalFor(state: LoopState): Long = when (state) {
        LoopState.DRIVE -> driveMs
        LoopState.CHARGE -> chargeMs
        LoopState.PARKED -> parkedMs
        LoopState.IDLE -> idleMs
    }

    companion object {
        const val MAX_POLL_INTERVAL_MS = 60_000L
        fun default() = CadenceConfig(
            driveMs = 1_000L,
            chargeMs = 3_000L,
            parkedMs = 3_000L,
            idleMs = 30_000L,
            actionMs = 300L,
        )
    }
}

object LoopFsm {
    // powerState (DiParsData.kt:21): 0=OFF, 1=ON, 2=DRIVE. gear (DiParsData.kt:20): 1=P, 4=D.
    // speed (DiParsData.kt:5): Int? km/h.
    fun classify(data: com.byd.wjx.data.remote.DiParsData): LoopState {
        if (ChargeGunState.isConnected(data.chargeGunState)) return LoopState.CHARGE
        if (data.powerState == null && data.gear == 1) return LoopState.PARKED
        val ignitionActive = when {
            data.powerState != null -> data.powerState in 1..2
            else -> data.gear == 4 || ((data.speed ?: 0) > 0)
        }
        if (!ignitionActive) return LoopState.IDLE
        val moving = (data.speed ?: 0) > 0
        return if (moving) LoopState.DRIVE else LoopState.PARKED
    }
}
