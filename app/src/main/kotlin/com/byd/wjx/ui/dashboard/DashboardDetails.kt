package com.byd.wjx.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.system.StorageVolumeMetric
import com.byd.wjx.data.system.SystemMetricsSnapshot
import com.byd.wjx.domain.energy.EnergyFlowCategory
import com.byd.wjx.domain.energy.EnergyFlowTotals
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.SocYellow
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.util.Locale

internal fun formatEnergyFlowKwh(value: Double): String = when {
    value >= 10.0 -> "%.1f".format(value)
    value >= 0.1 -> "%.2f".format(value)
    else -> "%.3f".format(value)
}

internal fun energyFlowCategoryLabel(category: EnergyFlowCategory): String = when (category) {
    EnergyFlowCategory.EV_4WD_REGEN -> "纯电四驱回收"
    EnergyFlowCategory.EV_FWD_REGEN -> "纯电前驱回收"
    EnergyFlowCategory.EV_RWD_REGEN -> "纯电后驱回收"
    EnergyFlowCategory.HEV_REGEN -> "混动回收"
    EnergyFlowCategory.ASSIST_REGEN -> "辅助驱动回收"
    EnergyFlowCategory.OTHER_REGEN -> "其他回收"
    EnergyFlowCategory.ENGINE_GENERATION -> "发动机发电"
    EnergyFlowCategory.NONE -> "当前无回收"
}

@Composable
internal fun ColumnScope.SystemMetricsDetailRows(
    snapshot: SystemMetricsSnapshot?,
    loading: Boolean,
    sampleReady: Boolean,
) {
    if (snapshot == null) {
        Text(
            text = if (loading) "正在读取车机系统信息…" else "当前系统节点不可读",
            color = TextMuted,
            fontSize = 13.sp,
        )
        return
    }

    SystemMetricsSection("处理器")
    DetailRow(
        "总占用",
        snapshot.cpuTotalUsagePercent?.let { "$it%" } ?: if (sampleReady) "--" else "采样中",
        AccentBlue,
    )
    DetailRow("核心数", "${snapshot.cpuCoreCount}", TextPrimary)
    snapshot.cpuModelName?.let { DetailRow("CPU 型号", it, TextPrimary) }
    val loadText = listOf(snapshot.loadAverage1, snapshot.loadAverage5, snapshot.loadAverage15)
        .joinToString(" / ") { it?.let { value -> "%.2f".format(value) } ?: "--" }
    DetailRow("系统负载 1/5/15", loadText, TextPrimary)
    snapshot.uptimeSeconds?.let { DetailRow("运行时间", formatUptime(it), TextPrimary) }
    snapshot.cpuCores.take(12).forEach { core ->
        val value = buildList {
            add(core.usagePercent?.let { "$it%" } ?: "--")
            core.frequencyMhz?.let { add("$it MHz") }
            core.governor?.let(::add)
        }.joinToString(" · ")
        DetailRow("CPU${core.index}", value, TextPrimary)
    }

    snapshot.memory?.let { memory ->
        SystemMetricsSection("内存")
        DetailRow("已用", "${memory.usedPercent}%", AccentBlue)
        DetailRow("可用 / 总量", "${memory.availableMb} / ${memory.totalMb} MB", TextPrimary)
        memory.cachedMb?.let { DetailRow("缓存", "$it MB", TextPrimary) }
        memory.buffersMb?.let { DetailRow("Buffers", "$it MB", TextPrimary) }
        memory.activeMb?.let { DetailRow("活跃", "$it MB", TextPrimary) }
        memory.inactiveMb?.let { DetailRow("非活跃", "$it MB", TextPrimary) }
        memory.dirtyMb?.let { DetailRow("待写回", "$it MB", TextPrimary) }
        memory.slabMb?.let { DetailRow("Slab", "$it MB", TextPrimary) }
        memory.swapTotalMb?.let { total ->
            DetailRow("Swap 可用 / 总量", "${memory.swapFreeMb ?: 0} / $total MB", TextPrimary)
        }
        memory.zramMb?.let { DetailRow("ZRAM", "$it MB", TextPrimary) }
        memory.cmaMb?.let { DetailRow("CMA", "$it MB", TextPrimary) }
    }

    if (snapshot.storageVolumes.isNotEmpty()) {
        SystemMetricsSection("存储空间")
        snapshot.storageVolumes.forEach { volume ->
            DetailRow(storageLabel(volume.label), formatStorageVolumeSummary(volume), TextPrimary)
        }
    }

    if (snapshot.diskIo.isNotEmpty()) {
        SystemMetricsSection("磁盘 IO")
        snapshot.diskIo.take(8).forEach { disk ->
            val speed = if (disk.readMbPerSec != null || disk.writeMbPerSec != null) {
                "R %.2f / W %.2f MB/s".format(disk.readMbPerSec ?: 0.0, disk.writeMbPerSec ?: 0.0)
            } else if (!sampleReady) {
                "采样中"
            } else {
                "--"
            }
            DetailRow(disk.device, speed, TextPrimary)
            DetailRow("累计读 / 写", "${disk.readMb} / ${disk.writeMb} MB", TextMuted)
        }
    }

    if (snapshot.gpuReadings.isNotEmpty()) {
        SystemMetricsSection("GPU")
        snapshot.gpuReadings.forEach { DetailRow(it.label, it.value, TextPrimary) }
    }

    if (snapshot.thermalZones.isNotEmpty()) {
        SystemMetricsSection("温度")
        snapshot.thermalZones.take(16).forEach { reading ->
            val color = when {
                reading.celsius >= 90f -> SocRed
                reading.celsius >= 75f -> SocYellow
                else -> TextPrimary
            }
            DetailRow(reading.name, "%.1f °C".format(reading.celsius), color)
        }
    }

    SystemMetricsSection("设备")
    snapshot.device.socModel?.let { DetailRow("SoC", it, TextPrimary) }
    DetailRow("车机型号", snapshot.device.model.ifBlank { "--" }, TextPrimary)
    DetailRow("主板 / 硬件", "${snapshot.device.board} / ${snapshot.device.hardware}", TextPrimary)
    DetailRow("Android", "${snapshot.device.androidVersion} (API ${snapshot.device.apiLevel})", TextPrimary)
    snapshot.device.securityPatch.takeIf { it.isNotBlank() }?.let {
        DetailRow("安全补丁", it, TextPrimary)
    }
    DetailRow("ABI", snapshot.device.abis.ifBlank { "--" }, TextPrimary)

    SystemMetricsSection("当前应用进程")
    DetailRow("PID / 线程", "${snapshot.appProcess.pid} / ${snapshot.appProcess.threadCount ?: "--"}", TextPrimary)
    DetailRow(
        "RSS / 虚拟内存",
        "${snapshot.appProcess.vmRssMb ?: "--"} / ${snapshot.appProcess.vmSizeMb ?: "--"} MB",
        TextPrimary,
    )
    DetailRow(
        "JVM 堆",
        "${snapshot.appProcess.jvmHeapUsedMb} / ${snapshot.appProcess.jvmHeapMaxMb} MB",
        TextPrimary,
    )
    DetailRow("打开文件数", snapshot.appProcess.openFdCount?.toString() ?: "--", TextPrimary)
}

