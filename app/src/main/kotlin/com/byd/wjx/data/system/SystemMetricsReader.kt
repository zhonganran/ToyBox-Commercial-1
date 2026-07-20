package com.byd.wjx.data.system

import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

data class CpuCoreMetric(
    val index: Int,
    val usagePercent: Int?,
    val frequencyMhz: Int?,
    val governor: String?,
)

data class MemoryMetrics(
    val totalMb: Long,
    val availableMb: Long,
    val usedPercent: Int,
    val cachedMb: Long?,
    val buffersMb: Long?,
    val activeMb: Long?,
    val inactiveMb: Long?,
    val dirtyMb: Long?,
    val slabMb: Long?,
    val swapTotalMb: Long?,
    val swapFreeMb: Long?,
    val zramMb: Long?,
    val cmaMb: Long?,
)

data class StorageVolumeMetric(
    val label: String,
    val totalGb: Double,
    val freeGb: Double,
    val usedPercent: Int,
)

data class DiskIoMetric(
    val device: String,
    val readMb: Long,
    val writeMb: Long,
    val readMbPerSec: Double?,
    val writeMbPerSec: Double?,
)

data class GpuReading(
    val label: String,
    val value: String,
)

data class ThermalReading(
    val name: String,
    val celsius: Float,
)

data class DeviceMetrics(
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val product: String,
    val device: String,
    val board: String,
    val hardware: String,
    val socModel: String?,
    val abis: String,
)

data class AppProcessMetrics(
    val pid: Int,
    val threadCount: Int?,
    val vmRssMb: Long?,
    val vmSizeMb: Long?,
    val openFdCount: Int?,
    val jvmHeapUsedMb: Long,
    val jvmHeapMaxMb: Long,
)

data class SystemMetricsSnapshot(
    val capturedAtMs: Long,
    val loadAverage1: Double?,
    val loadAverage5: Double?,
    val loadAverage15: Double?,
    val uptimeSeconds: Long?,
    val cpuTotalUsagePercent: Int?,
    val cpuCoreCount: Int,
    val cpuModelName: String?,
    val cpuCores: List<CpuCoreMetric>,
    val memory: MemoryMetrics?,
    val storageVolumes: List<StorageVolumeMetric>,
    val diskIo: List<DiskIoMetric>,
    val gpuReadings: List<GpuReading>,
    val thermalZones: List<ThermalReading>,
    val device: DeviceMetrics,
    val appProcess: AppProcessMetrics,
)

internal data class CpuTimes(
    val total: Long,
    val idle: Long,
)

internal data class DiskCounters(
    val device: String,
    val readSectors: Long,
    val writeSectors: Long,
)

@Singleton
class SystemMetricsReader @Inject constructor() {
    private val sampleMutex = Mutex()
    private val cpuModelName by lazy(LazyThreadSafetyMode.NONE) { readCpuModelName() }
    private val deviceMetrics by lazy(LazyThreadSafetyMode.NONE) { readDeviceMetrics() }
    private var previousCpuTimes: Map<String, CpuTimes>? = null
    private var previousDiskCounters: Map<String, DiskCounters>? = null
    private var previousSampleAtMs: Long? = null

    suspend fun readSnapshot(): SystemMetricsSnapshot = withContext(Dispatchers.IO) {
        sampleMutex.withLock { readSnapshotLocked() }
    }

