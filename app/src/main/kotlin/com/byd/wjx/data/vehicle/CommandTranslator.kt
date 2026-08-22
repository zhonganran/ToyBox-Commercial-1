package com.byd.wjx.data.vehicle

import com.byd.wjx.domain.vehicle.VehicleCommandSyntax
import com.byd.wjx.domain.vehicle.VehicleCommandSyntax.NumericCommand
import com.byd.wjx.domain.vehicle.VehicleCommandSyntax.WindowTarget

/**
 * Maps user-facing D+ Chinese command strings to (action_name, value) for VehicleApi.dispatch.
 *
 * Source: AutomationViewModel.ACTION_COMMANDS (49 entries) + Smart Home VPS catalog
 * (same vocabulary). Both callers historically stripped the "迪加" prefix before sending
 * over HTTP; the prefix is now removed here if present.
 *
 * Crowd validation strategy: actions not present here OR not in WriteAllowlist will
 * fail-soft at dispatch(). User files issue → we add the mapping in a follow-up.
 *
 * Window aggregates (车窗全开/关闭/半开, 前排/后排车窗全开/关闭) and individual rear
 * windows (后左/后右打开{n}) fan out to the validated per-door % fids — see the
 * [composite] map. The older aggregate fids all target the driver short-form
 * fid (1125122104) and the rear short-form fids are no-ops on Leopard 3, so
 * the % path is the only reliable channel.
 *
 * Interior/cabin light (打开/关闭车内灯), ambient light (氛围灯打开/关闭), DRL
 * (打开/关闭日行灯) and mirror heat = rear-window defrost (后视镜加热/关闭后视镜加热)
 * route to LIVE_VALIDATED entries on dev=1023/1004/1000 — the channel D+ used via
 * BYDAutoSettingDevice. dev=1023/1004 are carved out per-fid in WriteAllowlist.
 * All four groups validated on Leopard 3 2026-05-29 (write+readback snap).
 *
 * DROPPED: commands whose action_name either has no allowlist entry or targets
 * a banned dev namespace —
     *   ECO模式                                — drive mode write targets dev=1006 (BANNED).
     *                                            Default "low SOC ECO" rule is
 *                                            shipped disabled; if user enables it the
 *                                            dispatch fails-soft via AllowlistMiss.
 *
 * Values for base vehicle entries are kept in app/src/main/assets/vehicle-actions.json.
 * See value comments below.
 */
object CommandTranslator {
    data class Resolved(val actionName: String, val value: Int)

