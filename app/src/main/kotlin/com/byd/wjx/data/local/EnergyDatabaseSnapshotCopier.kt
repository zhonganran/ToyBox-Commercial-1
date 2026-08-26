package com.byd.wjx.data.local

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class EnergyDatabaseSnapshotCopier(
    private val context: Context,
) {
    fun copy(source: File): File {
        val externalDirectory = context.getExternalFilesDir(null)
            ?: throw EnergyDataException("ExternalFilesDir 不可用")
        val localFile = File(externalDirectory, LOCAL_DATABASE_NAME)
        val temporaryFile = File(externalDirectory, "$LOCAL_DATABASE_NAME.tmp")
        repeat(COPY_ATTEMPTS) { attempt ->
            temporaryFile.delete()
            val before = SourceFingerprint.capture(source)
            if (!before.readable) throw EnergyDataException("EnergyData 数据库不可读取")
            if (before.hasActiveJournal) {
                retryDelay(attempt)
                return@repeat
            }
            FileInputStream(source).use { input ->
                FileOutputStream(temporaryFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            val after = SourceFingerprint.capture(source)
            if (before == after && !after.hasActiveJournal) {
                moveIntoPlace(temporaryFile, localFile)
                return localFile
            }
            temporaryFile.delete()
            retryDelay(attempt)
        }
        temporaryFile.delete()
        throw EnergyDataException("EnergyData 数据库正在写入，无法创建一致副本，请稍后重试")
    }

    private fun moveIntoPlace(temporaryFile: File, localFile: File) {
        try {
            Files.move(
                temporaryFile.toPath(),
                localFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                localFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun retryDelay(attempt: Int) {
        if (attempt + 1 < COPY_ATTEMPTS) Thread.sleep(COPY_RETRY_DELAY_MS)
    }

    private data class SourceFingerprint(
        val readable: Boolean,
        val size: Long,
        val modifiedAt: Long,
        val walSize: Long,
        val walModifiedAt: Long,
        val journalSize: Long,
        val journalModifiedAt: Long,
    ) {
        val hasActiveJournal: Boolean get() = walSize > 0L || journalSize > 0L

        companion object {
            fun capture(source: File): SourceFingerprint {
                val wal = File("${source.absolutePath}-wal")
                val journal = File("${source.absolutePath}-journal")
                return SourceFingerprint(
                    readable = source.isFile && source.canRead() && source.length() > 0L,
                    size = source.length(),
                    modifiedAt = source.lastModified(),
                    walSize = wal.takeIf(File::isFile)?.length() ?: 0L,
                    walModifiedAt = wal.takeIf(File::isFile)?.lastModified() ?: 0L,
                    journalSize = journal.takeIf(File::isFile)?.length() ?: 0L,
                    journalModifiedAt = journal.takeIf(File::isFile)?.lastModified() ?: 0L,
                )
            }
        }
    }

    private companion object {
        const val LOCAL_DATABASE_NAME = "vehicle_ec.db"
        const val COPY_ATTEMPTS = 3
        const val COPY_RETRY_DELAY_MS = 75L
    }
}
