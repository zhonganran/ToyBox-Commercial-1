package com.byd.wjx.storage

import android.os.Environment
import java.io.File

object WjxDirectories {
    val baseDir: File = File(Environment.getExternalStorageDirectory(), "WJX")
    val soundDir: File = baseDir
    val appLog: File = File(baseDir, "wjx.log")
    val exteriorAudioLog: File = appLog
    val offlineLicense: File = File(baseDir, "wjx_license.dat")
    @Volatile private var obsoleteStorageCleaned = false

    fun ensureBaseDirs(): Boolean {
        val ready = baseDir.isDirectory || baseDir.mkdirs()
        if (!ready) return false
        if (!obsoleteStorageCleaned) {
            synchronized(this) {
                if (!obsoleteStorageCleaned) {
                    cleanupObsoleteEmptyDirs()
                    cleanupObsoleteLogFiles()
                    obsoleteStorageCleaned = true
                }
            }
        }
        return true
    }

    private fun cleanupObsoleteEmptyDirs() {
        listOf("sounds", "music", "video", "logs", "车辆音效")
            .map { File(baseDir, it) }
            .forEach { dir ->
                if (dir.isDirectory && dir.listFiles().isNullOrEmpty()) {
                    runCatching { dir.delete() }
                }
            }
    }

    private fun cleanupObsoleteLogFiles() {
        baseDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name != appLog.name &&
                    (file.name == "exterior_audio.log" || (file.name.startsWith("wjx_logs_") && file.extension == "txt"))
            }
            ?.forEach { file -> runCatching { file.delete() } }
    }
}
