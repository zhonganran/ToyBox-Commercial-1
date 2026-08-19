package com.byd.wjx.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byd.wjx.data.local.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                    "places",
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

    @Test
    fun migration20To21RepairsReleasedLegacyLayoutAndPreservesChargeData() {
        helper.createDatabase(LEGACY_V20_DB, 20).use { db ->
            db.execSQL("ALTER TABLE charges ADD COLUMN lifetime_kwh_at_start REAL")
            db.execSQL("ALTER TABLE charges ADD COLUMN lifetime_kwh_at_finish REAL")
            db.execSQL(
                "UPDATE room_master_table SET identity_hash = ? WHERE id = 42",
                arrayOf(RELEASED_V20_IDENTITY_HASH),
            )
            db.execSQL(
                """INSERT INTO charges (
                    id, start_ts, status, merged_count, kwh_charged,
                    lifetime_kwh_at_start, lifetime_kwh_at_finish
                ) VALUES (7, 1234, 'completed', 2, 18.5, 1000.0, 1018.5)""".trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            LEGACY_V20_DB,
            AppDatabase.SCHEMA_VERSION,
            true,
            AppDatabase.MIGRATION_20_21,
        ).use { db ->
            assertFalse("lifetime_kwh_at_start" in chargeColumns(db))
            assertFalse("lifetime_kwh_at_finish" in chargeColumns(db))
            db.query(
                "SELECT id, start_ts, status, merged_count, kwh_charged FROM charges WHERE id = 7"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7L, cursor.getLong(0))
                assertEquals(1234L, cursor.getLong(1))
                assertEquals("completed", cursor.getString(2))
                assertEquals(2, cursor.getInt(3))
                assertEquals(18.5, cursor.getDouble(4), 0.0)
            }
        }
    }

    @Test
    fun migration20To21AcceptsVersion38FreshInstallLayout() {
        helper.createDatabase(CODE_38_FRESH_DB, 20).use { db ->
            db.execSQL(
                "INSERT INTO charges (id, start_ts, status, merged_count) " +
                    "VALUES (8, 5678, 'charging', 0)"
            )
        }

        helper.runMigrationsAndValidate(
            CODE_38_FRESH_DB,
            AppDatabase.SCHEMA_VERSION,
            true,
            AppDatabase.MIGRATION_20_21,
        ).use { db ->
            assertFalse("lifetime_kwh_at_start" in chargeColumns(db))
            assertFalse("lifetime_kwh_at_finish" in chargeColumns(db))
            db.query("SELECT start_ts, status FROM charges WHERE id = 8").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5678L, cursor.getLong(0))
                assertEquals("charging", cursor.getString(1))
            }
        }
    }

    private fun chargeColumns(db: androidx.sqlite.db.SupportSQLiteDatabase): Set<String> =
        db.query("PRAGMA table_info(charges)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }

    private companion object {
        const val TEST_DB = "fresh-install.db"
        const val LEGACY_V20_DB = "released-v20.db"
        const val CODE_38_FRESH_DB = "code-38-fresh.db"
        const val RELEASED_V20_IDENTITY_HASH = "74ddf8cb06d30c65c572c49886a249c1"
    }
}
