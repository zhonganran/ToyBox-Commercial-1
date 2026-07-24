package com.byd.wjx.ui.settings

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SettingsLogUploaderTest {
    @Test
    fun `snapshot copies the complete wjx log when it fits`() {
        val directory = Files.createTempDirectory("wjx-log-upload").toFile()
        try {
            val source = directory.resolve("wjx.log").apply { writeText("line-1\nline-2\n") }
            val target = directory.resolve("snapshot.log")

            copyRawLogSnapshot(source, target, maxBytes = 1024L)

            assertArrayEquals(source.readBytes(), target.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `snapshot preserves raw bytes without treating the log as text`() {
        val directory = Files.createTempDirectory("wjx-log-upload").toFile()
        try {
            val sourceBytes = byteArrayOf(0x57, 0x4a, 0x58, 0x00, 0xff.toByte(), 0x0a)
            val source = directory.resolve("wjx.log").apply { writeBytes(sourceBytes) }
            val target = directory.resolve("snapshot.log")

            copyRawLogSnapshot(source, target, maxBytes = 16L)

            assertArrayEquals(sourceBytes, target.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }
}
