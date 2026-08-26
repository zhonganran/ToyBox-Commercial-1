package com.byd.wjx.data.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object BackupArchiveContract {
    const val ENTRY_DATABASE = "wjx.db"
    const val ENTRY_PREFERENCES = "prefs.json"
    const val ENTRY_MANIFEST = "manifest.json"
    const val DATABASE_FILE_NAME = "wjx.db"

    val requiredEntries = setOf(ENTRY_DATABASE, ENTRY_PREFERENCES, ENTRY_MANIFEST)
}

internal object BackupArchiveReader {
    fun extract(input: InputStream, temporaryDatabase: File): BackupArchivePayload {
        temporaryDatabase.parentFile?.mkdirs()
        temporaryDatabase.delete()
        val seenEntries = mutableSetOf<String>()
        var preferencesJson: String? = null
        var manifestJson: String? = null
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    validateEntry(entry, seenEntries)
                    when (entry.name) {
                        BackupArchiveContract.ENTRY_DATABASE -> {
                            FileOutputStream(temporaryDatabase).use { output ->
                                val size = copyBounded(zip, output, MAX_DATABASE_BYTES)
                                if (size == 0L) throw IllegalStateException("备份中的数据库文件为空")
                            }
                        }
                        BackupArchiveContract.ENTRY_PREFERENCES -> {
                            preferencesJson = readUtf8Bounded(zip, MAX_PREFERENCES_BYTES)
                        }
                        BackupArchiveContract.ENTRY_MANIFEST -> {
                            manifestJson = readUtf8Bounded(zip, MAX_MANIFEST_BYTES)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val missing = BackupArchiveContract.requiredEntries - seenEntries
            if (missing.isNotEmpty()) {
                throw IllegalStateException("备份文件损坏或不完整。缺少必要内容：${missing.sorted()}")
            }
            return BackupArchivePayload(
                preferencesJson = checkNotNull(preferencesJson),
                manifestJson = checkNotNull(manifestJson),
            )
        } catch (error: Exception) {
            temporaryDatabase.delete()
            throw error
        }
    }

    private fun validateEntry(entry: ZipEntry, seenEntries: MutableSet<String>) {
        if (entry.isDirectory || entry.name !in BackupArchiveContract.requiredEntries) {
            throw IllegalStateException("备份包含不允许的 ZIP 条目：${entry.name}")
        }
        if (!seenEntries.add(entry.name)) {
            throw IllegalStateException("备份包含重复 ZIP 条目：${entry.name}")
        }
        val limit = when (entry.name) {
            BackupArchiveContract.ENTRY_DATABASE -> MAX_DATABASE_BYTES
            BackupArchiveContract.ENTRY_PREFERENCES -> MAX_PREFERENCES_BYTES
            BackupArchiveContract.ENTRY_MANIFEST -> MAX_MANIFEST_BYTES
            else -> 0L
        }
        if (entry.size > limit) {
            throw IllegalStateException("备份条目过大：${entry.name}")
        }
    }

    private fun readUtf8Bounded(input: InputStream, limit: Long): String {
        val output = ByteArrayOutputStream()
        copyBounded(input, output, limit)
        return output.toString(Charsets.UTF_8.name())
    }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IllegalStateException("备份条目超过允许大小")
            output.write(buffer, 0, read)
        }
        return total
    }

    private const val MAX_DATABASE_BYTES = 512L * 1_024L * 1_024L
    private const val MAX_PREFERENCES_BYTES = 4L * 1_024L * 1_024L
    private const val MAX_MANIFEST_BYTES = 64L * 1_024L
}

internal object BackupArchiveWriter {
    fun write(
        output: OutputStream,
        databaseFile: File,
        preferencesJson: String,
        manifestJson: String,
    ) {
        ZipOutputStream(output).use { zip ->
            zip.writeEntry(BackupArchiveContract.ENTRY_DATABASE) {
                databaseFile.inputStream().use { it.copyTo(zip) }
            }
            zip.writeEntry(BackupArchiveContract.ENTRY_PREFERENCES) {
                zip.write(preferencesJson.toByteArray(Charsets.UTF_8))
            }
            zip.writeEntry(BackupArchiveContract.ENTRY_MANIFEST) {
                zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private inline fun ZipOutputStream.writeEntry(name: String, writeBody: () -> Unit) {
        putNextEntry(ZipEntry(name))
        writeBody()
        closeEntry()
    }
}
