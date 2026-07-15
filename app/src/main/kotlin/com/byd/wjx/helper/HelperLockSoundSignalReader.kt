package com.byd.wjx.helper

import android.content.Context
import android.os.IBinder
import java.io.File
import java.util.Locale
import kotlin.math.abs

internal const val LOCK_SOUND_DOOR_UNLOCKED = 1
internal const val LOCK_SOUND_DOOR_LOCKED = 2

internal data class SentryWarningReading(
    val level: Int?,
    val source: String,
)

internal data class LockSoundPowerSnapshot(
    val vehicleState: Int?,
    val bodyworkLevel: Int?,
    val sdkPower: Int?,
) {
    fun blocksPlayback(): Boolean = shouldBlockLockSoundForPower(vehicleState, bodyworkLevel, sdkPower)

    fun summary(): String =
        "vehicle=$vehicleState body=$bodyworkLevel sdk=$sdkPower blocked=${blocksPlayback()}"
}

internal data class LockSoundVehicleAccessSnapshot(
    val lockFrontLeft: Int?,
    val lockFrontRight: Int?,
    val lockRearLeft: Int?,
    val lockRearRight: Int?,
    val doorFrontLeftOpen: Boolean?,
    val doorFrontRightOpen: Boolean?,
    val doorRearLeftOpen: Boolean?,
    val doorRearRightOpen: Boolean?,
    val trunkOpen: Boolean?,
    val hoodOpen: Boolean?,
) {
    private val locks: List<Int?>
        get() = listOf(lockFrontLeft, lockFrontRight, lockRearLeft, lockRearRight)

    private val openings: List<Boolean?>
        get() = listOf(
            doorFrontLeftOpen,
            doorFrontRightOpen,
            doorRearLeftOpen,
            doorRearRightOpen,
            trunkOpen,
            hoodOpen,
        )

    fun accessDetected(): Boolean =
        locks.any { it == LOCK_SOUND_DOOR_UNLOCKED } || openings.any { it == true }

    fun secureForSentry(): Boolean =
        !accessDetected() &&
            locks.any { it == LOCK_SOUND_DOOR_LOCKED } &&
            (locks.any { it != null } || openings.any { it != null })

    fun summary(): String =
        "lock=$lockFrontLeft/$lockFrontRight/$lockRearLeft/$lockRearRight " +
            "door=$doorFrontLeftOpen/$doorFrontRightOpen/$doorRearLeftOpen/$doorRearRightOpen " +
            "trunk=$trunkOpen hood=$hoodOpen"
}

internal fun shouldBlockLockSoundForPower(
    vehicleState: Int?,
    bodyworkLevel: Int?,
    sdkPower: Int?,
): Boolean {
    val values = listOf(vehicleState, bodyworkLevel, sdkPower)
    if (values.any { it != null && it >= 1 }) return true
    return values.none { it == 0 }
}

internal fun normalizeVehicleAccessLockState(raw: Int?): Int? =
    when (raw) {
        0, 1 -> LOCK_SOUND_DOOR_UNLOCKED
        2 -> LOCK_SOUND_DOOR_LOCKED
        else -> null
    }

internal fun validateLockSoundDoorState(raw: Int?): Int? =
    when (raw) {
        1 -> LOCK_SOUND_DOOR_UNLOCKED
        2 -> LOCK_SOUND_DOOR_LOCKED
        else -> null
    }

