package com.byd.wjx.helper

import android.content.Context
import android.os.IBinder

internal class WjxLockSoundSignalReader(
    private val svc: IBinder,
    private val autoIface: String,
    private val systemContext: Context?,
) {
    private val sentryEvidence = WjxSentryEvidenceReader(readVehicleValue = ::readOne)

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

    fun readSentryWarning(): SentryWarningReading = sentryEvidence.readWarning()

    fun isSentryGuarding(): Boolean = sentryEvidence.isGuarding()

    fun consumeSentryAlarmTimestamp(value: String, allowTrigger: Boolean): Boolean =
        sentryEvidence.consumeAlarmTimestamp(value, allowTrigger)

    fun readFreshSentryLogMarker(allowTrigger: Boolean): String =
        sentryEvidence.readFreshLogMarker(allowTrigger)

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

    fun readSentryAlarmTimestamp(): String = sentryEvidence.readAlarmTimestamp()

    private fun isUsefulVehicleValue(value: Int): Boolean =
        value != 0x0000FFFF && value != Int.MAX_VALUE && value != Int.MIN_VALUE

    private companion object {
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
