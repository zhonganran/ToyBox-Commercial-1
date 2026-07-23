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

            copyLatestLogSnapshot(source, target, maxBytes = 1024L)

            assertArrayEquals(source.readBytes(), target.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `snapshot keeps the newest bytes when wjx log exceeds upload limit`() {
        val directory = Files.createTempDirectory("wjx-log-upload").toFile()
        try {
            val sourceBytes = ByteArray(64) { index -> index.toByte() }
            val source = directory.resolve("wjx.log").apply { writeBytes(sourceBytes) }
            val target = directory.resolve("snapshot.log")

            copyLatestLogSnapshot(source, target, maxBytes = 16L)

            assertArrayEquals(sourceBytes.takeLast(16).toByteArray(), target.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }
}
