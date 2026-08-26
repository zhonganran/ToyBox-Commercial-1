package com.byd.wjx.data.system

import com.byd.wjx.data.vehicle.HelperClient
import com.byd.wjx.log.WjxFileLogger
import java.io.File

/** Bounded text access for procfs/sysfs with helper fallback only where it is supported. */
internal class SystemMetricsTextSource(
    private val helperClient: HelperClient,
) {
    private val loggedHelperFallbacks = mutableSetOf<String>()

    fun read(path: String): String? = runCatching {
        val file = File(path)
        if (!file.isFile || !file.canRead()) return@runCatching null
        file.readText().take(MAX_TEXT_CHARS)
    }.getOrNull()

    fun readLong(path: String): Long? = read(path)?.trim()?.toLongOrNull()

    suspend fun readCpuTimes(): Map<String, CpuTimes> {
        val direct = read(SystemMetricPaths.PROC_STAT)?.let(::parseCpuStat).orEmpty()
        if (direct.isNotEmpty()) return direct
        return readHelperText(SystemMetricPaths.PROC_STAT)?.let(::parseCpuStat).orEmpty()
    }

    suspend fun readLoadAverage(): List<Double?> {
        val direct = read(SystemMetricPaths.PROC_LOADAVG)?.let(::parseLoadAverage).orEmpty()
        if (direct.any { it != null }) return direct
        return readHelperText(SystemMetricPaths.PROC_LOADAVG)
            ?.let(::parseLoadAverage)
            .orEmpty()
    }

    suspend fun readUptimeSeconds(): Long? {
        val text = read(SystemMetricPaths.PROC_UPTIME)
            ?: readHelperText(SystemMetricPaths.PROC_UPTIME)
        return text
            ?.trim()
            ?.substringBefore(' ')
            ?.toDoubleOrNull()
            ?.toLong()
    }

    private suspend fun readHelperText(path: String): String? {
        val command = when (path) {
            SystemMetricPaths.PROC_STAT -> "cat /proc/stat"
            SystemMetricPaths.PROC_LOADAVG -> "cat /proc/loadavg"
            SystemMetricPaths.PROC_UPTIME -> "cat /proc/uptime"
            else -> return null
        }
        val result = helperClient.execShellResult(command)?.takeIf { it.ok } ?: return null
        if (loggedHelperFallbacks.add(path)) {
            WjxFileLogger.i(TAG, "Using shell helper fallback for $path")
        }
        return result.output.trim().take(MAX_TEXT_CHARS).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "SystemMetricsReader"
        const val MAX_TEXT_CHARS = 256 * 1_024
    }
}

internal object SystemMetricPaths {
    const val PROC_STAT = "/proc/stat"
    const val PROC_CPUINFO = "/proc/cpuinfo"
    const val PROC_MEMINFO = "/proc/meminfo"
    const val PROC_DISKSTATS = "/proc/diskstats"
    const val PROC_LOADAVG = "/proc/loadavg"
    const val PROC_UPTIME = "/proc/uptime"
    const val PROC_SELF_STATUS = "/proc/self/status"
    const val PROC_SELF_FD = "/proc/self/fd"
    const val SYS_CPU_ROOT = "/sys/devices/system/cpu"
    const val SYS_THERMAL_ROOT = "/sys/class/thermal"
    const val SYS_ZRAM_DISK_SIZE = "/sys/block/zram0/disksize"
}
