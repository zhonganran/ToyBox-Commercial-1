package com.byd.wjx.data.dashcam

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

internal data class DashcamVideoFingerprint(
    val uri: String,
    val length: Long,
    val lastModified: Long,
)

internal class DashcamDurationCache(
    private val cacheFile: File,
) {
    private val records = linkedMapOf<String, CacheRecord>()
    private var dirty = false

    init {
        load()
    }

    operator fun get(fingerprint: DashcamVideoFingerprint): Long? {
        val record = records[fingerprint.uri] ?: return null
        if (record.matches(fingerprint)) return record.durationMs
        records.remove(fingerprint.uri)
        dirty = true
        return null
    }

    fun put(fingerprint: DashcamVideoFingerprint, durationMs: Long) {
        if (durationMs !in MIN_DURATION_MS..MAX_DURATION_MS) return
        val updated = CacheRecord(
            length = fingerprint.length,
            lastModified = fingerprint.lastModified,
            durationMs = durationMs,
        )
        if (records.put(fingerprint.uri, updated) != updated) dirty = true
    }

    fun retainAll(activeFingerprints: Set<DashcamVideoFingerprint>) {
        val activeByUri = activeFingerprints.associateBy(DashcamVideoFingerprint::uri)
        val changed = records.entries.removeAll { (uri, record) ->
            activeByUri[uri]?.let(record::matches) != true
        }
        if (changed) dirty = true
    }

    fun persist(): Boolean {
        if (!dirty) return true
        return runCatching {
            if (records.isEmpty()) {
                Files.deleteIfExists(cacheFile.toPath())
                Files.deleteIfExists(temporaryFile().toPath())
            } else {
                writeAtomically()
            }
            dirty = false
        }.isSuccess
    }

    fun clear(): Boolean {
        if (records.isNotEmpty() || cacheFile.exists()) dirty = true
        records.clear()
        return persist()
    }

    private fun load() {
        if (!cacheFile.isFile) return
        runCatching {
            val properties = Properties()
            FileInputStream(cacheFile).use(properties::load)
            properties.stringPropertyNames().forEach { uri ->
                decode(properties.getProperty(uri))?.let { records[uri] = it }
            }
        }.onFailure {
            records.clear()
            runCatching { Files.deleteIfExists(cacheFile.toPath()) }
        }
    }

    private fun writeAtomically() {
        cacheFile.parentFile?.mkdirs()
        val properties = Properties().apply {
            records.forEach { (uri, record) -> setProperty(uri, record.encode()) }
        }
        val temporaryFile = temporaryFile()
        FileOutputStream(temporaryFile).use { output ->
            properties.store(output, null)
            output.fd.sync()
        }
        runCatching {
            Files.move(
                temporaryFile.toPath(),
                cacheFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.recoverCatching {
            Files.move(
                temporaryFile.toPath(),
                cacheFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrThrow()
    }

    private fun temporaryFile(): File = File(cacheFile.parentFile, "${cacheFile.name}.tmp")

    private data class CacheRecord(
        val length: Long,
        val lastModified: Long,
        val durationMs: Long,
    ) {
        fun matches(fingerprint: DashcamVideoFingerprint): Boolean =
            length == fingerprint.length && lastModified == fingerprint.lastModified

        fun encode(): String = "$length\t$lastModified\t$durationMs"
    }

    private companion object {
        const val MIN_DURATION_MS = 1_000L
        const val MAX_DURATION_MS = 24L * 60L * 60L * 1_000L

        fun decode(value: String?): CacheRecord? {
            val parts = value?.split('\t') ?: return null
            if (parts.size != 3) return null
            val length = parts[0].toLongOrNull() ?: return null
            val lastModified = parts[1].toLongOrNull() ?: return null
            val durationMs = parts[2].toLongOrNull()
                ?.takeIf { it in MIN_DURATION_MS..MAX_DURATION_MS }
                ?: return null
            return CacheRecord(length, lastModified, durationMs)
        }
    }
}