    private val table: Map<String, Resolved> = mapOf(
        // ── Windows (vent) ── windows_vent (driver-only fid). Aggregate
        // open/close/half are handled by the composite fan-out below ──────────
        "车窗通风"   to Resolved("windows_vent",  5),   // val=5 (vent mode)

        // ── Windows (individual %, 0..100) ── LIVE_VALIDATED ──────────────────
        "主驾打开100" to Resolved("window_driver_pos",      100),
        "主驾打开0"   to Resolved("window_driver_pos",        0),
        "副驾打开100" to Resolved("window_passenger_pos",   100),
        "副驾打开0"   to Resolved("window_passenger_pos",     0),
        "后左打开100" to Resolved("window_rear_left_pos",   100),
        "后左打开0"   to Resolved("window_rear_left_pos",     0),
        "后右打开100" to Resolved("window_rear_right_pos",  100),
        "后右打开0"   to Resolved("window_rear_right_pos",    0),

        // ── Climate ── LIVE_VALIDATED (ac_on/ac_off/ac_cycle_*) ──────────────
        // 自动空调 = ac_on (ac_ctrl_mode AUTO = 0). 设置温度<N> resolves
        // dynamically over 16..30 in resolve(), so there are no per-temperature
        // entries here (the old 18/20/22/25-only table missed every other value).
        "打开空调"    to Resolved("ac_power",      1),
        "关闭空调"    to Resolved("ac_off",        0),
        "自动空调"    to Resolved("ac_on",         0),   // val=0 (ctrl_mode AUTO)
        "内循环"      to Resolved("ac_cycle_inner", 1),  // LIVE val=1
        "外循环"      to Resolved("ac_cycle_outer", 2),  // LIVE val=2

        // ── Climate ── vehicle-actions.json ──────────────────────────────────
        "打开空调通风" to Resolved("ac_flow_only_on",   1),  // val=1
        "关闭空调通风" to Resolved("ac_flow_only_off",  0),
        "吹前挡"      to Resolved("defrost_front_on",  1),  // val=1
        "关闭吹前挡"  to Resolved("defrost_front_off", 0),  // val=0
        "主副驾温度同步" to Resolved("ac_dual", 0),
        "主副驾温度分区" to Resolved("ac_dual", 1),
        "出风吹脸"         to Resolved("ac_wind_mode", 1),
        "出风吹脸吹脚"     to Resolved("ac_wind_mode", 2),
        "出风吹脚"         to Resolved("ac_wind_mode", 3),
        "出风吹脚除霜"     to Resolved("ac_wind_mode", 4),
        "出风除霜"         to Resolved("ac_wind_mode", 5),
        "出风吹脸吹脚除霜" to Resolved("ac_wind_mode", 6),
        "出风吹脸除霜"     to Resolved("ac_wind_mode", 7),

        // ── Driver seat heat ── vehicle-actions.json (dev=1001, safe) ─────────
        // Naming: off=1, on(lvl1)=2, lvl2=3, lvl3=4, lvl4=5, lvl5=6
        "主驾座椅加热1档"  to Resolved("driver_seat_heat_on",   2),
        "主驾座椅加热2档"  to Resolved("driver_seat_heat_lvl2", 3),
        "主驾座椅加热3档"  to Resolved("driver_seat_heat_lvl3", 4),
        "主驾座椅加热4档"  to Resolved("driver_seat_heat_lvl4", 5),
        "主驾座椅加热5档"  to Resolved("driver_seat_heat_lvl5", 6),
        "主驾座椅加热关闭" to Resolved("driver_seat_heat_off",  1),

        // ── Passenger seat heat ── vehicle-actions.json ───────────────────────
        "副驾座椅加热1档"  to Resolved("passenger_seat_heat_on",   2),
        "副驾座椅加热2档"  to Resolved("passenger_seat_heat_lvl2", 3),
        "副驾座椅加热3档"  to Resolved("passenger_seat_heat_lvl3", 4),
        "副驾座椅加热4档"  to Resolved("passenger_seat_heat_lvl4", 5),
        "副驾座椅加热5档"  to Resolved("passenger_seat_heat_lvl5", 6),
        "副驾座椅加热关闭" to Resolved("passenger_seat_heat_off",  1),

        // ── Driver seat vent ── vehicle-actions.json ──────────────────────────
        "主驾座椅通风1档"  to Resolved("driver_seat_vent_on",   2),
        "主驾座椅通风2档"  to Resolved("driver_seat_vent_lvl2", 3),
        "主驾座椅通风3档"  to Resolved("driver_seat_vent_lvl3", 4),
        "主驾座椅通风4档"  to Resolved("driver_seat_vent_lvl4", 5),
        "主驾座椅通风5档"  to Resolved("driver_seat_vent_lvl5", 6),
        "主驾座椅通风关闭" to Resolved("driver_seat_vent_off",  1),

        // ── Passenger seat vent ── vehicle-actions.json ───────────────────────
        "副驾座椅通风1档"  to Resolved("passenger_seat_vent_on",   2),
        "副驾座椅通风2档"  to Resolved("passenger_seat_vent_lvl2", 3),
        "副驾座椅通风3档"  to Resolved("passenger_seat_vent_lvl3", 4),
        "副驾座椅通风4档"  to Resolved("passenger_seat_vent_lvl4", 5),
        "副驾座椅通风5档"  to Resolved("passenger_seat_vent_lvl5", 6),
        "副驾座椅通风关闭" to Resolved("passenger_seat_vent_off",  1),

        // ── Optional comfort equipment; official Settings values ─────────────
        "方向盘加热" to Resolved("steering_heat_on", 2),
        "关闭方向盘加热" to Resolved("steering_heat_off", 1),
        "主驾迎宾开启" to Resolved("driver_seat_welcome_on", 1),
        "主驾迎宾关闭" to Resolved("driver_seat_welcome_off", 2),
        "副驾迎宾开启" to Resolved("passenger_seat_welcome_on", 1),
        "副驾迎宾关闭" to Resolved("passenger_seat_welcome_off", 2),
        "主驾按摩关闭" to Resolved("driver_massage_off", 1),
        "副驾按摩关闭" to Resolved("passenger_massage_off", 1),

        // ── Locks ── LIVE_VALIDATED ───────────────────────────────────────────
        "车门上锁"  to Resolved("doors_lock",   2),
        "车门解锁"  to Resolved("doors_unlock", 1),

        // ── Trunk ── vehicle-actions.json (dev=1001) ─────────────────────────
        "开后备箱"  to Resolved("open_trunk",  1),  // val=1
        "关后备箱"  to Resolved("close_trunk", 3),  // val=3

        // ── Sunroof ── LIVE_VALIDATED ─────────────────────────────────────────
        "天窗打开100" to Resolved("sunroof_open",  1),  // full open
        "天窗打开50"  to Resolved("sunroof_tilt",  3),  // tilt/half — LIVE val=3
        "天窗打开0"   to Resolved("sunroof_close", 2),
        "天窗停止"    to Resolved("sunroof_stop", 4),
        "天窗通风"    to Resolved("sunroof_updip", 5),
        "天窗舒适位置" to Resolved("sunroof_comfort", 6),

        // ── Sunshade ── LIVE_VALIDATED ────────────────────────────────────────
        "遮阳帘打开" to Resolved("sunshade_open",  1),
        "遮阳帘关闭" to Resolved("sunshade_close", 2),

        // ── Interior / ambient light ── LIVE_VALIDATED (dev=1023 carve-out) ──────
        "打开车内灯" to Resolved("interior_light_on",  2),
        "关闭车内灯" to Resolved("interior_light_off", 1),
        "氛围灯打开" to Resolved("ambient_light_on",   5),
        "氛围灯关闭" to Resolved("ambient_light_off",  1),  // raw +1 shift: 1 = off (lvl0)

        // ── DRL ── LIVE_VALIDATED (dev=1004 carve-out) ────────────────────────
        "打开日行灯" to Resolved("drl_on",  1),
        "关闭日行灯" to Resolved("drl_off", 2),

        // ── Hazard lights ── dev=1004 write-only comfort channel ────────────────────
        "双闪打开" to Resolved("hazard_on",  1),
        "双闪关闭" to Resolved("hazard_off", 0),

        // ── Mirror heat = rear-window defrost ── LIVE_VALIDATED (dev=1000) ────
        "后视镜加热"   to Resolved("defrost_rear_on",  1),
        "关闭后视镜加热" to Resolved("defrost_rear_off", 0),
    )

