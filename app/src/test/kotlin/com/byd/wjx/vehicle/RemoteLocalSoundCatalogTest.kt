package com.byd.wjx.vehicle

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteLocalSoundCatalogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `listFiles only returns supported audio in root directory`() {
        val root = temporaryFolder.newFolder("WJX")
        File(root, "b.WAV").writeBytes(byteArrayOf(1))
        File(root, "a.mp3").writeBytes(byteArrayOf(2))
        File(root, "wjx.log").writeText("log")
        File(root, "wjx_license.dat").writeText("license")
        File(root, "cover.jpg").writeBytes(byteArrayOf(3))
        File(root, "nested").mkdirs()
        File(root, "nested/hidden.mp3").writeBytes(byteArrayOf(4))

        val result = RemoteLocalSoundCatalog.listFiles(
            rootDirectory = root,
            supportedExtensions = setOf("mp3", "wav"),
            maxItems = 200,
        )

        assertEquals(listOf("a.mp3", "b.WAV"), result.map { it.name })
    }

    @Test
    fun `soundKey is stable and changes after file metadata changes`() {
        val file = temporaryFolder.newFile("sound.mp3").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(setLastModified(1_700_000_000_000L))
        }
        val original = RemoteLocalSoundCatalog.soundKey(file)

        assertEquals(64, original.length)
        assertTrue(original.matches(Regex("^[a-f0-9]{64}$")))
        assertEquals(original, RemoteLocalSoundCatalog.soundKey(file))

        file.appendBytes(byteArrayOf(4))
        assertTrue(file.setLastModified(1_700_000_001_000L))
        assertNotEquals(original, RemoteLocalSoundCatalog.soundKey(file))
    }

    @Test
    fun `parseReference only accepts complete hexadecimal sound keys`() {
        val validUppercase = "A".repeat(64)

        assertEquals(
            RemoteLocalSoundReference("a".repeat(64), "欢迎上车"),
            RemoteLocalSoundCatalog.parseReference(validUppercase, "  欢迎上车  "),
        )
        assertNull(RemoteLocalSoundCatalog.parseReference("a".repeat(63), "欢迎上车"))
        assertNull(RemoteLocalSoundCatalog.parseReference("z".repeat(64), "欢迎上车"))
        assertNull(RemoteLocalSoundCatalog.parseReference("a".repeat(64), "  "))
    }
}
