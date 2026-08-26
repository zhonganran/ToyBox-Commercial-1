package com.byd.wjx.lyrics

import java.io.ByteArrayOutputStream
import java.io.File

internal fun readBoundedFile(file: File, maxBytes: Long): ByteArray? {
    if (maxBytes <= 0L || file.length() !in 1..maxBytes) return null
    return runCatching {
        val output = ByteArrayOutputStream(minOf(file.length(), DEFAULT_BUFFER_SIZE.toLong()).toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        file.inputStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > maxBytes) return null
                output.write(buffer, 0, count)
            }
        }
        output.toByteArray()
    }.getOrNull()
}