internal class WjxLockSoundSignalReader(
    private val svc: IBinder,
    private val autoIface: String,
    private val systemContext: Context?,
) {
    private fun readOne(dev: Int, fid: Int): Int? {
        val (status, value) = autoserviceTransact(svc, autoIface, 5, dev, fid, 0, writeValue = false)
        return value.takeIf { status == 0 && isUsefulVehicleValue(it) }
    }

    private fun readAny(vararg reads: Pair<Int, Int>): Int? {
        reads.forEach { (dev, fid) ->
            readOne(dev, fid)?.let { return it }
        }
        return null
    }

    fun readLockState(): Int? {
        val reads = arrayOf(
            1032 to 1081081864,
            1041 to -1066401784,
            1041 to 960495668,
        )
        reads.forEach { (dev, fid) ->
            validateLockSoundDoorState(readOne(dev, fid))?.let { return it }
        }
        return validateLockSoundDoorState(readDoorLockBySdk())
    }

    fun readDriverDoorOpen(): Boolean? =
        readAny(1001 to DRIVER_DOOR_OPEN_STATE)?.let { it == 1 }

    fun readTurnSignalFlash(): Int? =
        readAny(1004 to 950009900) ?: readTurnSignalFlashBySdk()

    fun readPowerSnapshot(): LockSoundPowerSnapshot =
        LockSoundPowerSnapshot(
            vehicleState = readOne(1023, VEHICLE_POWER_STATE_FID)?.takeIf { it >= 0 },
            bodyworkLevel = readOne(1001, BODYWORK_POWER_LEVEL_FID)?.takeIf { it >= 0 },
            sdkPower = readPowerStateBySdk()?.takeIf { it in SDK_POWER_STATE_RANGE },
        )

    fun readVehicleAccessSnapshot(): LockSoundVehicleAccessSnapshot =
        LockSoundVehicleAccessSnapshot(
            lockFrontLeft = readLockState(),
            lockFrontRight = readAccessLockState(2, FRONT_RIGHT_LOCK_STATE_FID),
            lockRearLeft = readAccessLockState(3, REAR_LEFT_LOCK_STATE_FID),
            lockRearRight = readAccessLockState(4, REAR_RIGHT_LOCK_STATE_FID),
            doorFrontLeftOpen = readOpenState(FRONT_LEFT_DOOR_OPEN_STATE_FID),
            doorFrontRightOpen = readOpenState(FRONT_RIGHT_DOOR_OPEN_STATE_FID),
            doorRearLeftOpen = readOpenState(REAR_LEFT_DOOR_OPEN_STATE_FID),
            doorRearRightOpen = readOpenState(REAR_RIGHT_DOOR_OPEN_STATE_FID),
            trunkOpen = readOpenState(TRUNK_OPEN_STATE_FID),
            hoodOpen = readOpenState(HOOD_OPEN_STATE_FID),
        )

    fun readSentryWarning(): SentryWarningReading {
        val candidates = arrayOf(
            1049 to 1128267797,
            1049 to 1128267829,
            1049 to 861929534,
            1049 to 861929530,
        )
        candidates.forEach { (dev, fid) ->
            val (status, value) = autoserviceTransact(svc, autoIface, 5, dev, fid, 0, writeValue = false)
            if (status == 0 && value in 0..SENTRY_WARN_ALARM) {
                return SentryWarningReading(value, "fid:$dev/$fid")
            }
        }
        val setting = shExec("settings get global sentrymode_warning_level").stdout
            .trim()
            .toIntOrNull()
            ?.takeIf { it in 0..SENTRY_WARN_ALARM }
        return SentryWarningReading(
            level = setting,
            source = if (setting != null) "global:sentrymode_warning_level" else "unavailable",
        )
    }

    fun isSentryGuarding(): Boolean {
        val enabled = shExec("settings get global sentrymode_enabled").stdout.trim()
        val state = shExec("getprop sys.byd.sentrymode_state").stdout.trim()
        return enabled == "1" || state.equals("enabled", ignoreCase = true)
    }

    fun consumeSentryAlarmTimestamp(value: String, allowTrigger: Boolean): Boolean {
        if (value.isBlank() || value == "0" || value.equals("null", ignoreCase = true)) return false
        val cursor = File(SENTRY_PROP_CURSOR_PATH)
        val previous = runCatching { cursor.takeIf { it.isFile }?.readText()?.trim().orEmpty() }.getOrDefault("")
        if (previous.isBlank()) {
            runCatching { cursor.writeText(value) }
            if (allowTrigger && isRecentSentryAlarmTimestamp(value)) {
                helperLog(TAG, "sentry prop first fresh=$value")
                return true
            }
            helperLog(TAG, "sentry prop baseline=$value allowTrigger=$allowTrigger")
            return false
        }
        if (value == previous) return false
        runCatching { cursor.writeText(value) }
        if (!allowTrigger) {
            helperLog(TAG, "sentry prop baseline update=$value")
            return false
        }
        helperLog(TAG, "sentry prop changed previous=$previous current=$value")
        return true
    }

    private fun isRecentSentryAlarmTimestamp(value: String): Boolean {
        val raw = value.toLongOrNull() ?: return true
        val seconds = if (raw > 10_000_000_000L) raw / 1_000L else raw
        val nowSeconds = System.currentTimeMillis() / 1_000L
        return abs(nowSeconds - seconds) <= SENTRY_PROP_FIRST_FRESH_SECONDS
    }

    fun readFreshSentryLogMarker(allowTrigger: Boolean): String {
        val out = shExec("logcat -d -v threadtime -t 300 -s SENTRYMODE:D").stdout
        val marker = out.lineSequence()
            .lastOrNull(::isHighRiskSentryLogLine)
            .orEmpty()
        if (marker.isBlank()) return ""

        val cursor = File(SENTRY_LOG_CURSOR_PATH)
        val previous = runCatching { cursor.takeIf { it.isFile }?.readText()?.trim().orEmpty() }.getOrDefault("")
        if (previous.isBlank()) {
            runCatching { cursor.writeText(marker) }
            helperLog(TAG, "sentry logcat skip backlog cursor=${marker.take(80)}")
            return ""
        }
        if (marker == previous) return ""
        runCatching { cursor.writeText(marker) }
        if (!allowTrigger) {
            helperLog(TAG, "sentry logcat baseline update=${marker.take(80)}")
            return ""
        }
        helperLog(TAG, "sentry logcat high marker=${marker.take(120)}")
        return marker
    }

    private fun isHighRiskSentryLogLine(line: String): Boolean {
        val lower = line.lowercase(Locale.US)
        return lower.contains("lockvideohandler") &&
            lower.contains("msg.what = 3") &&
            !lower.contains("responseinform warn:")
    }

    private fun readTurnSignalFlashBySdk(): Int? =
        invokeBydDeviceInt(
            classNames = listOf("android.hardware.bydauto.light.BYDAutoLightDevice"),
            methodName = "getTurnLightFlashState",
        )

    private fun readDoorLockBySdk(): Int? =
        readDoorLockBySdk(1)

    private fun readDoorLockBySdk(door: Int): Int? =
        invokeBydDeviceInt(
            classNames = listOf(
                "android.hardware.bydauto.bodywork.BYDAutoDoorLockDevice",
                "android.hardware.bydauto.doorlock.BYDAutoDoorLockDevice",
            ),
            methodName = "getDoorLockStatus",
            intArg = door,
        ) ?: if (door == 1) invokeBydDeviceInt(
            classNames = listOf(
                "android.hardware.bydauto.ota.BYDAutoOtaDevice",
                "android.hardware.bydauto.bodywork.BYDAutoOtaDevice",
            ),
            methodName = "getLFDoorLockState",
        ) else null

    private fun readAccessLockState(door: Int, fid: Int): Int? =
        normalizeVehicleAccessLockState(readOne(1001, fid))
            ?: normalizeVehicleAccessLockState(readDoorLockBySdk(door))

    private fun readOpenState(fid: Int): Boolean? =
        readOne(1001, fid)?.takeIf { it == 0 || it == 1 }?.let { it == 1 }

    private fun readPowerStateBySdk(): Int? =
        invokeBydDeviceInt(
            classNames = listOf("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"),
            methodName = "getPowerLevel",
        )

    private fun invokeBydDeviceInt(classNames: List<String>, methodName: String, intArg: Int? = null): Int? {
        val ctx = systemContext ?: return null
        classNames.forEach { className ->
            val value = runCatching {
                val clazz = Class.forName(className)
                val out = StringBuilder()
                val instance = createBydBodyworkInstance(clazz, ctx, out) ?: return@runCatching null
                val method = instance.javaClass.methods.firstOrNull {
                    it.name == methodName &&
                        ((intArg == null && it.parameterTypes.isEmpty()) ||
                            (intArg != null && it.parameterTypes.size == 1 && it.parameterTypes[0] == Integer.TYPE))
                } ?: return@runCatching null
                method.isAccessible = true
                val raw = if (intArg == null) method.invoke(instance) else method.invoke(instance, intArg)
                (raw as? Number)?.toInt() ?: raw?.toString()?.toIntOrNull()
            }.getOrNull()
            if (value != null && isUsefulVehicleValue(value)) return value
        }
        return null
    }

    fun readSentryAlarmTimestamp(): String =
        shExec("getprop sys.byd.sentry_alarm_timestamp").stdout.trim()

    private fun isUsefulVehicleValue(value: Int): Boolean =
        value != 0x0000FFFF && value != Int.MAX_VALUE && value != Int.MIN_VALUE

    private companion object {
        private const val TAG = "LockSound"
        private const val SENTRY_PROP_FIRST_FRESH_SECONDS = 120L
        private const val SENTRY_LOG_CURSOR_PATH = "/data/local/tmp/wjx_sentry_msg3_cursor.txt"
        private const val SENTRY_PROP_CURSOR_PATH = "/data/local/tmp/wjx_sentry_alarm_ts_cursor.txt"
        private val SDK_POWER_STATE_RANGE = 0..4
        private const val VEHICLE_POWER_STATE_FID = 315621408
        private const val BODYWORK_POWER_LEVEL_FID = 315621418
        private const val FRONT_RIGHT_LOCK_STATE_FID = 1081081866
        private const val REAR_LEFT_LOCK_STATE_FID = 1081081868
        private const val REAR_RIGHT_LOCK_STATE_FID = 1081081870
        private const val FRONT_LEFT_DOOR_OPEN_STATE_FID = 692060168
        private const val FRONT_RIGHT_DOOR_OPEN_STATE_FID = 692060170
        private const val REAR_LEFT_DOOR_OPEN_STATE_FID = 692060172
        private const val REAR_RIGHT_DOOR_OPEN_STATE_FID = 692060174
        private const val TRUNK_OPEN_STATE_FID = 1074790416
        private const val HOOD_OPEN_STATE_FID = 692060188
        private const val DRIVER_DOOR_OPEN_STATE = FRONT_LEFT_DOOR_OPEN_STATE_FID
    }
}
