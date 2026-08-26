package com.byd.wjx.helper

import com.byd.wjx.storage.WjxDirectories
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun helperLog(tag: String, message: String) {
    val timestamp = synchronized(HELPER_LOG_DATE) { HELPER_LOG_DATE.format(Date()) }
    val line = "$timestamp I/$tag $message\n"
    runCatching {
        if (!WjxDirectories.ensureBaseDirs()) return@runCatching
        val log = WjxDirectories.appLog
        trimLogIfNeeded(log)
        log.appendText(line)
    }
}

private fun trimLogIfNeeded(file: File) {
    if (file.exists() && file.length() > HELPER_LOG_MAX_BYTES) {
        val tail = file.readLines().takeLast(HELPER_LOG_KEEP_LINES).joinToString("\n", postfix = "\n")
        file.writeText(tail)
    }
}

private const val HELPER_LOG_MAX_BYTES = 8 * 1024 * 1024L
private const val HELPER_LOG_KEEP_LINES = 8_000
private val HELPER_LOG_DATE = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)