    private fun readSnapshotLocked(): SystemMetricsSnapshot {
        val capturedAtMs = System.currentTimeMillis()
        val cpuTimes = readSmallText(PROC_STAT)?.let(::parseCpuStat).orEmpty()
        val cpuUsage = calculateCpuUsage(previousCpuTimes, cpuTimes)
        val diskCounters = readSmallText(PROC_DISKSTATS)?.let(::parseDiskStats).orEmpty()
        val diskIo = calculateDiskIo(
            previous = previousDiskCounters,
            current = diskCounters,
            elapsedMs = previousSampleAtMs?.let { capturedAtMs - it },
        )
        previousCpuTimes = cpuTimes
        previousDiskCounters = diskCounters
        previousSampleAtMs = capturedAtMs

        val memory = readSmallText(PROC_MEMINFO)
            ?.let(::parseMemInfo)
            ?.let { parsed ->
                val zramMb = readLongFile(SYS_ZRAM_DISK_SIZE)
                    ?.takeIf { it > 0L }
                    ?.div(BYTES_PER_MB)
                if (zramMb != null) parsed.copy(zramMb = zramMb) else parsed
            }
        val loadAverage = readSmallText(PROC_LOADAVG)
            ?.trim()
            ?.split(WHITESPACE_REGEX)
            .orEmpty()
            .take(3)
            .map { it.toDoubleOrNull() }
        val uptimeSeconds = readSmallText(PROC_UPTIME)
            ?.trim()
            ?.substringBefore(' ')
            ?.toDoubleOrNull()
            ?.toLong()

        return SystemMetricsSnapshot(
            capturedAtMs = capturedAtMs,
            loadAverage1 = loadAverage.getOrNull(0),
            loadAverage5 = loadAverage.getOrNull(1),
            loadAverage15 = loadAverage.getOrNull(2),
            uptimeSeconds = uptimeSeconds,
            cpuTotalUsagePercent = cpuUsage[CPU_TOTAL],
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            cpuModelName = cpuModelName,
            cpuCores = readCpuCores(cpuTimes.keys, cpuUsage),
            memory = memory,
            storageVolumes = readStorageVolumes(),
            diskIo = diskIo,
            gpuReadings = readGpuReadings(),
            thermalZones = readThermalZones(),
            device = deviceMetrics,
            appProcess = readAppProcessMetrics(),
        )
    }

    private fun readCpuModelName(): String? {
        val values = readSmallText(PROC_CPUINFO)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
            ?.toList()
            .orEmpty()
        return listOf("Hardware", "model name", "Processor")
            .firstNotNullOfOrNull { key -> values.firstOrNull { it.first == key }?.second }
            ?.takeIf { it.isNotBlank() }
    }

    private fun readCpuCores(
        cpuNames: Set<String>,
        usage: Map<String, Int>,
    ): List<CpuCoreMetric> = cpuNames
        .asSequence()
        .filter { it.matches(CPU_CORE_REGEX) }
        .mapNotNull { name -> name.removePrefix("cpu").toIntOrNull()?.let { it to name } }
        .sortedBy { it.first }
        .map { (index, name) ->
            val base = File(SYS_CPU_ROOT, name)
            CpuCoreMetric(
                index = index,
                usagePercent = usage[name],
                frequencyMhz = readCpuFrequencyMhz(base),
                governor = readSmallText(File(base, "cpufreq/scaling_governor").absolutePath)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
            )
        }
        .toList()

    private fun readCpuFrequencyMhz(base: File): Int? {
        val raw = listOf(
            File(base, "cpufreq/scaling_cur_freq"),
            File(base, "cpufreq/cpuinfo_cur_freq"),
        ).firstNotNullOfOrNull { readLongFile(it.absolutePath) } ?: return null
        return when {
            raw >= 100_000_000L -> (raw / 1_000_000L).toInt()
            raw >= 100_000L -> (raw / 1_000L).toInt()
            else -> raw.toInt()
        }.takeIf { it > 0 }
    }

    @Suppress("DEPRECATION")
    private fun readStorageVolumes(): List<StorageVolumeMetric> {
        val roots = linkedMapOf(
            "data" to Environment.getDataDirectory(),
            "root" to File("/"),
            "cache" to Environment.getDownloadCacheDirectory(),
            "external" to Environment.getExternalStorageDirectory(),
        )
        val seenPaths = mutableSetOf<String>()
        return roots.mapNotNull { (label, file) ->
            val path = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            if (!seenPaths.add(path)) return@mapNotNull null
            runCatching {
                val stat = StatFs(path)
                val total = stat.totalBytes
                val free = stat.availableBytes
                if (total <= 0L) return@runCatching null
                StorageVolumeMetric(
                    label = label,
                    totalGb = total.toDouble() / BYTES_PER_GB,
                    freeGb = free.toDouble() / BYTES_PER_GB,
                    usedPercent = (((total - free).coerceAtLeast(0L) * 100.0) / total)
                        .roundToInt()
                        .coerceIn(0, 100),
                )
            }.getOrNull()
        }
    }

    private fun readGpuReadings(): List<GpuReading> = GPU_NODES.mapNotNull { (path, label) ->
        val raw = readSmallText(path)?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        GpuReading(label = label, value = formatGpuValue(label, raw))
    }

    private fun formatGpuValue(label: String, raw: String): String {
        val value = raw.substringBefore(' ').toLongOrNull() ?: return raw.take(32)
        return if (label.contains("freq", ignoreCase = true)) {
            val mhz = when {
                value >= 100_000_000L -> value / 1_000_000L
                value >= 100_000L -> value / 1_000L
                else -> value
            }
            "$mhz MHz"
        } else if (value in 0..100) {
            "$value%"
        } else {
            raw.take(32)
        }
    }

