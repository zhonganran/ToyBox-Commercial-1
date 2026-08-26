package com.byd.wjx.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Owns read-only SQLite query execution and row mapping. */
internal class EnergyDatabaseReader {
    fun readTrips(databaseFile: File): List<BydTripRecord> = open(databaseFile) { database ->
        database.rawQuery(EnergyDataQueries.ALL_TRIPS, null).use(::readRecords)
    }

    fun readTripsSince(databaseFile: File, sinceTimestampSec: Long): List<BydTripRecord> =
        open(databaseFile) { database ->
            database.rawQuery(
                EnergyDataQueries.TRIPS_SINCE,
                arrayOf(sinceTimestampSec.toString()),
            ).use(::readRecords)
        }

    fun inspect(databaseFile: File): EnergyDatabaseInspection = open(databaseFile) { database ->
        val tables = database.rawQuery(EnergyDataQueries.TABLES, null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        if (EnergyDataQueries.TABLE !in tables) {
            return@open EnergyDatabaseInspection(tables, emptyList(), 0, 0, emptyList())
        }

        val columns = database.rawQuery(EnergyDataQueries.COLUMNS, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(EnergyDatabaseColumn(name = cursor.getString(1), type = cursor.getString(2)))
                }
            }
        }
        val totalCount = database.scalarCount(EnergyDataQueries.TOTAL_COUNT)
        val activeCount = database.scalarCount(EnergyDataQueries.ACTIVE_COUNT)
        val samples = database.rawQuery(EnergyDataQueries.DIAGNOSTIC_SAMPLES, null).use { cursor ->
            buildList {
                val deletedColumn = cursor.getColumnIndexOrThrow("is_deleted")
                while (cursor.moveToNext()) {
                    add(EnergyDiagnosticSample(cursor.readBydTripRecord(), cursor.getInt(deletedColumn)))
                }
            }
        }
        EnergyDatabaseInspection(tables, columns, totalCount, activeCount, samples)
    }

    private fun readRecords(cursor: Cursor): List<BydTripRecord> = buildList {
        while (cursor.moveToNext()) add(cursor.readBydTripRecord())
    }

    private fun SQLiteDatabase.scalarCount(query: String): Int = rawQuery(query, null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    private inline fun <T> open(databaseFile: File, block: (SQLiteDatabase) -> T): T =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use(block)
}
