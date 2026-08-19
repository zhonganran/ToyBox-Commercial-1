package com.byd.wjx.data.vehicle

import com.byd.wjx.ui.automation.ACTION_COMMANDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommandTranslatorTest {

    // resolve() now returns a List<Resolved>: empty = unknown, 1 element = single
    // write, N elements = composite fan-out (e.g. window aggregates). Helper for
    // the single-command tests.
    private fun one(cmd: String): CommandTranslator.Resolved? =
        CommandTranslator.resolve(cmd).singleOrNull()

    private fun pairs(cmd: String): Set<Pair<String, Int>> =
        CommandTranslator.resolve(cmd).map { it.actionName to it.value }.toSet()

    // ── Test 1: prefix stripped before lookup ─────────────────────────────────
    @Test fun `prefix stripped before lookup`() {
        val r = one("迪加车门上锁")
        assertEquals("doors_lock", r?.actionName)
        assertEquals(2, r?.value)
    }

    // ── Test 2: unknown command returns empty list ────────────────────────────
    @Test fun `unknown command returns empty list`() {
        assertTrue(CommandTranslator.resolve("不存在的命令").isEmpty())
    }

    // ── Test 3: every translator action_name resolves in production allowlist ─
    // CI gate: translator + allowlist must stay in sync.
    @Test fun `every translator action_name resolves in production allowlist`() {
        val projectRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/assets/vehicle-actions.json").exists() }
            ?: error("Cannot find app/src/main/assets/vehicle-actions.json from ${File(".").canonicalPath}")
        val jsonText = File(projectRoot, "app/src/main/assets/vehicle-actions.json").readText()
        val allowlist = WriteAllowlist.loadProduction { jsonText }
        val unresolved = CommandTranslator.allActions().filter { allowlist.find(it) == null }
        assertTrue(
            "Translator references action_names not in allowlist: $unresolved",
            unresolved.isEmpty(),
        )
    }

    // ── Test 4: set temperature 22 maps to ac_temp_main val 22 ───────────────
    @Test fun `set temperature 22 maps to ac_temp_main val 22`() {
        val r = one("设置温度22")
        assertEquals("ac_temp_main", r?.actionName)
        assertEquals(22, r?.value)
    }

    // ── Test 5: command without prefix resolves directly ──────────────────────
    @Test fun `command without prefix resolves directly`() {
        val r = one("车门解锁")
        assertEquals("doors_unlock", r?.actionName)
        assertEquals(1, r?.value)
    }

    // ── Test 6: seat heat level 1 maps to _on val=2 ──────────────────────────
    @Test fun `seat heat level 1 maps to driver_seat_heat_on val 2`() {
        val r = one("主驾座椅加热1档")
        assertEquals("driver_seat_heat_on", r?.actionName)
        assertEquals(2, r?.value)
    }

    // ── Test 9: sunroof 50 maps to sunroof_tilt val=3 ────────────────────────
    @Test fun `sunroof 50 maps to sunroof_tilt val 3`() {
        val r = one("天窗打开50")
        assertEquals("sunroof_tilt", r?.actionName)
        assertEquals(3, r?.value)
    }

    // ── Test 10: inner circulation maps to ac_cycle_inner val=1 ──────────────
    @Test fun `inner circulation maps to ac_cycle_inner val 1`() {
        val r = one("内循环")
        assertEquals("ac_cycle_inner", r?.actionName)
        assertEquals(1, r?.value)
    }

    // ── Rear windows (individual) — Alice VPS vocab 后左/后右打开{n} ──────────────
    @Test fun `rear-left open 100 maps to window_rear_left_pos val 100`() {
        val r = one("后左打开100")
        assertEquals("window_rear_left_pos", r?.actionName)
        assertEquals(100, r?.value)
    }

    @Test fun `rear-right open 0 maps to window_rear_right_pos val 0`() {
        val r = one("后右打开0")
        assertEquals("window_rear_right_pos", r?.actionName)
        assertEquals(0, r?.value)
    }

    // ── Rear windows (aggregate) — fan-out to both validated % fids ────────────
    @Test fun `rear windows open fans out to both rear pos fids at 100`() {
        assertEquals(
            setOf("window_rear_left_pos" to 100, "window_rear_right_pos" to 100),
            pairs("后排车窗全开"),
        )
    }

    @Test fun `rear windows close fans out to both rear pos fids at 0`() {
        assertEquals(
            setOf("window_rear_left_pos" to 0, "window_rear_right_pos" to 0),
            pairs("后排车窗关闭"),
        )
    }

    // ── Front windows (aggregate) — fan-out to driver + passenger ─────────────
    @Test fun `front windows open fans out to driver and passenger pos at 100`() {
        assertEquals(
            setOf("window_driver_pos" to 100, "window_passenger_pos" to 100),
            pairs("前排车窗全开"),
        )
    }

    // ── All windows (aggregate) — fan-out to all four validated % fids ────────
    @Test fun `all windows open fans out to all four pos fids at 100`() {
        assertEquals(
            setOf(
                "window_driver_pos" to 100,
                "window_passenger_pos" to 100,
                "window_rear_left_pos" to 100,
                "window_rear_right_pos" to 100,
            ),
            pairs("车窗全开"),
        )
    }

    @Test fun `all windows close fans out to all four pos fids at 0`() {
        assertEquals(
            setOf(
                "window_driver_pos" to 0,
                "window_passenger_pos" to 0,
                "window_rear_left_pos" to 0,
                "window_rear_right_pos" to 0,
            ),
            pairs("车窗关闭"),
        )
    }

    @Test fun `all windows half fans out to all four pos fids at 50`() {
        assertEquals(
            setOf(
                "window_driver_pos" to 50,
                "window_passenger_pos" to 50,
                "window_rear_left_pos" to 50,
                "window_rear_right_pos" to 50,
            ),
            pairs("车窗半开"),
        )
    }

    // ── Interior / ambient light (candidate, dev=1023 carve-out) ──────────────
    @Test fun `open interior light maps to interior_light_on`() {
        val r = one("打开车内灯")
        assertEquals("interior_light_on", r?.actionName)
        assertEquals(2, r?.value)
    }

    @Test fun `close interior light maps to interior_light_off`() {
        val r = one("关闭车内灯")
        assertEquals("interior_light_off", r?.actionName)
        assertEquals(1, r?.value)
    }

    @Test fun `open ambient light maps to ambient_light_on`() {
        val r = one("氛围灯打开")
        assertEquals("ambient_light_on", r?.actionName)
    }

    @Test fun `close ambient light maps to ambient_light_off`() {
        val r = one("氛围灯关闭")
        assertEquals("ambient_light_off", r?.actionName)
    }

    // ── Ambient off must write raw 1 (level 0), not 0 (level -1, no effect) ───
    @Test fun `close ambient light writes value 1 not 0`() {
        val r = one("氛围灯关闭")
        assertEquals(1, r?.value)
    }

    // ── DRL — dev=1004 carve-out, validated 2026-05-29 ────────────────────────
    @Test fun `open drl maps to drl_on val 1`() {
        val r = one("打开日行灯")
        assertEquals("drl_on", r?.actionName)
        assertEquals(1, r?.value)
    }

    @Test fun `close drl maps to drl_off val 2`() {
        val r = one("关闭日行灯")
        assertEquals("drl_off", r?.actionName)
        assertEquals(2, r?.value)
    }

    @Test fun `hazard commands map to the write-only switch values`() {
        assertEquals("hazard_on" to 1, one("双闪打开")?.let { it.actionName to it.value })
        assertEquals("hazard_off" to 0, one("双闪关闭")?.let { it.actionName to it.value })
    }

    // ── Mirror heat = rear defrost (one button on Leopard 3), validated 2026-05-29 ──
    @Test fun `mirror heat on maps to defrost_rear_on val 1`() {
        val r = one("后视镜加热")
        assertEquals("defrost_rear_on", r?.actionName)
        assertEquals(1, r?.value)
    }

    @Test fun `mirror heat off maps to defrost_rear_off val 0`() {
        val r = one("关闭后视镜加热")
        assertEquals("defrost_rear_off", r?.actionName)
        assertEquals(0, r?.value)
    }

    // ── Auto AC must use ac_on value 0 (ctrl_mode AUTO), not 2 ────────────────
    @Test fun `auto AC maps to ac_on val 0`() {
        val r = one("自动空调")
        assertEquals("ac_on", r?.actionName)
        assertEquals(0, r?.value)
    }

    // ── Temperature: dynamic parse over full 16..30 range ────────────────────
    @Test fun `set temperature 24 maps to ac_temp_main val 24`() {
        val r = one("设置温度24")
        assertEquals("ac_temp_main", r?.actionName)
        assertEquals(24, r?.value)
    }

    @Test fun `set temperature 16 maps to ac_temp_main val 16`() {
        val r = one("设置温度16")
        assertEquals("ac_temp_main", r?.actionName)
        assertEquals(16, r?.value)
    }

    // Out-of-range request clamps into the validated 16..30 window.
    @Test fun `set temperature 35 clamps to ac_temp_main val 30`() {
        val r = one("设置温度35")
        assertEquals("ac_temp_main", r?.actionName)
        assertEquals(30, r?.value)
    }

    @Test fun `set temperature 5 clamps to ac_temp_main val 16`() {
        val r = one("设置温度5")
        assertEquals("ac_temp_main", r?.actionName)
        assertEquals(16, r?.value)
    }

    // ── Trunk ── vehicle-actions.json (dev=1001, open=1 close=3) ─────────────
    @Test fun `open trunk maps to open_trunk val 1`() {
        val r = one("开后备箱")
        assertEquals("open_trunk", r?.actionName)
        assertEquals(1, r?.value)
    }

    @Test fun `close trunk maps to close_trunk val 3`() {
        val r = one("关后备箱")
        assertEquals("close_trunk", r?.actionName)
        assertEquals(3, r?.value)
    }

    @Test fun `seat heat and vent expose full five levels`() {
        assertEquals("driver_seat_heat_lvl5" to 6, one("主驾座椅加热5档")?.let { it.actionName to it.value })
        assertEquals("passenger_seat_vent_lvl4" to 5, one("副驾座椅通风4档")?.let { it.actionName to it.value })
    }

    @Test fun `arbitrary window percentages use validated per-window routes`() {
        assertEquals("window_rear_left_pos" to 37, one("后左打开37")?.let { it.actionName to it.value })
        assertEquals(
            setOf(
                "window_driver_pos" to 63,
                "window_passenger_pos" to 63,
                "window_rear_left_pos" to 63,
                "window_rear_right_pos" to 63,
            ),
            pairs("车窗打开63"),
        )
    }

    @Test fun `new climate controls use official Settings enums`() {
        assertEquals("ac_off" to 0, one("关闭空调")?.let { it.actionName to it.value })
        assertEquals("ac_temp_deputy" to 24, one("副驾温度24")?.let { it.actionName to it.value })
        assertEquals("ac_wind_level" to 7, one("风量7档")?.let { it.actionName to it.value })
        assertEquals("ac_dual" to 0, one("主副驾温度同步")?.let { it.actionName to it.value })
        assertEquals("ac_dual" to 1, one("主副驾温度分区")?.let { it.actionName to it.value })
        assertEquals("ac_flow_only_off" to 0, one("关闭空调通风")?.let { it.actionName to it.value })
        assertEquals("ac_wind_mode" to 1, one("出风吹脸")?.let { it.actionName to it.value })
        assertEquals("ac_wind_mode" to 7, one("出风吹脸除霜")?.let { it.actionName to it.value })
    }

    @Test fun `ambient sunshade and steering comfort controls map correctly`() {
        assertEquals("ambient_light_level" to 4, one("氛围灯亮度3档")?.let { it.actionName to it.value })
        assertEquals("sunshade_set_position" to 72, one("遮阳帘位置72")?.let { it.actionName to it.value })
        assertEquals("steering_heat_on" to 2, one("方向盘加热")?.let { it.actionName to it.value })
        assertEquals("steering_heat_off" to 1, one("关闭方向盘加热")?.let { it.actionName to it.value })
    }

    @Test fun `massage uses official mode enum and writes intensity before mode`() {
        assertEquals(
            listOf(
                "driver_massage_lvl3" to 3,
                "driver_massage_wave" to 2,
            ),
            CommandTranslator.resolve("主驾按摩波浪3档").map { it.actionName to it.value },
        )
        assertEquals(
            listOf(
                "passenger_massage_lvl2" to 2,
                "passenger_massage_shoulder" to 6,
            ),
            CommandTranslator.resolve("副驾按摩肩部2档").map { it.actionName to it.value },
        )
        assertEquals("driver_massage_off" to 1, one("主驾按摩关闭")?.let { it.actionName to it.value })
    }

    // ── Test 12: allActions returns non-empty set of unique names ─────────────
    @Test fun `allActions returns non-empty set`() {
        val actions = CommandTranslator.allActions()
        assertTrue("allActions must be non-empty", actions.isNotEmpty())
    }

    @Test fun `every automation vehicle action resolves to at least one write`() {
        val unresolved = ACTION_COMMANDS
            .map { it.command }
            .filter { CommandTranslator.resolve(it).isEmpty() }
        assertTrue("Automation actions without control mapping: $unresolved", unresolved.isEmpty())
    }

    @Test fun `every automation vehicle action value is accepted by production allowlist`() {
        val projectRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/assets/vehicle-actions.json").exists() }
            ?: error("Cannot find project root")
        val allowlist = WriteAllowlist.loadProduction {
            File(projectRoot, "app/src/main/assets/vehicle-actions.json").readText()
        }
        val invalid = ACTION_COMMANDS.flatMap { option ->
            CommandTranslator.resolve(option.command).mapNotNull { resolved ->
                val entry = allowlist.find(resolved.actionName)
                when {
                    entry == null -> "${option.command}: missing ${resolved.actionName}"
                    resolved.value !in entry.valueMin..entry.valueMax ->
                        "${option.command}: ${resolved.actionName}=${resolved.value} outside ${entry.valueMin}..${entry.valueMax}"
                    else -> null
                }
            }
        }
        assertTrue("Automation write contract drift: $invalid", invalid.isEmpty())
    }

    @Test fun `every translator sample value is accepted by production allowlist`() {
        val projectRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/assets/vehicle-actions.json").exists() }
            ?: error("Cannot find project root")
        val allowlist = WriteAllowlist.loadProduction {
            File(projectRoot, "app/src/main/assets/vehicle-actions.json").readText()
        }
        val invalid = CommandTranslator.contractSamples().mapNotNull { resolved ->
            val entry = allowlist.find(resolved.actionName)
            when {
                entry == null -> "missing ${resolved.actionName}"
                resolved.value !in entry.valueMin..entry.valueMax ->
                    "${resolved.actionName}=${resolved.value} outside ${entry.valueMin}..${entry.valueMax}"
                else -> null
            }
        }.distinct()
        assertTrue("Translator/allowlist contract drift: $invalid", invalid.isEmpty())
    }
}
