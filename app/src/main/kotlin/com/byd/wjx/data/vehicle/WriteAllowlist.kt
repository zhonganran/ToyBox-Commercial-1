package com.byd.wjx.data.vehicle

import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import org.json.JSONObject

/**
 * Hardcoded action_name → write address map. Production entries come from
 * vehicle-actions.json (source="wjx-vehicle-actions") merged with
 * LIVE_VALIDATED entries. Live entries win on name collision.
 *
 * Vehicle action JSON shape (vehicle-actions.json asset):
 *   { "<actionName>": { "featureId": Int, "deviceType": Int, "value"?: Int }, ... }
 * Entries without "value" are range/variable params; stored with valueMin=valueMax=0.
 *
 * Every entry MUST sit on a comfort-tier dev namespace. The invariant test
 * (WriteAllowlistTest) refuses to compile a release against any entry
 * targeting dev ∈ {1004, 1006, 1007, 1009, 1011, 1012, 1013, 1014, 1016, 1023, 1032}.
 *
 * Banned namespace reference:
 * - 1004 = light enums (firmware-protected)
 * - 1006 = drive mode
 * - 1007 = seatbelt sensor
 * - 1009 = charging gun state
 * - 1011 = gear
 * - 1012 = engine power
 * - 1013 = vehicle speed
 * - 1014 = BMS statistics
 * - 1016 = tire pressure
 * - 1023 = global powerState
 * - 1032 = door lock state-of-truth
 */
data class WriteEntry(
    val actionName: String,
    val dev: Int,
    val writeFid: Int,
    val readbackFid: Int?,
    val valueMin: Int,
    val valueMax: Int,
    val category: String,
    val validated: Boolean,
    val source: String,
)

