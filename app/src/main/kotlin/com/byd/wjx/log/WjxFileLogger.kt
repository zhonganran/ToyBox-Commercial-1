package com.byd.wjx.log

import android.util.Log
import com.byd.wjx.storage.WjxDirectories
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
        Log.d(tag, message)
        writeAsync("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeAsync("I", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeAsync("W", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
        writeSync("W", tag, "$message\n${throwable.stackTraceToString()}")
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
        writeAsync("E", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
        writeSync("E", tag, "$message\n${throwable.stackTraceToString()}")
    }

    fun tail(maxLines: Int = 80): List<String> {
        flush()
        return synchronized(lock) {
            runCatching {
                WjxDirectories.appLog
                    .takeIf { it.exists() }
                    ?.readLines()
                    ?.takeLast(maxLines)
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
            writeSectionInternal(title, body)
        }
    }

    fun appendExternalLine(line: String) {
        synchronized(lock) {
            runCatching {
                WjxDirectories.ensureBaseDirs()
                val file = WjxDirectories.appLog
                trimIfNeeded(file)
                file.appendText("${dateFormat.format(Date())} L/logcat $line\n")
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
                file.appendText("${dateFormat.format(Date())} $level/$tag $message\n")
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
            file.writeText(file.readLines().takeLast(MAX_KEEP_LINES).joinToString("\n", postfix = "\n"))
        }
    }

    private const val MAX_KEEP_LINES = 8_000
}