    private fun readThermalZones(): List<ThermalReading> {
        val root = File(SYS_THERMAL_ROOT)
        val zones = root.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name.matches(THERMAL_ZONE_REGEX) }
            ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
        return zones.mapNotNull { zone ->
            val raw = readLongFile(File(zone, "temp").absolutePath) ?: return@mapNotNull null
            val celsius = normalizeTemperature(raw) ?: return@mapNotNull null
            val name = readSmallText(File(zone, "type").absolutePath)
                ?.trim()
                ?.take(32)
                ?.takeIf { it.isNotBlank() }
                ?: zone.name
            ThermalReading(name = name, celsius = celsius)
        }.take(MAX_THERMAL_ZONES).toList()
    }

    private fun readDeviceMetrics(): DeviceMetrics = DeviceMetrics(
        androidVersion = Build.VERSION.RELEASE.orEmpty(),
        apiLevel = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        model = Build.MODEL.orEmpty(),
        product = Build.PRODUCT.orEmpty(),
        device = Build.DEVICE.orEmpty(),
        board = Build.BOARD.orEmpty(),
        hardware = Build.HARDWARE.orEmpty(),
        socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() }
        } else {
            null
        },
        abis = Build.SUPPORTED_ABIS.joinToString(),
    )

    private fun readAppProcessMetrics(): AppProcessMetrics {
        val status = readSmallText(PROC_SELF_STATUS)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                line.substring(0, separator) to line.substring(separator + 1).trim()
            }
            ?.toMap()
            .orEmpty()
        val runtime = Runtime.getRuntime()
        return AppProcessMetrics(
            pid = android.os.Process.myPid(),
            threadCount = status["Threads"]?.toIntOrNull(),
            vmRssMb = status["VmRSS"]?.parseKilobytes()?.div(KB_PER_MB),
            vmSizeMb = status["VmSize"]?.parseKilobytes()?.div(KB_PER_MB),
            openFdCount = runCatching { File(PROC_SELF_FD).listFiles()?.size }.getOrNull(),
            jvmHeapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
            jvmHeapMaxMb = runtime.maxMemory() / BYTES_PER_MB,
        )
    }

    private fun readSmallText(path: String): String? = runCatching {
        val file = File(path)
        if (!file.isFile || !file.canRead()) return@runCatching null
        file.readText().take(MAX_TEXT_CHARS)
    }.getOrNull()

    private fun readLongFile(path: String): Long? = readSmallText(path)?.trim()?.toLongOrNull()

    private companion object {
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
        const val CPU_TOTAL = "cpu"
        const val KB_PER_MB = 1024L
        const val BYTES_PER_MB = 1024L * 1024L
        const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
        const val MAX_TEXT_CHARS = 256 * 1024
        const val MAX_THERMAL_ZONES = 24

        val WHITESPACE_REGEX = Regex("\\s+")
        val CPU_CORE_REGEX = Regex("cpu\\d+")
        val THERMAL_ZONE_REGEX = Regex("thermal_zone\\d+")
        val GPU_NODES = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage" to "GPU 占用",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq" to "GPU 频率",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq" to "GPU 最高频率",
            "/sys/devices/platform/mali.0/utilisation" to "Mali 占用",
            "/sys/devices/platform/gpusysfs/gpu_utilization" to "GPU 占用",
            "/sys/kernel/debug/mali0/utilization" to "Mali 占用",
        )
    }
}

internal fun parseMemInfo(text: String): MemoryMetrics? {
    val values = text.lineSequence().mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@mapNotNull null
        val key = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim().parseKilobytes() ?: return@mapNotNull null
        key to value
    }.toMap()
    val totalKb = values["MemTotal"] ?: return null
    if (totalKb <= 0L) return null
    val availableKb = (values["MemAvailable"]
        ?: listOf("MemFree", "Buffers", "Cached").sumOf { values[it] ?: 0L })
        .coerceIn(0L, totalKb)
    fun mb(key: String): Long? = values[key]?.div(1024L)
    return MemoryMetrics(
        totalMb = totalKb / 1024L,
        availableMb = availableKb / 1024L,
        usedPercent = (((totalKb - availableKb).coerceAtLeast(0L) * 100.0) / totalKb)
            .roundToInt()
            .coerceIn(0, 100),
        cachedMb = mb("Cached"),
        buffersMb = mb("Buffers"),
        activeMb = mb("Active"),
        inactiveMb = mb("Inactive"),
        dirtyMb = mb("Dirty"),
        slabMb = mb("Slab"),
        swapTotalMb = mb("SwapTotal"),
        swapFreeMb = mb("SwapFree"),
        zramMb = mb("Zram"),
        cmaMb = mb("CmaTotal"),
    )
}

