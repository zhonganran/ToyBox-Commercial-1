package com.byd.wjx.helper

import android.content.Context
import java.io.File
import java.lang.reflect.Modifier

internal data class RemoteStandbyPowerResult(
    val applied: Boolean,
    val mcuStatus: Int,
    val summary: String,
)

internal enum class RemoteAccState {
    ON,
    OFF,
    UNKNOWN,
}

/** Empty and `0` are the normal ACC-on values used by BYD's shutdown animator. */
internal fun parseRemoteAccState(raw: String?): RemoteAccState = when {
    raw == null -> RemoteAccState.UNKNOWN
    raw.trim().isEmpty() || raw.trim() == "0" -> RemoteAccState.ON
    else -> RemoteAccState.OFF
}

/**
 * Shell-helper owner for parked remote-audio power.
 *
 * The app arms this controller while remote audio is eligible. The helper then watches
 * `sys.accanim.status` itself, so it can cast the three BYD power votes at the ACC-off edge
 * without depending on an OEM broadcast or on the Android app process surviving shutdown.
 */
internal class RemoteStandbyPowerController(
    private val systemContext: Context?,
) {
    private val marker = File(MARKER_PATH)
    @Volatile private var stopped = false
    @Volatile private var lastAppliedAtMs = 0L
    @Volatile private var active = false
    @Volatile private var lastApplySucceeded = true
    @Volatile private var lastAccState = RemoteAccState.UNKNOWN

    fun start() {
        Thread({ maintenanceLoop() }, "WJX-RemoteStandbyPower").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean, source: String): RemoteStandbyPowerResult {
        val markerOk = if (enabled) {
            runCatching {
                marker.parentFile?.mkdirs()
                marker.writeText("armedAt=${System.currentTimeMillis()}\n")
                marker.isFile
            }.onFailure {
                helperLog(TAG, "marker write failed: ${throwableSummary(it)}")
            }.getOrDefault(false)
        } else {
            runCatching { !marker.exists() || marker.delete() }
                .onFailure { helperLog(TAG, "marker delete failed: ${throwableSummary(it)}") }
                .getOrDefault(false)
        }

        val accState = readAccState()
        val powerResult = when {
            !enabled -> applyPowerState(enabled = false, source = source, accState = accState)
            accState == RemoteAccState.OFF -> applyPowerState(
                enabled = true,
                source = source,
                accState = accState,
            )
            active && accState == RemoteAccState.ON -> applyPowerState(
                enabled = false,
                source = source,
                accState = accState,
            )
            else -> RemoteStandbyPowerResult(
                applied = true,
                mcuStatus = readMcuStatus(),
                summary = "armed=true active=false acc=$accState source=$source",
            )
        }
        lastAccState = accState

        val applied = markerOk && powerResult.applied
        val summary = "${powerResult.summary} marker=$markerOk"
        helperLog(TAG, summary)
        return powerResult.copy(applied = applied, summary = summary)
    }

    private fun maintenanceLoop() {
        while (!stopped) {
            if (!isAppInstalled()) {
                if (marker.exists() || active) {
                    runCatching { marker.delete() }
                    applyPowerState(false, "package_removed", readAccState())
                }
                stopped = true
                return
            }

            val armed = marker.isFile
            val accState = readAccState()
            val desiredActive = when {
                !armed -> false
                accState == RemoteAccState.OFF -> true
                accState == RemoteAccState.ON -> false
                else -> active
            }
            val now = System.currentTimeMillis()
            val stateChanged = accState != lastAccState || desiredActive != active
            val retryDue = !lastApplySucceeded && now - lastAppliedAtMs >= FAILED_RETRY_INTERVAL_MS
            val reassertDue = desiredActive && now - lastAppliedAtMs >= REAPPLY_INTERVAL_MS

            if (stateChanged) {
                helperLog(
                    TAG,
                    "transition armed=$armed acc=$lastAccState->$accState active=$active->$desiredActive",
                )
            }
            if (stateChanged || retryDue || reassertDue) {
                applyPowerState(
                    enabled = desiredActive,
                    source = when {
                        stateChanged -> "acc_transition"
                        retryDue -> "retry"
                        else -> "maintenance"
                    },
                    accState = accState,
                )
            }
            lastAccState = accState

            try {
                Thread.sleep(ACC_POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    @Synchronized
    private fun applyPowerState(
        enabled: Boolean,
        source: String,
        accState: RemoteAccState,
    ): RemoteStandbyPowerResult {
        val context = systemContext
            ?: return RemoteStandbyPowerResult(false, MCU_STATUS_UNKNOWN, "system_context_unavailable")
        val special = resolveDevice(SPECIAL_DEVICE_CLASS, context)
        val power = resolveDevice(POWER_DEVICE_CLASS, context)
        var mcuStatus = readIntNoArg(power, "getMcuStatus") ?: MCU_STATUS_UNKNOWN
        val wakeResults = mutableListOf<Int>()

        if (enabled && mcuStatus !in MCU_READY_STATES) {
            for (attempt in 0 until MCU_WAKE_ATTEMPTS) {
                val wakeResult = readIntNoArg(power, "wakeUpMcu") ?: break
                wakeResults += wakeResult
                Thread.sleep(MCU_SETTLE_MS)
                mcuStatus = readIntNoArg(power, "getMcuStatus") ?: mcuStatus
                if (mcuStatus in MCU_READY_STATES) break
            }
        }

        val remoteModeOk = sendSet(special, REMOTE_POWER_MODE_ID, if (enabled) 1 else 0)
        val dataModuleOk = sendSet(special, DATA_MODULE_POWER_ID, if (enabled) 1 else 2)
        val mcuPowerHoldOk = sendSet(power, MCU_POWER_HOLD_ID, if (enabled) 1 else 0)
        val applied = remoteModeOk && dataModuleOk && mcuPowerHoldOk
        active = enabled
        lastApplySucceeded = applied
        lastAppliedAtMs = System.currentTimeMillis()
        val summary = buildString {
            append("armed=").append(marker.isFile)
            append(" active=").append(enabled)
            append(" acc=").append(accState)
            append(" remoteMode=").append(remoteModeOk)
            append(" dataModule=").append(dataModuleOk)
            append(" mcuPowerHold=").append(mcuPowerHoldOk)
            append(" mcuStatus=").append(mcuStatus)
            if (wakeResults.isNotEmpty()) append(" wakeResults=").append(wakeResults.joinToString(","))
            append(" source=").append(source)
        }
        helperLog(TAG, summary)
        return RemoteStandbyPowerResult(applied, mcuStatus, summary)
    }

    private fun readAccState(): RemoteAccState {
        val direct = runCatching {
            val type = Class.forName("android.os.SystemProperties")
            type.getMethod("get", String::class.java).invoke(null, ACC_STATUS_PROPERTY) as? String
        }.getOrNull()
        if (direct != null) return parseRemoteAccState(direct)

        val shell = runCatching { shExec("getprop \"\$1\"", ACC_STATUS_PROPERTY) }.getOrNull()
            ?: return RemoteAccState.UNKNOWN
        return if (shell.code == 0) parseRemoteAccState(shell.stdout) else RemoteAccState.UNKNOWN
    }

    private fun readMcuStatus(): Int {
        val context = systemContext ?: return MCU_STATUS_UNKNOWN
        return readIntNoArg(resolveDevice(POWER_DEVICE_CLASS, context), "getMcuStatus")
            ?: MCU_STATUS_UNKNOWN
    }

    private fun isAppInstalled(): Boolean {
        val context = systemContext ?: return true
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(HelperBinderProtocol.APP_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    private fun resolveDevice(className: String, context: Context): Any? {
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

    private fun sendSet(device: Any?, id: Int, value: Int): Boolean {
        device ?: return false
        return runCatching {
            val eventClass = Class.forName(EVENT_VALUE_CLASS)
            val event = eventClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            setIntField(eventClass, event, "intValue", value)
            runCatching { setIntField(eventClass, event, "valueType", 1) }
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
            helperLog(TAG, "set id=$id value=$value failed: ${throwableSummary(it)}")
        }.getOrDefault(false)
    }

    private fun setIntField(clazz: Class<*>, instance: Any, name: String, value: Int) {
        val field = runCatching { clazz.getField(name) }
            .getOrElse { clazz.getDeclaredField(name).apply { isAccessible = true } }
        field.setInt(instance, value)
    }

    private fun readIntNoArg(instance: Any?, methodName: String): Int? {
        instance ?: return null
        return runCatching {
            val method = (instance.javaClass.methods.asSequence() + instance.javaClass.declaredMethods.asSequence())
                .firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
                ?: return@runCatching null
            method.isAccessible = true
            (method.invoke(instance) as? Number)?.toInt()
        }.getOrNull()
    }

    companion object {
        const val MARKER_PATH = "/data/local/tmp/wjx_remote_standby.enabled"
        private const val TAG = "RemoteStandbyPower"
        private const val SPECIAL_DEVICE_CLASS = "android.hardware.bydauto.special.BYDAutoSpecialDevice"
        private const val POWER_DEVICE_CLASS = "android.hardware.bydauto.power.BYDAutoPowerDevice"
        private const val EVENT_VALUE_CLASS = "android.hardware.bydauto.BYDAutoEventValue"
        private const val ACC_STATUS_PROPERTY = "sys.accanim.status"
        private const val REMOTE_POWER_MODE_ID = 782237711
        private const val DATA_MODULE_POWER_ID = 782237728
        private const val MCU_POWER_HOLD_ID = -1442840502
        private const val MCU_STATUS_UNKNOWN = -1
        private val MCU_READY_STATES = setOf(1, 10)
        private const val MCU_WAKE_ATTEMPTS = 2
        private const val MCU_SETTLE_MS = 700L
        private const val ACC_POLL_INTERVAL_MS = 500L
        private const val FAILED_RETRY_INTERVAL_MS = 5_000L
        private const val REAPPLY_INTERVAL_MS = 30_000L
    }
}
