package com.byd.wjx.data.vehicle

/**
 * Classification of an autoservice setInt result.
 *
 * HelperDaemon forwards the raw write status:
 * - status >= 1: the actuator accepted a real action.
 * - status == 0: accepted but no physical action on this trim.
 * - status == -10011: permanently denied / unsupported channel.
 * - null or other negative values: transient failure, do not learn a channel.
 */
enum class WriteOutcome {
    REAL,
    NOOP,
    PERMANENT_DENIED,
    TRANSIENT;

    companion object {
        fun fromStatus(status: Int?): WriteOutcome = when {
            status == null -> TRANSIENT
            status >= 1 -> REAL
            status == 0 -> NOOP
            status == -10011 -> PERMANENT_DENIED
            else -> TRANSIENT
        }
    }
}