    /**
     * Composite commands fan out to several validated per-door % writes. All four
     * window fids (driver/passenger/rear-left/rear-right *_pos) are LIVE_VALIDATED.
     */
    private fun allWindows(pct: Int): List<Resolved> = listOf(
        Resolved("window_driver_pos", pct),
        Resolved("window_passenger_pos", pct),
        Resolved("window_rear_left_pos", pct),
        Resolved("window_rear_right_pos", pct),
    )

    private val composite: Map<String, List<Resolved>> = mapOf(
        "车窗全开"     to allWindows(100),
        "车窗关闭"     to allWindows(0),
        "车窗半开"     to allWindows(50),
        "前排车窗全开" to listOf(Resolved("window_driver_pos", 100), Resolved("window_passenger_pos", 100)),
        "前排车窗关闭" to listOf(Resolved("window_driver_pos", 0),   Resolved("window_passenger_pos", 0)),
        "后排车窗全开" to listOf(Resolved("window_rear_left_pos", 100), Resolved("window_rear_right_pos", 100)),
        "后排车窗关闭" to listOf(Resolved("window_rear_left_pos", 0),   Resolved("window_rear_right_pos", 0)),
    )

    /**
     * Resolve a D+ command string. Strips leading "迪加" prefix if present.
     * Returns an empty list when the command is unknown — caller treats this as a
     * soft failure. A composite command returns several writes; the caller
     * dispatches each (fan-out).
     */
    fun resolve(commandString: String): List<Resolved> {
        val stripped = commandString.removePrefix("迪加")
        composite[stripped]?.let { return it }
        table[stripped]?.let { return listOf(it) }
        VehicleCommandSyntax.parseNumeric(stripped)?.let { return resolveNumeric(it) }
        return emptyList()
    }

