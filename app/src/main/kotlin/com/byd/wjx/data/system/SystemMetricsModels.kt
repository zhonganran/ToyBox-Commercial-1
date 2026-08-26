package com.byd.wjx.data.system

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

data class GpuReading(val label: String, val value: String)

data class ThermalReading(val name: String, val celsius: Float)

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

internal data class CpuTimes(val total: Long, val idle: Long)

internal data class DiskCounters(
    val device: String,
    val readSectors: Long,
    val writeSectors: Long,
)
