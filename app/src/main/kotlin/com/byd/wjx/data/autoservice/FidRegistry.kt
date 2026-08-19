package com.byd.wjx.data.autoservice

/**
 * autoservice Binder addresses validated against on-device service.
 *
 * IMPORTANT: read-only constants. Write addresses belong in the reviewed
 * vehicle-action allowlist, not in this registry.
 */
object FidRegistry {

    // transact codes — only read-side codes are listed
    const val TX_GET_INT = 5
    const val TX_GET_FLOAT = 7

    // device types — verified by on-device probe
    const val DEV_STATISTIC = 1014   // BMS lifetime statistics
    const val DEV_CHARGING = 1009    // Charging gun + charger state
    const val DEV_BODYWORK = 1001    // 12V battery voltage on bodywork
    const val DEV_ENGINE = 1012      // Engine controller (live battery power)
    const val DEV_BATTERY_MGMT = 1039 // Battery safety / insulation controller

    // === Statistic fids (dev=1014) ===
    /** BMS State of Health, percent (transact 5=int, raw 0..100). */
    const val FID_SOH = 1145045032
    /** Lifetime energy throughput, kWh (transact 7=float). */
    const val FID_LIFETIME_KWH = 1032871984
    /** Lifetime average consumption, kWh/100km (transact 7=float). */
    const val FID_LIFETIME_AVG_PHM = 1246761008
    /** Lifetime mileage, km ×10 (transact 5=int, divide by 10). */
    const val FID_LIFETIME_MILEAGE = 1246765072
    /** Current SOC, percent (transact 7=float). */
    const val FID_SOC = 1246777400
    /** BMS maximum permitted charging power, kW (int); meaningful during charging. */
    const val FID_BATTERY_MAX_CHARGE_POWER = 877658136
    /** BMS maximum permitted discharge power, kW (int). */
    const val FID_BATTERY_MAX_DISCHARGE_POWER = 1145045048

    // === Charging fids (dev=1009) ===
    // Validated against live AC-charging probes. The discarded `-1442840xxx` group
    // (gun/type/battery_type) returned sentinel 0xffffd8e5 on every call —
    // phantom values the SentinelDecoder collapsed to null. The fids below
    // return real codes during charging.
    /** Charging gun connect state: 1=NONE, 2=AC, 3=DC, 4=AC_DC, 5=VTOL. */
    const val FID_GUN_CONNECT_STATE = 876609586
    /** Charging type after handshake: 1=DEFAULT, 2=AC, 3=VTOG, 4=GB_DC, 5=GB_NON_DC. */
    const val FID_CHARGING_TYPE = 876609592
    /** Charger HV battery voltage, V (int). CHARGING_CHARGE_BATTERY_VOLT. */
    const val FID_CHARGE_BATTERY_VOLT = 1145045000
    /** Charger current, A (int). Read only by the visible live-charging panel after active charge confirmation. */
    const val FID_CHARGE_CURRENT = 1145045016
    /** Live charging power, kW (float). Read only by the visible live-charging panel. */
    const val FID_CHARGE_POWER = 842006552
    /** Battery type: 0=LEAD_ACID, 1=IRON/LFP, 65535=INVALID. */
    const val FID_BATTERY_TYPE = -1728053169
    /** Per-session charged energy, kWh (transact 7=float). Persists across DiLink
     *  power-cycle; resets on new charging session (gun reconnect or BMS reset). */
    const val FID_CHARGING_CAPACITY = 666894360
    /** BMS charging state: 1=CHARGING, 2=FINISH, 13=PAUSE (other values observed
     *  but not used). transact 5 (int). */
    const val FID_CHARGING_BMS_STATE = 876609560
    /** Configured charging stop SOC, percent (int). */
    const val FID_CHARGE_STOP_SOC = 1132462120

    // === Battery safety fids (dev=1039) ===
    /** High-voltage battery insulation resistance, kΩ (int). */
    const val FID_BATTERY_INSULATION = 1134559256

    // === Bodywork fids (dev=1001) ===
    /** 12V auxiliary battery voltage, V (transact 7=float). */
    const val FID_OTA_BATTERY_POWER_VOLTAGE = 1128267816

    // === Engine fids (dev=1012) ===
    /**
     * Live battery power, signed int kW (transact 5). Positive = consumption,
     * negative = regen/charging. Raw int = kW directly (no scaling).
     * Validated on Leopard 3 2026-05-11: ENG_POW=1→1kW, ENG_POW=3→3kW.
     * Drive probe range -26..+101 matched dashboard observations.
     * See memory `reference_eng_pow_fid.md`.
     */
}