    private fun resolveNumeric(command: NumericCommand): List<Resolved> = when (command) {
        is VehicleCommandSyntax.WindowPosition -> when (command.target) {
            WindowTarget.DRIVER -> listOf(Resolved("window_driver_pos", command.value))
            WindowTarget.PASSENGER -> listOf(Resolved("window_passenger_pos", command.value))
            WindowTarget.REAR_LEFT -> listOf(Resolved("window_rear_left_pos", command.value))
            WindowTarget.REAR_RIGHT -> listOf(Resolved("window_rear_right_pos", command.value))
            WindowTarget.ALL -> allWindows(command.value)
            WindowTarget.FRONT -> listOf(
                Resolved("window_driver_pos", command.value),
                Resolved("window_passenger_pos", command.value),
            )
            WindowTarget.REAR -> listOf(
                Resolved("window_rear_left_pos", command.value),
                Resolved("window_rear_right_pos", command.value),
            )
        }
        is VehicleCommandSyntax.Temperature -> listOf(
            Resolved(if (command.passenger) "ac_temp_deputy" else "ac_temp_main", command.value)
        )
        is VehicleCommandSyntax.FanLevel -> listOf(Resolved("ac_wind_level", command.value))
        is VehicleCommandSyntax.SunshadePosition -> listOf(Resolved("sunshade_set_position", command.value))
        is VehicleCommandSyntax.AmbientBrightness -> listOf(Resolved("ambient_light_level", command.value + 1))
        is VehicleCommandSyntax.Massage -> listOf(
            Resolved("${command.seat.actionPrefix}_massage_lvl${command.value}", command.value),
            Resolved("${command.seat.actionPrefix}_massage_${command.mode.actionSuffix}", command.mode.rawValue),
        )
    }

    /** Action names produced only by dynamic resolution (absent from [table]). */
    private val DYNAMIC_ACTIONS = buildSet {
        addAll(
            listOf(
                "ac_temp_main",
                "ac_temp_deputy",
                "ac_wind_level",
                "sunshade_set_position",
                "ambient_light_level",
            )
        )
        for (prefix in listOf("driver", "passenger")) {
            for (level in 1..3) add("${prefix}_massage_lvl$level")
            for (mode in listOf("wave", "pulse", "stretch", "waist", "shoulder")) {
                add("${prefix}_massage_$mode")
            }
        }
    }

    /** Set of all action_names referenced by this translator. Used by invariant test. */
    fun allActions(): Set<String> =
        (table.values.map { it.actionName } +
            composite.values.flatten().map { it.actionName } +
            DYNAMIC_ACTIONS)
            .toMutableSet()

    /** Representative values for the cross-layer translator/allowlist contract test. */
    internal fun contractSamples(): List<Resolved> = buildList {
        addAll(table.values)
        addAll(composite.values.flatten())
        listOf(
            "主驾打开0", "主驾打开100", "副驾打开50", "后左打开50", "后右打开50",
            "车窗打开0", "车窗打开100", "前排车窗打开50", "后排车窗打开50",
            "设置温度16", "设置温度30", "副驾温度16", "副驾温度30",
            "风量0档", "风量7档", "遮阳帘位置0", "遮阳帘位置100",
            "氛围灯亮度1档", "氛围灯亮度4档",
            "主驾按摩波浪1档", "主驾按摩肩部3档",
            "副驾按摩波浪1档", "副驾按摩肩部3档",
        ).forEach { addAll(resolve(it)) }
    }
}
