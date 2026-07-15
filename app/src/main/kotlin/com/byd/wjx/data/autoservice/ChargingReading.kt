package com.byd.wjx.data.autoservice

/**
 * One snapshot of charging-related autoservice fids.
 *
 * gunConnectState: 1=NONE, 2=AC, 3=DC, 4=AC_DC, 5=VTOL. Survives DiLink sleep
 * as long as the gun stays physically inserted. Resets to 1 after gun
 * removal.
 *
 * chargingType: handshake result. Resets to 1 (DEFAULT) after charging
 * finalizes — do not rely on this for catch-up classification, use
 * gunConnectState or the kwh/h heuristic.
 *
 * batteryType: 1=IRON/LFP (Leopard 3), 2=NCM. Informational.
 *
 * chargingCapacityKwh: per-session counter from BMS. Persists across DiLink
 * power-cycle; resets on new charging session (gun reconnect or BMS reset).
 * Use for cascade-B/C charge detection. May be null if autoservice is unavailable.
 *
 * bmsState: official BMS enum: 0=READY, 1=CHARGING, 2=FINISH, 3=DISCHARGE,
 * 4=TERMINATE, 5..8=FAULT, 9=SCHEDULE, 10=DISCHARGE_CBU, 11=TIMEOUT,
 * 12=DISCHARGE_FINISH, 13=PAUSE.
 *
 * The trailing electrical/diagnostic fields are optional live telemetry. The default catch-up
 * read leaves them null; the charging page requests them only while an AC/DC connector or charging
 * BMS state is active, so they never expand ordinary vehicle or background catch-up polling.
 */
data class ChargingReading(
    val gunConnectState: Int?,
    val chargingType: Int?,
    val chargeBatteryVoltV: Int?,
    val batteryType: Int?,
    val chargingCapacityKwh: Float?,
    val bmsState: Int?,
    val readAtMs: Long,
    val chargeCurrentA: Int? = null,
    val chargePowerKw: Float? = null,
    val batteryMaxChargePowerKw: Int? = null,
    val batteryMaxDischargePowerKw: Int? = null,
    val batteryInsulationKohm: Int? = null,
    val chargeStopSoc: Int? = null,
)