@Composable
private fun SystemMetricsSection(title: String) {
    Text(
        text = title,
        color = AccentBlue,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

private fun storageLabel(label: String): String = when (label) {
    "data" -> "数据分区"
    "root" -> "根分区"
    "cache" -> "缓存分区"
    "external" -> "外部存储"
    else -> label
}

internal fun formatStorageVolumeSummary(volume: StorageVolumeMetric): String =
    "${volume.usedPercent}% · 可用 ${"%.1f".format(Locale.CHINA, volume.freeGb)} / " +
        "总 ${"%.1f".format(Locale.CHINA, volume.totalGb)} GB"

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400L
    val hours = seconds % 86_400L / 3_600L
    val minutes = seconds % 3_600L / 60L
    return when {
        days > 0 -> "${days}天 ${hours}小时"
        hours > 0 -> "${hours}小时 ${minutes}分"
        else -> "${minutes}分"
    }
}

@Composable
internal fun ColumnScope.EnergyFlowDetailRows(title: String, totals: EnergyFlowTotals) {
    Text(
        text = title,
        color = AccentGreen,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
    DetailRow("回收合计", "${formatEnergyFlowKwh(totals.totalRegenKwh)} kWh", TextPrimary)
    DetailRow("纯电四驱", "${formatEnergyFlowKwh(totals.ev4wdRegenKwh)} kWh", TextPrimary)
    DetailRow("纯电前驱", "${formatEnergyFlowKwh(totals.evFwdRegenKwh)} kWh", TextPrimary)
    DetailRow("纯电后驱", "${formatEnergyFlowKwh(totals.evRwdRegenKwh)} kWh", TextPrimary)
    DetailRow("混动回收", "${formatEnergyFlowKwh(totals.hevRegenKwh)} kWh", TextPrimary)
    DetailRow("辅助驱动回收", "${formatEnergyFlowKwh(totals.assistRegenKwh)} kWh", TextPrimary)
    DetailRow("其他回收", "${formatEnergyFlowKwh(totals.otherRegenKwh)} kWh", TextPrimary)
    DetailRow("发动机发电", "${formatEnergyFlowKwh(totals.engineGenerationKwh)} kWh", TextPrimary)
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
}
