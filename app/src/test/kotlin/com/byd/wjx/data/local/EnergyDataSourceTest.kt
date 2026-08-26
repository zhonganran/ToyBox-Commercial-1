package com.byd.wjx.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnergyDataSourceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `listing selects the most recently modified database extension`() {
        val directory = temporaryFolder.newFolder("listed")
        directory.resolve("older.db").apply {
            writeBytes(ByteArray(32))
            setLastModified(1_000L)
        }
        val newest = directory.resolve("newest.sqlite").apply {
            writeBytes(ByteArray(32))
            setLastModified(2_000L)
        }
        directory.resolve("ignored.txt").writeText("not a database")

        assertEquals(newest, EnergyDataSource.findDatabaseFromListing(directory))
    }

    @Test
    fun `known-name fallback follows declared priority and skips empty files`() {
        val directory = temporaryFolder.newFolder("known")
        directory.resolve("EC_database.db").writeBytes(ByteArray(0))
        val selected = directory.resolve("energydata.db").apply { writeBytes(ByteArray(32)) }
        directory.resolve("energy.db").writeBytes(ByteArray(32))

        assertEquals(selected, EnergyDataSource.findKnownDatabase(directory))
    }

    @Test
    fun `source discovery ignores unrelated files`() {
        val directory = temporaryFolder.newFolder("empty")
        directory.resolve("notes.txt").writeText("hello")

        assertNull(EnergyDataSource.findDatabaseFromListing(directory))
        assertNull(EnergyDataSource.findKnownDatabase(directory))
    }

    @Test
    fun `stable source is copied to an fsynced local snapshot`() {
        val directory = temporaryFolder.newFolder("copy")
        val bytes = ByteArray(256) { it.toByte() }
        val source = directory.resolve("energy.db").apply { writeBytes(bytes) }
        val context = ApplicationProvider.getApplicationContext<Context>()

        val local = EnergyDataSource(context, directory).copyToLocal(source)

        assertEquals(bytes.toList(), local.readBytes().toList())
    }

    @Test
    fun `active wal prevents an inconsistent source copy`() {
        val directory = temporaryFolder.newFolder("wal")
        val source = directory.resolve("energy.db").apply { writeBytes(ByteArray(256)) }
        directory.resolve("energy.db-wal").writeBytes(ByteArray(32) { 1 })
        val context = ApplicationProvider.getApplicationContext<Context>()

        val error = assertThrows(EnergyDataException::class.java) {
            EnergyDataSource(context, directory).copyToLocal(source)
        }

        assertEquals(true, error.message.orEmpty().contains("正在写入"))
    }
}
