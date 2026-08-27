package com.byd.wjx.helper

import android.annotation.SuppressLint
import android.content.Context
import com.byd.wjx.data.vehicle.MirrorPosition
import java.lang.reflect.Method

/** Reflection-only bridge to the BYD bodywork geometry API shipped on the vehicle. */
@SuppressLint("PrivateApi")
internal object HelperMirrorPlatform {
    fun readPosition(systemContext: Context?): MirrorPosition? {
        val context = systemContext ?: return null
        return withBodyworkDevice(context) { device ->
            MirrorPosition(
                leftHorizontal = readAxis(device, FID_LEFT_HORIZONTAL_READ) ?: return@withBodyworkDevice null,
                leftVertical = readAxis(device, FID_LEFT_VERTICAL_READ) ?: return@withBodyworkDevice null,
                rightHorizontal = readAxis(device, FID_RIGHT_HORIZONTAL_READ) ?: return@withBodyworkDevice null,
                rightVertical = readAxis(device, FID_RIGHT_VERTICAL_READ) ?: return@withBodyworkDevice null,
            ).takeIf(MirrorPosition::isSane)
        }
    }

    fun applyPosition(systemContext: Context?, position: MirrorPosition): Boolean {
        if (!position.isSane()) return false
        val context = systemContext ?: return false
        return withBodyworkDevice(context) { device ->
            // The mirror linkage batch also contains both steering-column axes. Never invent a
            // filler value here: on a car with an electric column, a guessed value could move it.
            // Cars without that hardware normally report the OEM 127.5 sentinel themselves.
            val steeringHorizontal = readAxis(device, FID_STEERING_HORIZONTAL_READ)
                ?: return@withBodyworkDevice false
            val steeringVertical = readAxis(device, FID_STEERING_VERTICAL_READ)
                ?: return@withBodyworkDevice false
            val ids = intArrayOf(
                FID_LEFT_HORIZONTAL_WRITE,
                FID_LEFT_VERTICAL_WRITE,
                FID_RIGHT_HORIZONTAL_WRITE,
                FID_RIGHT_VERTICAL_WRITE,
                FID_STEERING_HORIZONTAL_WRITE,
                FID_STEERING_VERTICAL_WRITE,
            )
            val values = floatArrayOf(
                position.leftHorizontal,
                position.leftVertical,
                position.rightHorizontal,
                position.rightVertical,
                steeringHorizontal,
                steeringVertical,
            )
            invokeFloatBatch(device, ids, values)
        } ?: false
    }

    private inline fun <T> withBodyworkDevice(context: Context, block: (Any) -> T?): T? {
        val clazz = runCatching { Class.forName(BODYWORK_DEVICE_CLASS) }
            .onFailure { helperLog(TAG, "bodywork class unavailable: ${throwableSummary(it)}") }
            .getOrNull() ?: return null
        val diagnostics = StringBuilder()
        for (candidate in buildBodyworkContextCandidates(context)) {
            val device = createBydBodyworkInstance(clazz, candidate, diagnostics) ?: continue
            installDeviceContext(device, candidate)
            runCatching { block(device) }
                .onFailure { helperLog(TAG, "bodywork call failed: ${throwableSummary(it)}") }
                .getOrNull()
                ?.let { return it }
        }
        if (diagnostics.isNotEmpty()) helperLog(TAG, diagnostics.toString().lineSequence().last().take(240))
        return null
    }

    private fun readAxis(device: Any, fid: Int): Float? {
        val getMethod = findMethod(device.javaClass, "get", intArrayOf()::class.java, Class::class.java)
            ?: return null
        val event = getMethod.invoke(device, intArrayOf(fid), Double::class.javaPrimitiveType)
            ?: return null
        val value = extractDouble(event)?.toFloat() ?: return null
        return value.takeIf { it.isFinite() && it in MirrorPosition.AXIS_MIN..MirrorPosition.AXIS_MAX }
    }

    private fun invokeFloatBatch(device: Any, ids: IntArray, values: FloatArray): Boolean {
        val eventClass = Class.forName(EVENT_VALUE_CLASS)
        val event = eventClass.getDeclaredConstructor().newInstance()
        eventClass.getField("floatArrayValue").set(event, values)
        val setMethod = findMethod(device.javaClass, "set", IntArray::class.java, eventClass)
            ?: return false
        val result = setMethod.invoke(device, ids, event)
        val accepted = when (result) {
            is Number -> result.toInt() == 0
            is Boolean -> result
            else -> false
        }
        helperLog(TAG, "mirror batch count=${ids.size} accepted=$accepted result=${result ?: "null"}")
        return accepted
    }

    private fun extractDouble(value: Any): Double? {
        if (value is Number) return value.toDouble()
        runCatching { value.javaClass.getField("doubleValue").getDouble(value) }
            .getOrNull()
            ?.takeIf { it.isFinite() && it > INVALID_DOUBLE_FLOOR }
            ?.let { return it }
        return runCatching { value.javaClass.getField("floatValue").getFloat(value).toDouble() }
            .getOrNull()
            ?.takeIf { it.isFinite() && it > INVALID_DOUBLE_FLOOR }
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg params: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredMethod(name, *params).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun installDeviceContext(device: Any, context: Context) {
        val permissive = if (context is PermissionBypassContext) context else PermissionBypassContext(context)
        var current: Class<*>? = device.javaClass
        while (current != null && current != Any::class.java) {
            try {
                current.getDeclaredField("mContext").apply {
                    isAccessible = true
                    set(device, permissive)
                }
                return
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            } catch (error: Throwable) {
                helperLog(TAG, "context install failed: ${throwableSummary(error)}")
                return
            }
        }
    }

    private const val TAG = "MirrorPosition"
    private const val BODYWORK_DEVICE_CLASS =
        "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
    private const val EVENT_VALUE_CLASS = "android.hardware.bydauto.BYDAutoEventValue"
    private const val INVALID_DOUBLE_FLOOR = -1000.0
    private const val FID_LEFT_HORIZONTAL_READ = 0x4B300188
    private const val FID_LEFT_VERTICAL_READ = 0x4B300190
    private const val FID_RIGHT_HORIZONTAL_READ = 0x4BB00110
    private const val FID_RIGHT_VERTICAL_READ = 0x4BB00118
    private const val FID_STEERING_HORIZONTAL_READ = 0x4B300198
    private const val FID_STEERING_VERTICAL_READ = 0x4B3001A0

    private const val FID_LEFT_HORIZONTAL_WRITE = 0x4C116010
    private const val FID_LEFT_VERTICAL_WRITE = 0x4C116018
    private const val FID_RIGHT_HORIZONTAL_WRITE = 0x4C116020
    private const val FID_RIGHT_VERTICAL_WRITE = 0x4C116028
    private const val FID_STEERING_HORIZONTAL_WRITE = 0x4C116030
    private const val FID_STEERING_VERTICAL_WRITE = 0x4C116038
}
