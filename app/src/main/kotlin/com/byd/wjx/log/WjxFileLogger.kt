package com.byd.wjx.log

import android.util.Log
import com.byd.wjx.security.EndpointPrivacy
import com.byd.wjx.storage.WjxDirectories
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object WjxFileLogger {
    const val MAX_LOG_BYTES = 8 * 1024 * 1024L

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)
    private val lock = Any()
    @Volatile private var writerThread: Thread? = null
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "WJX-FileLogger").apply {
            isDaemon = true
            writerThread = this
        }
    }

    val logFilePath: String
        get() = WjxDirectories.appLog.absolutePath

    fun d(tag: String, message: String) {
        val safeMessage = EndpointPrivacy.redact(message)
        Log.d(tag, safeMessage)
        writeAsync("D", tag, safeMessage)
    }

    fun i(tag: String, message: String) {
        val safeMessage = EndpointPrivacy.redact(message)
        Log.i(tag, safeMessage)
        writeAsync("I", tag, safeMessage)
    }

    fun w(tag: String, message: String) {
        val safeMessage = EndpointPrivacy.redact(message)
        Log.w(tag, safeMessage)
        writeAsync("W", tag, safeMessage)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        val safeDetails = EndpointPrivacy.redact("$message\n${throwable.stackTraceToString()}")
        Log.w(tag, safeDetails)
        writeSync("W", tag, safeDetails)
    }

    fun e(tag: String, message: String) {
        val safeMessage = EndpointPrivacy.redact(message)
        Log.e(tag, safeMessage)
        writeAsync("E", tag, safeMessage)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        val safeDetails = EndpointPrivacy.redact("$message\n${throwable.stackTraceToString()}")
        Log.e(tag, safeDetails)
        writeSync("E", tag, safeDetails)
    }

    fun tail(maxLines: Int = 80): List<String> {
        flush()
        return synchronized(lock) {
            runCatching {
                WjxDirectories.appLog.takeIf { it.exists() }?.also(::trimIfNeeded)
                    ?.readLines()
                    ?.takeLast(maxLines)
                    ?.map(EndpointPrivacy::redact)
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }

    fun clear() {
        runBlockingOnWriter {
            synchronized(lock) {
                runCatching {
                    WjxDirectories.ensureBaseDirs()
                    WjxDirectories.appLog.writeText("")
                }
            }
        }
    }

    fun appendSection(title: String, body: String) {
        runBlockingOnWriter {
            writeSectionInternal(
                EndpointPrivacy.redact(title),
                EndpointPrivacy.redact(body),
            )
        }
    }

    fun appendExternalLine(line: String) {
        synchronized(lock) {
            runCatching {
                WjxDirectories.ensureBaseDirs()
                val file = WjxDirectories.appLog
                trimIfNeeded(file)
                file.appendText("${dateFormat.format(Date())} L/logcat ${EndpointPrivacy.redact(line)}\n")
            }
        }
    }

    fun sanitizeStoredLog() {
        runBlockingOnWriter {
            synchronized(lock) {
                runCatching {
                    val file = WjxDirectories.appLog
                    if (!file.isFile || file.length() <= 0L) return@runCatching
                    trimIfNeeded(file)
                    val original = file.readText()
                    val redacted = EndpointPrivacy.redact(original)
                    if (redacted != original) file.writeText(redacted)
                }
            }
        }
    }

    private fun writeAsync(level: String, tag: String, message: String) {
        runCatching {
            writer.execute { writeInternal(level, tag, message) }
        }.onFailure {
            writeInternal(level, tag, message)
        }
    }

    private fun writeSync(level: String, tag: String, message: String) {
        writeInternal(level, tag, message)
    }

    private fun writeInternal(level: String, tag: String, message: String) {
        synchronized(lock) {
            runCatching {
                WjxDirectories.ensureBaseDirs()
                val file = WjxDirectories.appLog
                trimIfNeeded(file)
                file.appendText(
                    "${dateFormat.format(Date())} $level/$tag ${EndpointPrivacy.redact(message)}\n",
                )
            }
        }
    }

    private fun writeSectionInternal(title: String, body: String) {
        synchronized(lock) {
            runCatching {
                WjxDirectories.ensureBaseDirs()
                val file = WjxDirectories.appLog
                trimIfNeeded(file)
                file.appendText(
                    buildString {
                        appendLine()
                        appendLine("===== $title =====")
                        append(body.trimEnd())
                        appendLine()
                        appendLine("===== /$title =====")
                    }
                )
            }
        }
    }

    fun flush() {
        runBlockingOnWriter { /* barrier */ }
    }

    private fun runBlockingOnWriter(block: () -> Unit) {
        if (Thread.currentThread() == writerThread) {
            block()
            return
        }
        runCatching {
            writer.submit(block).get(2, TimeUnit.SECONDS)
        }.onFailure {
            Log.w("WjxFileLogger", "writer barrier timed out: ${it.message}")
        }
    }

    private fun trimIfNeeded(file: java.io.File) {
        if (file.exists() && file.length() > MAX_LOG_BYTES) {
            val length = file.length()
            val start = (length - MAX_TRIM_READ_BYTES).coerceAtLeast(0L)
            val bytes = ByteArray((length - start).toInt())
            RandomAccessFile(file, "r").use { input ->
                input.seek(start)
                input.readFully(bytes)
            }
            val tail = bytes.toString(Charsets.UTF_8)
                .let { text -> if (start > 0L) text.substringAfter('\n', "") else text }
                .lines()
                .takeLast(MAX_KEEP_LINES)
                .joinToString("\n", postfix = "\n")
            file.writeText(tail)
        }
    }

    private const val MAX_KEEP_LINES = 8_000
    private const val MAX_TRIM_READ_BYTES = 4 * 1024 * 1024L
}
