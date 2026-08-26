package com.byd.wjx.data.backup

import com.byd.wjx.storage.WjxDirectories
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Chooses a collision-free backup path in the shared WJX directory. */
class BackupDestinationProvider @Inject constructor() {
    fun create(): File {
        check(WjxDirectories.ensureBaseDirs()) {
            "无法创建 WJX 文件夹，请检查文件写入权限"
        }
        return destinationIn(WjxDirectories.baseDir, System.currentTimeMillis())
    }

    internal companion object {
        fun destinationIn(directory: File, timestampMillis: Long): File {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(Date(timestampMillis))
            val baseName = "ToyBox_backup_$timestamp"
            var destination = File(directory, "$baseName.zip")
            var suffix = 2
            while (destination.exists()) {
                destination = File(directory, "${baseName}_$suffix.zip")
                suffix += 1
            }
            return destination
        }
    }
}
