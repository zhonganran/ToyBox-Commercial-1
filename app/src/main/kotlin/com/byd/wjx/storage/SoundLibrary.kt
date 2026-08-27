package com.byd.wjx.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundLibrary @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    fun listSounds(): List<SoundItem> {
        WjxDirectories.ensureBaseDirs()
        val directory = WjxDirectories.soundDir
        val listedFiles = directory.listFiles()
        if (listedFiles == null) {
            val allFilesAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()
            WjxFileLogger.w(
                TAG,
                "list sounds failed directory=${directory.absolutePath} " +
                    "exists=${directory.exists()} readable=${directory.canRead()} " +
                    "allFilesAccess=$allFilesAccess",
            )
            return emptyList()
        }
        val files = listedFiles.filter {
                it.isFile &&
                    it.extension.lowercase() in SUPPORTED_EXT
            }
        val order = loadOrder()
        val orderIndex = order.withIndex().associate { it.value to it.index }
        return files
            .sortedWith(
                compareBy<File> { orderIndex[it.absolutePath] ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() }
            )
            .map { SoundItem(it) }
    }

    fun moveSound(fromPath: String, toPath: String) {
        if (fromPath == toPath) return
        val current = listSounds().map { it.file.absolutePath }.toMutableList()
        val fromIndex = current.indexOf(fromPath)
        val toIndex = current.indexOf(toPath)
        if (fromIndex < 0 || toIndex < 0) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex.coerceIn(0, current.size), moved)
        saveOrder(current)
    }

    fun deleteSound(path: String): Boolean {
        if (!isManagedSoundFile(path)) return false
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        val deleted = file.delete()
        if (deleted) {
            saveOrder(loadOrder().filterNot { it == file.absolutePath })
            WjxFileLogger.i(TAG, "delete sound path=${file.absolutePath}")
        } else {
            WjxFileLogger.w(TAG, "delete sound failed path=${file.absolutePath}")
        }
        return deleted
    }

    fun createSoundFile(name: String, extension: String): File {
        WjxDirectories.ensureBaseDirs()
        return createSoundFileIn(WjxDirectories.soundDir, name, extension)
    }

    private fun createSoundFileIn(directory: File, name: String, extension: String): File {
        val safeBase = sanitizeName(name).ifBlank { "音效" }
        val safeExt = extension.lowercase().trim('.').takeIf { it in SUPPORTED_EXT } ?: "wav"
        var candidate = File(directory, "$safeBase.$safeExt")
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "$safeBase-$index.$safeExt")
            index += 1
        }
        return candidate
    }

    fun saveSound(name: String, extension: String, input: InputStream): File {
        val target = createSoundFile(name, extension)
        return saveToTarget(target, input)
    }

    fun isManagedSound(path: String): Boolean = isManagedSoundFile(path)

    private fun saveToTarget(target: File, input: InputStream): File {
        val tmp = File(target.parentFile, "${target.name}.download")
        runCatching { tmp.delete() }
        try {
            tmp.outputStream().use { output ->
                copySoundBytes(input, output, MAX_SOUND_BYTES)
            }
            if (tmp.length() <= 0L) {
                throw IllegalStateException("下载文件为空")
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = false)
                tmp.delete()
            }
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            runCatching { if (target.length() <= 0L) target.delete() }
            WjxFileLogger.w(TAG, "save sound failed target=${target.absolutePath}: ${e.message}", e)
            throw e
        }
        WjxFileLogger.i(TAG, "save sound path=${target.absolutePath} size=${target.length()}")
        return target
    }

    data class SoundItem(val file: File) {
        val name: String = file.nameWithoutExtension
        val ext: String = file.extension
        val sizeLabel: String = formatSize(file.length())
    }

    companion object {
        private const val TAG = "SoundLibrary"
        val SUPPORTED_EXT = setOf("mp3", "wav", "aac", "m4a", "ogg", "flac")
        const val MAX_SOUND_BYTES = 50L * 1024L * 1024L
        private const val PREFS_NAME = "sound_library"
        private const val KEY_ORDER = "order"

        fun isManagedSoundPath(path: String): Boolean {
            if (path.isBlank()) return false
            val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
            val directory = runCatching { WjxDirectories.soundDir.canonicalFile }.getOrNull() ?: return false
            return file.parentFile == directory &&
                file.extension.lowercase() in SUPPORTED_EXT
        }

        fun isManagedSoundFile(path: String): Boolean =
            isManagedSoundPath(path) && File(path).isFile

        fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "${DecimalFormat("0.#").format(kb)} KB"
            return "${DecimalFormat("0.#").format(kb / 1024.0)} MB"
        }

        private fun sanitizeName(name: String): String {
            return name
                .trim()
                .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "")
                .replace(Regex("\\s+"), " ")
                .take(48)
                .trim('.', ' ')
        }
    }

    private fun loadOrder(): List<String> =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, "")
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun saveOrder(paths: List<String>) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDER, paths.distinct().joinToString("\n"))
            .apply()
    }

}

internal fun copySoundBytes(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long,
): Long {
    require(maxBytes > 0L)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (total + read > maxBytes) {
            error("音效文件不能超过 ${SoundLibrary.MAX_SOUND_BYTES / 1024L / 1024L} MB")
        }
        output.write(buffer, 0, read)
        total += read
    }
    return total
}
