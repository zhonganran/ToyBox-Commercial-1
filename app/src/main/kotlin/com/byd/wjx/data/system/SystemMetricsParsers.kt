package com.byd.wjx.data.system

import kotlin.math.roundToInt

internal fun parseMemInfo(text: String): MemoryMetrics? {
    val values = text.lineSequence().mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@mapNotNull null
        val key = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim().parseKilobytes()
            ?: return@mapNotNull null
        key to value
    }.toMap()
    val totalKb = values["MemTotal"] ?: return null
    if (totalKb <= 0L) return null
    val availableKb = (
        values["MemAvailable"]
            ?: listOf("MemFree", "Buffers", "Cached").sumOf { values[it] ?: 0L }
        ).coerceIn(0L, totalKb)
    fun megabytes(key: String): Long? = values[key]?.div(KB_PER_MB)
    return MemoryMetrics(
        totalMb = totalKb / KB_PER_MB,
        availableMb = availableKb / KB_PER_MB,
        usedPercent = (((totalKb - availableKb).coerceAtLeast(0L) * 100.0) / totalKb)
            .roundToInt()
            .coerceIn(0, 100),
        cachedMb = megabytes("Cached"),
        buffersMb = megabytes("Buffers"),
        activeMb = megabytes("Active"),
        inactiveMb = megabytes("Inactive"),
        dirtyMb = megabytes("Dirty"),
        slabMb = megabytes("Slab"),
        swapTotalMb = megabytes("SwapTotal"),
        swapFreeMb = megabytes("SwapFree"),
        zramMb = megabytes("Zram"),
        cmaMb = megabytes("CmaTotal"),
    )
}

internal fun parseCpuStat(text: String): Map<String, CpuTimes> = buildMap {
    text.lineSequence().forEach { line ->
        val fields = line.trim().split(WHITESPACE_REGEX)
        val name = fields.firstOrNull() ?: return@forEach
        if (name != CPU_TOTAL && !name.matches(CPU_CORE_REGEX)) return@forEach
        val counters = fields.drop(1).mapNotNull { it.toLongOrNull() }
        if (counters.size < 4) return@forEach
        val idle = counters[3] + counters.getOrElse(4) { 0L }
        put(name, CpuTimes(total = counters.take(8).sum(), idle = idle))
    }
}

internal fun parseLoadAverage(text: String): List<Double?> = text
    .trim()
    .split(WHITESPACE_REGEX)
    .take(3)
    .map { it.toDoubleOrNull() }

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
        val fields = line.trim().split(WHITESPACE_REGEX)
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
    val elapsedSeconds = elapsedMs?.takeIf { it > 0L }?.div(1_000.0)
    return current.values.map { now ->
        val before = previous?.get(now.device)
        val readDelta = before?.let { (now.readSectors - it.readSectors).coerceAtLeast(0L) }
        val writeDelta = before?.let { (now.writeSectors - it.writeSectors).coerceAtLeast(0L) }
        DiskIoMetric(
            device = now.device,
            readMb = sectorsToMegabytes(now.readSectors),
            writeMb = sectorsToMegabytes(now.writeSectors),
            readMbPerSec = throughputMegabytesPerSecond(readDelta, elapsedSeconds),
            writeMbPerSec = throughputMegabytesPerSecond(writeDelta, elapsedSeconds),
        )
    }.sortedBy { it.device }
}

internal fun normalizeCpuFrequencyMhz(raw: Long): Int? = when {
    raw >= 100_000_000L -> (raw / 1_000_000L).toInt()
    raw >= 100_000L -> (raw / 1_000L).toInt()
    else -> raw.toInt()
}.takeIf { it > 0 }

internal fun formatGpuValue(label: String, raw: String): String {
    val value = raw.substringBefore(' ').toLongOrNull() ?: return raw.take(32)
    return when {
        label.contains("freq", ignoreCase = true) || "频率" in label ->
            "${normalizeCpuFrequencyMhz(value) ?: value} MHz"
        value in 0..100 -> "$value%"
        else -> raw.take(32)
    }
}

internal fun normalizeTemperature(raw: Long): Float? {
    val value = when {
        raw >= 10_000L || raw <= -10_000L -> raw / 1_000f
        raw >= 1_000L || raw <= -1_000L -> raw / 100f
        raw >= 200L || raw <= -200L -> raw / 10f
        else -> raw.toFloat()
    }
    return value.takeIf { it in -50f..200f }
}

private fun throughputMegabytesPerSecond(
    sectorDelta: Long?,
    elapsedSeconds: Double?,
): Double? = if (sectorDelta != null && elapsedSeconds != null) {
    sectorDelta * SECTOR_BYTES / BYTES_PER_MB.toDouble() / elapsedSeconds
} else {
    null
}

private fun sectorsToMegabytes(sectors: Long): Long = sectors * SECTOR_BYTES / BYTES_PER_MB

private fun String.parseKilobytes(): Long? = trim().substringBefore(' ').toLongOrNull()

private fun isWholeDiskDevice(device: String): Boolean = when {
    device.startsWith("ram") -> false
    device.startsWith("loop") -> false
    device.startsWith("dm-") -> false
    device.matches(Regex("mmcblk\\d+p\\d+")) -> false
    device.matches(Regex("nvme\\d+n\\d+p\\d+")) -> false
    device.matches(Regex("sd[a-z]+\\d+")) -> false
    else -> true
}

internal const val CPU_TOTAL = "cpu"
internal const val KB_PER_MB = 1_024L
internal const val BYTES_PER_MB = 1_024L * 1_024L
private const val SECTOR_BYTES = 512L
internal val WHITESPACE_REGEX = Regex("\\s+")
internal val CPU_CORE_REGEX = Regex("cpu\\d+")
