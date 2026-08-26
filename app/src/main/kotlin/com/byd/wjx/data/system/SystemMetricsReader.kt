package com.byd.wjx.data.system

import com.byd.wjx.data.vehicle.HelperClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes differential CPU/disk samples and assembles the complete system snapshot. */
@Singleton
class SystemMetricsReader @Inject constructor(
    helperClient: HelperClient,
) {
    private val sampleMutex = Mutex()
    private val textSource = SystemMetricsTextSource(helperClient)
    private val hardwareReader = SystemMetricsHardwareReader(textSource)
    private var previousCpuTimes: Map<String, CpuTimes>? = null
    private var previousDiskCounters: Map<String, DiskCounters>? = null
    private var previousSampleAtMs: Long? = null

    suspend fun readSnapshot(): SystemMetricsSnapshot = withContext(Dispatchers.IO) {
        sampleMutex.withLock {
            primeCpuSampleIfNeeded()
            readSnapshotLocked()
        }
    }

    private suspend fun primeCpuSampleIfNeeded() {
        if (previousCpuTimes != null) return
        val capturedAtMs = System.currentTimeMillis()
        val cpuTimes = textSource.readCpuTimes()
        if (cpuTimes.isEmpty()) return
        previousCpuTimes = cpuTimes
        previousDiskCounters = textSource.read(SystemMetricPaths.PROC_DISKSTATS)
            ?.let(::parseDiskStats)
            .orEmpty()
        previousSampleAtMs = capturedAtMs
        delay(INITIAL_CPU_SAMPLE_DELAY_MS)
    }

    private suspend fun readSnapshotLocked(): SystemMetricsSnapshot {
        val capturedAtMs = System.currentTimeMillis()
        val cpuTimes = textSource.readCpuTimes()
        val cpuUsage = calculateCpuUsage(previousCpuTimes, cpuTimes)
        val diskCounters = textSource.read(SystemMetricPaths.PROC_DISKSTATS)
            ?.let(::parseDiskStats)
            .orEmpty()
        val diskIo = calculateDiskIo(
            previous = previousDiskCounters,
            current = diskCounters,
            elapsedMs = previousSampleAtMs?.let { capturedAtMs - it },
        )
        previousCpuTimes = cpuTimes.takeIf { it.isNotEmpty() }
        previousDiskCounters = diskCounters
        previousSampleAtMs = capturedAtMs

        val loadAverage = textSource.readLoadAverage()
        return SystemMetricsSnapshot(
            capturedAtMs = capturedAtMs,
            loadAverage1 = loadAverage.getOrNull(0),
            loadAverage5 = loadAverage.getOrNull(1),
            loadAverage15 = loadAverage.getOrNull(2),
            uptimeSeconds = textSource.readUptimeSeconds(),
            cpuTotalUsagePercent = cpuUsage[CPU_TOTAL],
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            cpuModelName = hardwareReader.cpuModelName,
            cpuCores = hardwareReader.readCpuCores(cpuTimes.keys, cpuUsage),
            memory = hardwareReader.readMemory(),
            storageVolumes = hardwareReader.readStorageVolumes(),
            diskIo = diskIo,
            gpuReadings = hardwareReader.readGpuReadings(),
            thermalZones = hardwareReader.readThermalZones(),
            device = hardwareReader.deviceMetrics,
            appProcess = hardwareReader.readAppProcessMetrics(),
        )
    }

    private companion object {
        const val INITIAL_CPU_SAMPLE_DELAY_MS = 350L
    }
}
