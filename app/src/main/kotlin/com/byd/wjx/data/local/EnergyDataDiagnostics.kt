package com.byd.wjx.data.local

import com.byd.wjx.log.WjxFileLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the user-facing energy database diagnostics without owning source or query policy. */
internal class EnergyDataDiagnostics(
    private val source: EnergyDataSource,
    private val databaseReader: EnergyDatabaseReader,
) {
    fun buildReport(): String = buildString {
        appendLine("=== 比亚迪数据存储 ===")
        val directory = source.directory()
        appendLine("路径：${directory.path}")
        appendLine("存在：${formatBool(directory.exists())}")
        appendLine("是目录：${formatBool(directory.isDirectory)}")
        if (!directory.exists()) {
            appendLine("错误：目录不存在")
            return@buildString
        }

        val files = source.listFiles(directory)
        if (files == null) {
            appendLine("文件列表：无结果（可能无访问权限）")
        } else {
            appendLine("文件列表：${files.size} 个文件")
            files.forEach { file ->
                appendLine("  ${file.name}（${file.length()} 字节，${if (file.isFile) "文件" else "目录"}）")
            }
        }

        appendLine("\n检查已知文件名：")
        source.knownDatabaseFiles(directory).forEach { file ->
            val status = when {
                !file.exists() -> "未找到"
                file.length() == 0L -> "空文件"
                else -> "${file.length()} 字节，可读取=${formatBool(file.canRead())}"
            }
            appendLine("  ${file.name}: $status")
        }

        val sourceDatabase = source.findDatabase(directory)
        if (sourceDatabase == null) {
            appendLine("\n错误：未找到数据库")
            return@buildString
        }
        appendLine("\n来源：${sourceDatabase.name}（${sourceDatabase.length()} 字节）")

        try {
            val localDatabase = source.copyToLocal(sourceDatabase)
            appendLine("本地副本：${localDatabase.absolutePath}（${localDatabase.length()} 字节）")
            appendInspection(databaseReader.inspect(localDatabase))
        } catch (error: Exception) {
            appendLine("读取数据库错误：${error.message}")
            WjxFileLogger.w(TAG, "diagnose read database error: ${error.message}")
        }
    }

    private fun StringBuilder.appendInspection(inspection: EnergyDatabaseInspection) {
        appendLine("\n=== 数据库表 ===")
        appendLine(inspection.tables.joinToString(", "))
        if (EnergyDataQueries.TABLE !in inspection.tables) {
            appendLine("错误：未找到 ${EnergyDataQueries.TABLE} 表")
            return
        }

        appendLine("\n=== ${EnergyDataQueries.TABLE} 字段 ===")
        inspection.columns.forEach { column -> appendLine("  ${column.name} (${column.type})") }
        appendLine("\n=== 记录 ===")
        appendLine("总数：${inspection.totalCount}")
        appendLine("有效：${inspection.activeCount}")
        appendLine("已删除：${inspection.totalCount - inspection.activeCount}")
        appendLine("\n=== 示例数据（前 5 条） ===")
        inspection.samples.forEach { sample ->
            val record = sample.record
            appendLine(
                "#${record.id}: ${formatTimestamp(record.startTimestamp)}，${record.duration} 秒，" +
                    "%.1f 公里，%.2f 千瓦时，已删除=${sample.deleted}".format(
                        Locale.US,
                        record.tripKm,
                        record.electricityKwh,
                    ),
            )
        }
    }

    private fun formatBool(value: Boolean): String = if (value) "是" else "否"

    private fun formatTimestamp(timestampSec: Long): String = runCatching {
        SimpleDateFormat("dd.MM.yy HH:mm", Locale.US).format(Date(timestampSec * 1000L))
    }.getOrDefault("?")

    private companion object {
        const val TAG = "EnergyDataDiagnostics"
    }
}
