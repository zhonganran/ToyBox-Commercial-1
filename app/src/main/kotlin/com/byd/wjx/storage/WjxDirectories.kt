package com.byd.wjx.storage

import android.os.Environment
import java.io.File

object WjxDirectories {
    val baseDir: File = File(Environment.getExternalStorageDirectory(), "WJX")
    val soundDir: File = baseDir
    val appLog: File = File(baseDir, "wjx.log")

    fun ensureBaseDirs(): Boolean = baseDir.isDirectory || baseDir.mkdirs()
}
