package com.byd.wjx.helper

import android.content.Context

/** Shell-helper bridge to BYD's system-wide display rotation API. */
internal class BydSystemRotationController(
    private val systemContext: Context?,
) {
    private val lock = Any()
    private var device: Any? = null

    fun forceLandscape(): Boolean = synchronized(lock) {
        invokeNoArg("forceSystemLandscape")
    }

    fun restoreRotation(): Boolean = synchronized(lock) {
        invokeNoArg("restoreRotation")
    }

    fun rotationState(): Int? = synchronized(lock) {
        val target = resolveDevice() ?: return@synchronized null
        runCatching {
            (target.javaClass.getMethod("getRotationState").invoke(target) as? Number)?.toInt()
        }.onFailure {
            helperLog(TAG, "getRotationState failed: ${throwableSummary(it)}")
            device = null
        }.getOrNull()
    }

    private fun invokeNoArg(methodName: String): Boolean {
        val target = resolveDevice() ?: return false
        return runCatching {
            target.javaClass.getMethod(methodName).invoke(target)
            helperLog(TAG, "$methodName done")
            true
        }.onFailure {
            helperLog(TAG, "$methodName failed: ${throwableSummary(it)}")
            device = null
        }.getOrDefault(false)
    }

    private fun resolveDevice(): Any? {
        device?.let { return it }
        val context = systemContext ?: run {
            helperLog(TAG, "system context unavailable")
            return null
        }
        return runCatching {
            val clazz = Class.forName(SETTING_DEVICE_CLASS)
            clazz.getMethod("getInstance", Context::class.java).invoke(null, context)
        }.onFailure {
            helperLog(TAG, "device init failed: ${throwableSummary(it)}")
        }.getOrNull()?.also { device = it }
    }

    private companion object {
        const val TAG = "SystemRotation"
        const val SETTING_DEVICE_CLASS = "android.hardware.bydauto.setting.BYDAutoSettingDevice"
    }
}