internal fun parseCpuStat(text: String): Map<String, CpuTimes> = buildMap {
    text.lineSequence().forEach { line ->
        val fields = line.trim().split(Regex("\\s+"))
        val name = fields.firstOrNull() ?: return@forEach
        if (name != "cpu" && !name.matches(Regex("cpu\\d+"))) return@forEach
        val counters = fields.drop(1).mapNotNull { it.toLongOrNull() }
        if (counters.size < 4) return@forEach
        val idle = counters[3] + counters.getOrElse(4) { 0L }
        put(name, CpuTimes(total = counters.sum(), idle = idle))
    }
}

internal fun calculateCpuUsage(
    previous: Map<String, CpuTimes>?,
    current: Map<String, CpuTimes>,
): Map<String, Int> {
    if (previous == null) return emptyMap()
    return current.mapNotNull { (name, now) ->
        val before = previous[name] ?: return@mapNotNull null
        val totalDelta = now.total - before.total
        val idleDelta = now.idle - before.idle
        if (totalDelta <= 0L) return@mapNotNull null
        val used = ((totalDelta - idleDelta).coerceAtLeast(0L) * 100.0 / totalDelta)
            .roundToInt()
            .coerceIn(0, 100)
        name to used
    }.toMap()
}

internal fun parseDiskStats(text: String): Map<String, DiskCounters> = buildMap {
    text.lineSequence().forEach { line ->
        val fields = line.trim().split(Regex("\\s+"))
        if (fields.size < 10) return@forEach
        val device = fields[2]
        if (!isWholeDiskDevice(device)) return@forEach
        val readSectors = fields[5].toLongOrNull() ?: return@forEach
        val writeSectors = fields[9].toLongOrNull() ?: return@forEach
        put(device, DiskCounters(device, readSectors, writeSectors))
    }
}

internal fun calculateDiskIo(
    previous: Map<String, DiskCounters>?,
    current: Map<String, DiskCounters>,
    elapsedMs: Long?,
): List<DiskIoMetric> {
    val elapsedSeconds = elapsedMs?.takeIf { it > 0L }?.div(1000.0)
    return current.values.map { now ->
        val before = previous?.get(now.device)
        val readDelta = before?.let { (now.readSectors - it.readSectors).coerceAtLeast(0L) }
        val writeDelta = before?.let { (now.writeSectors - it.writeSectors).coerceAtLeast(0L) }
        DiskIoMetric(
            device = now.device,
            readMb = now.readSectors * 512L / (1024L * 1024L),
            writeMb = now.writeSectors * 512L / (1024L * 1024L),
            readMbPerSec = if (elapsedSeconds != null && readDelta != null) {
                readDelta * 512.0 / (1024.0 * 1024.0) / elapsedSeconds
            } else {
                null
            },
            writeMbPerSec = if (elapsedSeconds != null && writeDelta != null) {
                writeDelta * 512.0 / (1024.0 * 1024.0) / elapsedSeconds
            } else {
                null
            },
        )
    }.sortedBy { it.device }
}

private fun String.parseKilobytes(): Long? = trim()
    .substringBefore(' ')
    .toLongOrNull()

private fun isWholeDiskDevice(device: String): Boolean = when {
    device.startsWith("ram") -> false
    device.startsWith("loop") -> false
    device.startsWith("dm-") -> false
    device.matches(Regex("mmcblk\\d+p\\d+")) -> false
    device.matches(Regex("nvme\\d+n\\d+p\\d+")) -> false
    device.matches(Regex("sd[a-z]+\\d+")) -> false
    else -> true
}

private fun normalizeTemperature(raw: Long): Float? {
    val value = when {
        raw >= 10_000L || raw <= -10_000L -> raw / 1000f
        raw >= 1_000L || raw <= -1_000L -> raw / 100f
        raw >= 200L || raw <= -200L -> raw / 10f
        else -> raw.toFloat()
    }
    return value.takeIf { it in -50f..200f }
}
