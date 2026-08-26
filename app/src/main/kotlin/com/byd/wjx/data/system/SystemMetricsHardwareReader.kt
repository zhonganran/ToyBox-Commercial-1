package com.byd.wjx.data.system

import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import kotlin.math.roundToInt

/** Reads non-differential hardware, storage, thermal, and process metrics. */
internal class SystemMetricsHardwareReader(
    private val textSource: SystemMetricsTextSource,
) {
    val cpuModelName: String? by lazy(LazyThreadSafetyMode.NONE) { readCpuModelName() }
    val deviceMetrics: DeviceMetrics by lazy(LazyThreadSafetyMode.NONE) { readDeviceMetrics() }

    fun readMemory(): MemoryMetrics? {
        val parsed = textSource.read(SystemMetricPaths.PROC_MEMINFO)
            ?.let(::parseMemInfo)
            ?: return null
        val zramMb = textSource.readLong(SystemMetricPaths.SYS_ZRAM_DISK_SIZE)
            ?.takeIf { it > 0L }
            ?.div(BYTES_PER_MB)
        return if (zramMb != null) parsed.copy(zramMb = zramMb) else parsed
    }

    fun readCpuCores(
        cpuNames: Set<String>,
        usage: Map<String, Int>,
    ): List<CpuCoreMetric> = cpuNames
        .asSequence()
        .filter { it.matches(CPU_CORE_REGEX) }
        .mapNotNull { name -> name.removePrefix(CPU_TOTAL).toIntOrNull()?.let { it to name } }
        .sortedBy { it.first }
        .map { (index, name) ->
            val base = File(SystemMetricPaths.SYS_CPU_ROOT, name)
            CpuCoreMetric(
                index = index,
                usagePercent = usage[name],
                frequencyMhz = readCpuFrequencyMhz(base),
                governor = textSource.read(File(base, "cpufreq/scaling_governor").absolutePath)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
            )
        }
        .toList()

    @Suppress("DEPRECATION")
    fun readStorageVolumes(): List<StorageVolumeMetric> {
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

    fun readGpuReadings(): List<GpuReading> = GPU_NODES.mapNotNull { (path, label) ->
        val raw = textSource.read(path)?.trim()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        GpuReading(label = label, value = formatGpuValue(label, raw))
    }

    fun readThermalZones(): List<ThermalReading> {
        val zones = File(SystemMetricPaths.SYS_THERMAL_ROOT).listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name.matches(THERMAL_ZONE_REGEX) }
            ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
        return zones.mapNotNull { zone ->
            val raw = textSource.readLong(File(zone, "temp").absolutePath)
                ?: return@mapNotNull null
            val celsius = normalizeTemperature(raw) ?: return@mapNotNull null
            val name = textSource.read(File(zone, "type").absolutePath)
                ?.trim()
                ?.take(32)
                ?.takeIf { it.isNotBlank() }
                ?: zone.name
            ThermalReading(name = name, celsius = celsius)
        }.take(MAX_THERMAL_ZONES).toList()
    }

    fun readAppProcessMetrics(): AppProcessMetrics {
        val status = textSource.read(SystemMetricPaths.PROC_SELF_STATUS)
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
            openFdCount = runCatching {
                File(SystemMetricPaths.PROC_SELF_FD).listFiles()?.size
            }.getOrNull(),
            jvmHeapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
            jvmHeapMaxMb = runtime.maxMemory() / BYTES_PER_MB,
        )
    }

    private fun readCpuModelName(): String? {
        val values = textSource.read(SystemMetricPaths.PROC_CPUINFO)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
            ?.toList()
            .orEmpty()
        return CPU_MODEL_KEYS.firstNotNullOfOrNull { key ->
            values.firstOrNull { it.first == key }?.second
        }?.takeIf { it.isNotBlank() }
    }

    private fun readCpuFrequencyMhz(base: File): Int? {
        val raw = CPU_FREQUENCY_FILES.firstNotNullOfOrNull { relativePath ->
            textSource.readLong(File(base, relativePath).absolutePath)
        } ?: return null
        return normalizeCpuFrequencyMhz(raw)
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

    private fun String.parseKilobytes(): Long? = trim().substringBefore(' ').toLongOrNull()

    private companion object {
        const val BYTES_PER_GB = 1_024.0 * 1_024.0 * 1_024.0
        const val MAX_THERMAL_ZONES = 24
        val THERMAL_ZONE_REGEX = Regex("thermal_zone\\d+")
        val CPU_MODEL_KEYS = listOf("Hardware", "model name", "Processor")
        val CPU_FREQUENCY_FILES = listOf("cpufreq/scaling_cur_freq", "cpufreq/cpuinfo_cur_freq")
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
