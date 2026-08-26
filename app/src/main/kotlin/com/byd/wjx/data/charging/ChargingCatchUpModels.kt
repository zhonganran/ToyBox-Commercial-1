package com.byd.wjx.data.charging

enum class DetectorState { IDLE, EVALUATING, ERROR }

enum class CatchUpOutcome {
    AUTOSERVICE_UNAVAILABLE,
    SENTINEL,
    BASELINE_INITIALIZED,
    NO_DELTA,
    STILL_CHARGING,
    SESSION_CREATED,
}

data class CatchUpResult(
    val outcome: CatchUpOutcome,
    val chargeId: Long? = null,
    val deltaKwh: Double? = null,
)