class WriteAllowlist(private val map: Map<String, WriteEntry>) {
    val size: Int get() = map.size
    val validatedCount: Int get() = map.values.count { it.validated }
    val entries: Collection<WriteEntry> get() = map.values
    fun find(actionName: String): WriteEntry? = map[actionName.lowercase()]
    fun allEntries(): Collection<WriteEntry> = map.values
    companion object {
        private const val TAG = "WriteAllowlist"

        val EMPTY: WriteAllowlist = WriteAllowlist(emptyMap())

        val BANNED_DEVS: Set<Int> = setOf(
            1004, 1006, 1007, 1009, 1011, 1012, 1013, 1014, 1016, 1023, 1032
        )

        /**
         * Per-(dev,fid) carve-outs from [BANNED_DEVS]. dev=1023 (global powerState)
         * and dev=1004 (light enums) are blanket-banned, but BYD's own system app
         * (BYDAutoSettingDevice and validated vehicle diagnostics) drives the cabin/ambient
         * lights, DRL and hazard switch through these specific comfort fids via autoservice
         * setInt — the same channel D+ used. Only these exact pairs are exempt; the
         * rest of dev=1023/1004 stays banned. Each entry records its own validation source.
         */
        val BANNED_DEV_FID_EXCEPTIONS: Set<Pair<Int, Int>> = setOf(
            1023 to 1330643002,  // SET_INSIDE_LIGHT_STATE_SET (interior light on/off)
            1023 to 1069547536,  // SET_INTERIOR_ATMOSPHERE_LAMP_BRIGHTNESS_SET (ambient)
            1023 to 1276190736,  // main-driver massage intensity
            1023 to 1276190752,  // main-driver massage mode
            1023 to 1276190739,  // co-driver massage intensity
            1023 to 1276190756,  // co-driver massage mode
            1004 to 1125122118,  // DRL (daytime running lights) on/off
            1004 to 871366669,   // hazard lights on/off
            1004 to 1125122084,  // unified headlight height level
        )

        /** Official Settings feature-catalog readbacks for optional comfort writes. */
        private val OFFICIAL_READBACK_BY_WRITE: Map<Pair<Int, Int>, Int> = mapOf(
            (1000 to 501219340) to 1077936156,  // AC wind level
            (1000 to 501219336) to 1077936152,  // AC wind mode
            (1000 to 501219346) to 1077936164,  // dual/separate temperature zone
            (1000 to 501219376) to 1077936176,  // passenger target temperature
            (1000 to 501219394) to 1314914314,  // ventilation-only state
            (1000 to 944767029) to 1116733454,  // steering-wheel heat state
            (1001 to 1330642984) to 1101004816, // sunshade percentage
            (1004 to 1125122084) to 1254096916, // current headlight height level
            (1023 to 1276190736) to 1335885876, // driver massage intensity
            (1023 to 1276190752) to 1335885872, // driver massage mode
            (1023 to 1276190739) to 1335885884, // passenger massage intensity
            (1023 to 1276190756) to 1335885880, // passenger massage mode
        )

        /**
         * Safe value ranges for variable actions in vehicle-actions.json.
         * Without an explicit range an action remains fixed at zero and cannot
         * accidentally accept arbitrary values.
         */
        private val OFFICIAL_VARIABLE_RANGES: Map<Pair<Int, Int>, IntRange> = mapOf(
            (1000 to 501219352) to 0..1,       // AC control mode
            (1000 to 501219355) to 1..2,       // inner / outer circulation
            (1000 to 501219362) to 0..1,       // front defrost
            (1000 to 501219357) to 0..1,       // rear defrost
            (1000 to 501219346) to 0..1,       // sync / separate temperature zone
            (1000 to 501219364) to 0..1,       // AC power
            (1000 to 501219368) to 16..30,     // driver target temperature
            (1000 to 501219376) to 16..30,     // passenger target temperature
            (1000 to 501219340) to 0..7,       // wind level
            (1000 to 501219336) to 1..7,       // wind mode
            (1000 to 501219394) to 0..1,       // ventilation-only state
            (1000 to 944767029) to 1..2,       // steering-wheel heat off/on
            (1001 to 1330642984) to 0..100,    // sunshade percentage
        )

        /** A (dev,fid) is banned unless explicitly carved out. */
        internal fun isBanned(dev: Int, fid: Int): Boolean =
            dev in BANNED_DEVS && (dev to fid) !in BANNED_DEV_FID_EXCEPTIONS

        // Native write entries on Fangchengbao Bao 3 via HelperDaemon. AC power follows the
        // Official 0=off, 1=on contract; ac_off remains conservatively unvalidated
        // until the corrected zero-value path is confirmed on the vehicle.
        // 2026-05-28 (climate/windows/sunroof/sunshade/locks) + 8 live-validated
        // 2026-05-29 (interior/ambient light, DRL, rear-window-defrost = mirror heat).
        // The sole non-validated entry is ac_on: its address uses the
        // ac_ctrl_mode channel (501219352=0) and awaits an in-vehicle
        // confirmation snap before promotion to validated=true — the prior
        // power-fid/value-2 guess never turned the AC on.
        val LIVE_VALIDATED: List<WriteEntry> = listOf(
            // climate (dev=1000)
            WriteEntry("ac_on",          1000, 501219352, null, 0, 0,   "climate",  false, "wjx-vehicle-actions"),
            WriteEntry("ac_off",         1000, 501219364, null, 0, 0,   "climate",  false, "validated-settings-contract"),
            WriteEntry("ac_temp_main",   1000, 501219368, null, 16, 30, "climate",  true, "wjx-live-2026-05-28"),
            WriteEntry("ac_cycle_inner", 1000, 501219355, null, 1, 1,   "climate",  true, "wjx-live-2026-05-28"),
            WriteEntry("ac_cycle_outer", 1000, 501219355, null, 2, 2,   "climate",  true, "wjx-live-2026-05-28"),

            // windows short-form (front only)
            WriteEntry("window_driver_open",     1001, 1125122104, null, 1, 1, "windows", true, "wjx-live-2026-05-28"),
            WriteEntry("window_driver_close",    1001, 1125122104, null, 2, 2, "windows", true, "wjx-live-2026-05-28"),
            WriteEntry("window_passenger_open",  1001, 1125122107, null, 1, 1, "windows", true, "wjx-live-2026-05-28"),
            WriteEntry("window_passenger_close", 1001, 1125122107, null, 2, 2, "windows", true, "wjx-live-2026-05-28"),

            // windows Fangchengbao Bao 3 % path (preferred — all 4 doors, 0..100%)
            WriteEntry("window_driver_pos",      1001, 1276219408, null, 0, 100, "windows", true, "wjx-live-2026-05-28"),
            WriteEntry("window_passenger_pos",   1001, 1276219424, null, 0, 100, "windows", true, "wjx-live-2026-05-28"),
            WriteEntry("window_rear_left_pos",   1001, 1276219416, null, 0, 100, "windows", true, "wjx-live-2026-05-28"),
            WriteEntry("window_rear_right_pos",  1001, 1276219432, null, 0, 100, "windows", true, "wjx-live-2026-05-28"),

            // sunroof
            WriteEntry("sunroof_open",    1001, 1125122056, null, 1, 1, "sunroof", true, "wjx-live-2026-05-28"),
            WriteEntry("sunroof_close",   1001, 1125122056, null, 2, 2, "sunroof", true, "wjx-live-2026-05-28"),
            WriteEntry("sunroof_tilt",    1001, 1125122056, null, 3, 3, "sunroof", true, "wjx-live-2026-05-28"),
            WriteEntry("sunroof_stop",    1001, 1125122056, null, 4, 4, "sunroof", true, "wjx-live-2026-05-28"),
            WriteEntry("sunroof_updip",   1001, 1125122056, null, 5, 5, "sunroof", true, "wjx-live-2026-05-28"),
            WriteEntry("sunroof_comfort", 1001, 1125122056, null, 6, 6, "sunroof", true, "wjx-live-2026-05-28"),

            // sunshade
            WriteEntry("sunshade_open",  1001, 1125122060, null, 1, 1, "sunshade", true, "wjx-live-2026-05-28"),
            WriteEntry("sunshade_close", 1001, 1125122060, null, 2, 2, "sunshade", true, "wjx-live-2026-05-28"),

            // locks (readback fid 1081081864 mirrors write value)
            WriteEntry("doors_unlock", 1001, 1276141590, 1081081864, 1, 1, "locks", true, "wjx-live-2026-05-28"),
            WriteEntry("doors_lock",   1001, 1276141590, 1081081864, 2, 2, "locks", true, "wjx-live-2026-05-28"),

            // interior/cabin light — SET_INSIDE_LIGHT_STATE_SET (1=off, 2=on), dev=1023 carve-out
            WriteEntry("interior_light_on",  1023, 1330643002, null, 2, 2, "lights", true, "wjx-live-2026-05-29"),
            WriteEntry("interior_light_off", 1023, 1330643002, null, 1, 1, "lights", true, "wjx-live-2026-05-29"),
            // ambient (IAL) via brightness — raw is +1 shifted: raw 1=off(lvl0), 2..5=lvl1..4, dev=1023 carve-out
            WriteEntry("ambient_light_on",   1023, 1069547536, null, 5, 5, "lights", true, "wjx-live-2026-05-29"),
            WriteEntry("ambient_light_off",  1023, 1069547536, null, 1, 1, "lights", true, "wjx-live-2026-05-29"),
            WriteEntry("ambient_light_level", 1023, 1069547536, null, 2, 5, "lights", true, "wjx-live-2026-05-29"),
            // DRL (daytime running lights) — 1=on, 2=off (0 invalid), dev=1004 carve-out
            WriteEntry("drl_on",  1004, 1125122118, null, 1, 1, "lights", true, "wjx-live-2026-05-29"),
            WriteEntry("drl_off", 1004, 1125122118, null, 2, 2, "lights", true, "wjx-live-2026-05-29"),
            // Hazard switch is write-only on this channel: 1=on, 0=off.
            WriteEntry("hazard_on",  1004, 871366669, null, 1, 1, "lights", true, "bydmate-field-2026-07-31"),
            WriteEntry("hazard_off", 1004, 871366669, null, 0, 0, "lights", true, "bydmate-field-2026-07-31"),
            // mirror heat = rear-window defrost (single button on Fangchengbao Bao 3) — 1=on, 0=off, dev=1000
            WriteEntry("defrost_rear_on",  1000, 501219357, null, 1, 1, "climate", true, "wjx-live-2026-05-29"),
            WriteEntry("defrost_rear_off", 1000, 501219357, null, 0, 0, "climate", true, "wjx-live-2026-05-29"),

            // Seat heat/vent primary channel. On = switch 1 + level 1..5;
            // off = switch 2. switch 0 can return a real-looking status on some
            // firmwares while leaving the seat on, so AdaptiveSeatChannel never uses it.
            WriteEntry("driver_seat_heat_switch",    1000, 1276248084, null, 0, 2, "seats", true, "wjx-live-2026-07-01"),
            WriteEntry("driver_seat_heat_level",     1000, 1276252180, null, 1, 5, "seats", true, "wjx-live-2026-06-29"),
            WriteEntry("passenger_seat_heat_switch", 1000, 1276248092, null, 0, 2, "seats", true, "wjx-live-2026-07-01"),
            WriteEntry("passenger_seat_heat_level",  1000, 1276252188, null, 1, 5, "seats", true, "wjx-live-2026-06-29"),
            WriteEntry("driver_seat_vent_switch",    1000, 1276248080, null, 0, 2, "seats", true, "wjx-live-2026-07-01"),
            WriteEntry("driver_seat_vent_level",     1000, 1276252176, null, 1, 5, "seats", true, "wjx-live-2026-06-29"),
            WriteEntry("passenger_seat_vent_switch", 1000, 1276248088, null, 0, 2, "seats", true, "wjx-live-2026-07-01"),
            WriteEntry("passenger_seat_vent_level",  1000, 1276252184, null, 1, 5, "seats", true, "wjx-live-2026-06-29"),
        )

        /**
         * Candidate (unvalidated) native channels staged for an in-vehicle snap.
         * Holds official-but-unverified feature channels plus compatibility fallbacks.
         * The merge order in [loadProduction] folds these in after vehicle-actions.json;
         * LIVE_VALIDATED still wins on collision.
         */
        val CANDIDATE_UNVALIDATED: List<WriteEntry> = listOf(
            // Original Settings APK: UI index 0..5 maps to raw 11,9,7,5,3,1.
            WriteEntry(
                "headlight_height_level",
                1004,
                1125122084,
                1254096916,
                1,
                11,
                "lights",
                false,
                "byd-settings-apk",
            ),
            // Seat heat/vent fallback channel. value range 1..6:
            // 1=off, 2=lvl1 ... 6=lvl5.
            WriteEntry("driver_seat_heat_fallback",    1001, 1125122068, null, 1, 6, "seats", false, "validated-v80"),
            WriteEntry("driver_seat_vent_fallback",    1001, 1125122064, null, 1, 6, "seats", false, "validated-v80"),
            WriteEntry("passenger_seat_heat_fallback", 1001, 1125122076, null, 1, 6, "seats", false, "validated-v80"),
            WriteEntry("passenger_seat_vent_fallback", 1001, 1125122072, null, 1, 6, "seats", false, "validated-v80"),
        )

        /**
         * Category inference from action name prefix.
         * Order matters — check most-specific prefixes first.
         */
        private fun inferCategory(name: String): String = when {
            name.startsWith("ac_") || name.startsWith("set_driver_temp") ||
                name.startsWith("set_passenger_temp") || name.contains("defrost") -> "climate"
            name.startsWith("window_") || name.startsWith("windows_") ||
                name.startsWith("close_windows") || name.startsWith("open_windows") -> "windows"
            name.contains("lock") || name.startsWith("door") ||
                name.startsWith("unlock") -> "locks"
            name.startsWith("sunroof_") || name.startsWith("open_sunroof") ||
                name.startsWith("close_sunroof") || name.startsWith("tilt_sunroof") ||
                name.startsWith("comfort_sunroof") -> "sunroof"
            name.startsWith("sunshade_") || name.startsWith("open_sunshade") ||
                name.startsWith("close_sunshade") || name.startsWith("set_sunshade") -> "sunshade"
            name.startsWith("trunk_") || name.startsWith("open_trunk") ||
                name.startsWith("close_trunk") -> "trunk"
            name.startsWith("drl_") || name.contains("light") -> "lights"
            name.contains("seat") && (name.contains("heat") || name.contains("vent")) ||
                name.contains("massage") -> "seats"
            name.contains("mirror") -> "mirrors"
            name.contains("steering") || name.contains("wheel_heat") -> "climate"
            else -> "other"
        }

        /**
         * Parse vehicle-actions.json, merge with LIVE_VALIDATED (live wins),
         * filter banned devs (logged as W "WriteAllowlist").
         *
         * [assetReader] returns the raw JSON text of vehicle-actions.json.
         */
        fun loadProduction(assetReader: () -> String): WriteAllowlist {
            // Fail-fast: LIVE_VALIDATED must have no duplicate actionName (case-insensitive).
            val liveKeys = LIVE_VALIDATED.map { it.actionName.lowercase() }
            require(liveKeys.size == liveKeys.distinct().size) {
                val dupes = liveKeys.groupBy { it }.filter { it.value.size > 1 }.keys
                "LIVE_VALIDATED has duplicate actionName(s) (case-insensitive): $dupes"
            }

            val json = JSONObject(assetReader())
            // Key = lowercase actionName for collision detection; value preserves original casing.
            val merged = mutableMapOf<String, WriteEntry>()

            // Parse base asset entries first (keyed by lowercase, entry stores original name)
            val keys = json.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                try {
                    val obj = json.getJSONObject(name)
                    val dev = obj.getInt("deviceType")
                    val fid = obj.getInt("featureId")
                    val hasValue = obj.has("value")
                    val value = if (hasValue) obj.getInt("value") else 0
                    val officialRange = OFFICIAL_VARIABLE_RANGES[dev to fid]
                    val valueMin = if (hasValue) value else officialRange?.first ?: 0
                    val valueMax = if (hasValue) value else officialRange?.last ?: 0
                    if (isBanned(dev, fid)) {
                        Log.w(TAG, "Dropping banned-dev action=$name dev=$dev")
                        WjxFileLogger.w(TAG, "Dropping banned-dev action=$name dev=$dev")
                        continue
                    }
                    merged[name.lowercase()] = WriteEntry(
                        actionName = name,
                        dev = dev,
                        writeFid = fid,
                        readbackFid = OFFICIAL_READBACK_BY_WRITE[dev to fid],
                        valueMin = valueMin,
                        valueMax = valueMax,
                        category = inferCategory(name),
                        validated = false,
                        source = "wjx-vehicle-actions",
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "skip malformed vehicle action entry $name: ${e.message}")
                    WjxFileLogger.w(TAG, "skip malformed vehicle action entry $name: ${e.message}")
                    continue
                }
            }

            // Candidate (unvalidated) native entries merged after base asset.
            // Carve-out check still applies (only exempt dev=1023 light fids pass).
            for (entry in CANDIDATE_UNVALIDATED) {
                if (isBanned(entry.dev, entry.writeFid)) {
                    Log.w(TAG, "Dropping banned-dev candidate=${entry.actionName} dev=${entry.dev}")
                    WjxFileLogger.w(TAG, "Dropping banned-dev candidate=${entry.actionName} dev=${entry.dev}")
                    continue
                }
                merged[entry.actionName.lowercase()] = entry
            }

            // LIVE_VALIDATED wins on collision (match by lowercase key, store original entry)
            for (entry in LIVE_VALIDATED) {
                if (isBanned(entry.dev, entry.writeFid)) {
                    Log.w(TAG, "Dropping banned-dev live entry=${entry.actionName} dev=${entry.dev}")
                    WjxFileLogger.w(TAG, "Dropping banned-dev live entry=${entry.actionName} dev=${entry.dev}")
                    continue
                }
                merged[entry.actionName.lowercase()] = entry
            }

            return WriteAllowlist(merged)
        }
    }
}
