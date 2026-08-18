package com.byd.wjx.helper

import android.annotation.SuppressLint
import android.content.Context
import java.io.File
import java.lang.reflect.Modifier

/** Releases power votes left by versions that supported parked remote audio. */
@SuppressLint("PrivateApi")
internal fun clearRemovedPowerVotes(systemContext: Context?) {
    val marker = File(LEGACY_MARKER_PATH)
    if (!marker.isFile) return
    val context = systemContext ?: run {
        helperLog(TAG, "legacy marker found but system context is unavailable")
        return
    }

    val special = resolveLegacyDevice(SPECIAL_DEVICE_CLASS, context)
    val power = resolveLegacyDevice(POWER_DEVICE_CLASS, context)
    val released = listOf(
        sendLegacySet(special, REMOTE_POWER_MODE_ID, 0),
        sendLegacySet(special, DATA_MODULE_POWER_ID, 2),
        sendLegacySet(power, MCU_POWER_HOLD_ID, 0),
    ).all { it }
    val markerRemoved = released && runCatching { marker.delete() }.getOrDefault(false)
    helperLog(TAG, "released=$released markerRemoved=$markerRemoved")
}

private fun resolveLegacyDevice(className: String, context: Context): Any? {
    val clazz = runCatching { Class.forName(className) }.getOrNull() ?: return null
    buildBodyworkContextCandidates(context).forEach { candidate ->
        val method = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
            .firstOrNull {
                it.name == "getInstance" &&
                    Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].isAssignableFrom(candidate.javaClass)
            } ?: return@forEach
        val instance = runCatching {
            method.isAccessible = true
            method.invoke(null, candidate)
        }.getOrNull()
        if (instance != null) return instance
    }
    return null
}

private fun sendLegacySet(device: Any?, id: Int, value: Int): Boolean {
    device ?: return false
    return runCatching {
        val eventClass = Class.forName(EVENT_VALUE_CLASS)
        val event = eventClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        setLegacyIntField(eventClass, event, "intValue", value)
        runCatching { setLegacyIntField(eventClass, event, "valueType", 1) }
        val method = (device.javaClass.methods.asSequence() + device.javaClass.declaredMethods.asSequence())
            .firstOrNull {
                it.name == "set" &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == IntArray::class.java &&
                    it.parameterTypes[1].isAssignableFrom(eventClass)
            } ?: error("set(int[], BYDAutoEventValue) unavailable")
        method.isAccessible = true
        when (val result = method.invoke(device, intArrayOf(id), event)) {
            is Boolean -> result
            is Number -> result.toInt() >= 0
            else -> true
        }
    }.onFailure {
        helperLog(TAG, "release id=$id failed: ${throwableSummary(it)}")
    }.getOrDefault(false)
}

private fun setLegacyIntField(clazz: Class<*>, instance: Any, name: String, value: Int) {
    val field = runCatching { clazz.getField(name) }
        .getOrElse { clazz.getDeclaredField(name).apply { isAccessible = true } }
    field.setInt(instance, value)
}

private const val LEGACY_MARKER_PATH = "/data/local/tmp/wjx_remote_standby.enabled"
private const val TAG = "LegacyPowerCleanup"
private const val SPECIAL_DEVICE_CLASS = "android.hardware.bydauto.special.BYDAutoSpecialDevice"
private const val POWER_DEVICE_CLASS = "android.hardware.bydauto.power.BYDAutoPowerDevice"
private const val EVENT_VALUE_CLASS = "android.hardware.bydauto.BYDAutoEventValue"
private const val REMOTE_POWER_MODE_ID = 782237711
private const val DATA_MODULE_POWER_ID = 782237728
private const val MCU_POWER_HOLD_ID = -1442840502
