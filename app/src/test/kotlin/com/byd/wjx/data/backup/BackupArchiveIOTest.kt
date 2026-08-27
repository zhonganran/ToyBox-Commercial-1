package com.byd.wjx.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupArchiveIOTest {
    @Test
    fun `reader extracts only the required bounded entries`() {
        val temporaryDatabase = temporaryDatabase()
        val payload = BackupArchiveReader.extract(
            input = ByteArrayInputStream(
                archive(
                    BackupArchiveContract.ENTRY_DATABASE to "sqlite",
                    BackupArchiveContract.ENTRY_PREFERENCES to "{}",
                    BackupArchiveContract.ENTRY_MANIFEST to "{\"ok\":true}",
                ),
            ),
            temporaryDatabase = temporaryDatabase,
        )

        assertEquals("sqlite", temporaryDatabase.readText())
        assertEquals("{}", payload.preferencesJson)
        assertEquals("{\"ok\":true}", payload.manifestJson)
        temporaryDatabase.delete()
    }

    @Test
    fun `reader rejects unknown archive entries without leaving a database temp file`() {
        val temporaryDatabase = temporaryDatabase()

        assertThrows(IllegalStateException::class.java) {
            BackupArchiveReader.extract(
                input = ByteArrayInputStream(
                    archive(
                        BackupArchiveContract.ENTRY_DATABASE to "sqlite",
                        "../unexpected" to "payload",
                        BackupArchiveContract.ENTRY_PREFERENCES to "{}",
                        BackupArchiveContract.ENTRY_MANIFEST to "{}",
                    ),
                ),
                temporaryDatabase = temporaryDatabase,
            )
        }
        assertFalse(temporaryDatabase.exists())
    }

    @Test
    fun `manifest accepts an older sorted preference inventory`() {
        val manifest = BackupManifest(
            backupFormatVersion = 1,
            appVersionCode = 2120,
            dbSchemaVersion = 21,
            createdAt = 1L,
            preferenceFiles = listOf("a", "b"),
        )

        BackupManifestCodec.requireCurrent(manifest, 1, 21, listOf("a", "b", "new"))
    }

    @Test
    fun `manifest rejects an unsorted or duplicate preference inventory`() {
        val manifest = BackupManifest(
            backupFormatVersion = 1,
            appVersionCode = 2120,
            dbSchemaVersion = 21,
            createdAt = 1L,
            preferenceFiles = listOf("b", "a"),
        )

        assertThrows(IllegalStateException::class.java) {
            BackupManifestCodec.requireCurrent(
                manifest,
                1,
                21,
                listOf("a", "b"),
            )
        }
    }

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun temporaryDatabase(): File = File.createTempFile("wjx-backup-", ".db").apply {
        delete()
    }
}
