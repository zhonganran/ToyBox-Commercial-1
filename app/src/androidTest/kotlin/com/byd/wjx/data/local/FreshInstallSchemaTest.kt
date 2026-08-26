package com.byd.wjx.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byd.wjx.data.local.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreshInstallSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun currentSchemaContainsOnlyCurrentApplicationTables() {
        helper.createDatabase(TEST_DB, AppDatabase.SCHEMA_VERSION).use { db ->
            val schemaVersion = db.query("PRAGMA user_version").use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
            assertEquals(AppDatabase.SCHEMA_VERSION, schemaVersion)

            val tables = db.query(
                """SELECT name FROM sqlite_master
                    WHERE type='table'
                      AND name NOT LIKE 'sqlite_%'
                      AND name NOT IN ('android_metadata', 'room_master_table')
                """.trimIndent()
            ).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            assertEquals(
                setOf(
                    "automation_log",
                    "automation_rules",
                    "battery_snapshots",
                    "charges",
                    "idle_drains",
                    "last_state",
                    "odometer_samples",
                    "settings",
                    "trip_points",
                    "trips",
                    "vehicle_write_log",
                ),
                tables,
            )

            val chargeColumns = db.query("PRAGMA table_info(charges)").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            assertFalse("lifetime_kwh_at_start" in chargeColumns)
            assertFalse("lifetime_kwh_at_finish" in chargeColumns)
        }
    }

    private companion object {
        const val TEST_DB = "fresh-install.db"
    }
}
