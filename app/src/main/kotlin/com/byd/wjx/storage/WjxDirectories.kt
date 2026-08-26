package com.byd.wjx.storage

import android.os.Environment
import com.byd.wjx.BuildConfig
import java.io.File

object WjxDirectories {
    val baseDir: File = File(
        Environment.getExternalStorageDirectory(),
        "Android/data/${BuildConfig.APPLICATION_ID}/files/WJX",
    )
    val soundDir: File = baseDir
    val appLog: File = File(baseDir, "wjx.log")
    val offlineLicense: File = File(baseDir, "wjx_license.dat")

    fun ensureBaseDirs(): Boolean = baseDir.isDirectory || baseDir.mkdirs()
}
