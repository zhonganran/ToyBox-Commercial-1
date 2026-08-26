package com.byd.wjx.data.local

import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EnergyDatabaseReaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private var database: SQLiteDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun `full query excludes deleted rows and orders newest first`() {
        val file = createDatabase()
        insert(id = 1, start = 100, tripKm = 0.0, deleted = 0)
        insert(id = 2, start = 300, tripKm = 12.5, deleted = 0)
        insert(id = 3, start = 400, tripKm = 99.0, deleted = 1)
        closeDatabase()

        val records = EnergyDatabaseReader().readTrips(file)

        assertEquals(listOf(2L, 1L), records.map(BydTripRecord::id))
        assertEquals(12.5, records.first().tripKm, 0.0001)
        assertTrue(records.last().tripKm <= 0.0)
    }

    @Test
    fun `incremental query uses strict watermark and chronological order`() {
        val file = createDatabase()
        insert(id = 1, start = 100, tripKm = 1.0, deleted = 0)
        insert(id = 2, start = 200, tripKm = 2.0, deleted = 0)
        insert(id = 3, start = 300, tripKm = 3.0, deleted = 0)
        closeDatabase()

        val records = EnergyDatabaseReader().readTripsSince(file, sinceTimestampSec = 100)

        assertEquals(listOf(2L, 3L), records.map(BydTripRecord::id))
    }

    @Test
    fun `inspection reports schema counts and deleted samples`() {
        val file = createDatabase()
        insert(id = 1, start = 100, tripKm = 1.0, deleted = 0)
        insert(id = 2, start = 200, tripKm = 2.0, deleted = 1)
        closeDatabase()

        val inspection = EnergyDatabaseReader().inspect(file)

        assertTrue(EnergyDataQueries.TABLE in inspection.tables)
        assertTrue(inspection.columns.any { it.name == "electricity" && it.type == "REAL" })
        assertEquals(2, inspection.totalCount)
        assertEquals(1, inspection.activeCount)
        assertEquals(listOf(2L, 1L), inspection.samples.map { it.record.id })
        assertEquals(listOf(1, 0), inspection.samples.map(EnergyDiagnosticSample::deleted))
    }

    @Test
    fun `inspection handles databases without the energy table`() {
        val file = temporaryFolder.newFile("empty.db")
        database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database!!.execSQL("CREATE TABLE OtherTable (_id INTEGER PRIMARY KEY)")
        closeDatabase()

        val inspection = EnergyDatabaseReader().inspect(file)

        assertFalse(EnergyDataQueries.TABLE in inspection.tables)
        assertTrue(inspection.columns.isEmpty())
        assertEquals(0, inspection.totalCount)
    }

    private fun createDatabase(): File {
        val file = temporaryFolder.newFile("energy-${System.nanoTime()}.db")
        database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database!!.execSQL(
            """CREATE TABLE EnergyConsumption (
                _id INTEGER PRIMARY KEY,
                start_timestamp INTEGER NOT NULL,
                end_timestamp INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                trip REAL NOT NULL,
                electricity REAL NOT NULL,
                is_deleted INTEGER NOT NULL
            )""",
        )
        return file
    }

    private fun insert(id: Long, start: Long, tripKm: Double, deleted: Int) {
        database!!.execSQL(
            """INSERT INTO EnergyConsumption
                (_id, start_timestamp, end_timestamp, duration, trip, electricity, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
            arrayOf<Any>(id, start, start + 60, 60L, tripKm, 1.25, deleted),
        )
    }
}
