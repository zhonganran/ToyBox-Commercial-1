package com.byd.wjx.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byd.wjx.data.local.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommercialBaselineSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun currentSchemaContainsCommercialTables() {
        helper.createDatabase(TEST_DB, AppDatabase.SCHEMA_VERSION).use { db ->
            val tables = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN (?, ?, ?)",
                arrayOf("automation_rules", "trips", "vehicle_write_log"),
            ).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            assertEquals(setOf("automation_rules", "trips", "vehicle_write_log"), tables)
        }
    }

    @Test
    fun migration17To18PreservesRulesAndAddsAutomationDefaults() {
        helper.createDatabase(MIGRATION_DB, 17).use { db ->
            db.execSQL(
                """INSERT INTO automation_rules
                    (id, name, enabled, trigger_logic, triggers, actions, cooldown_seconds,
                     require_park, confirm_before_execute, fire_once_per_trip,
                     last_triggered_at, trigger_count, created_at)
                    VALUES (1, 'legacy', 1, 'AND', '[]', '[]', 0, 0, 0, 0, NULL, 0, 123)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            MIGRATION_DB,
            AppDatabase.SCHEMA_VERSION,
            true,
            AppDatabase.MIGRATION_17_18,
        ).use { db ->
            db.query(
                "SELECT name, exit_actions, repeat_interval_seconds FROM automation_rules WHERE id = 1"
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("legacy", cursor.getString(0))
                assertEquals("[]", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
        }
    }

    private companion object {
        const val TEST_DB = "commercial-baseline.db"
        const val MIGRATION_DB = "commercial-migration-17-18.db"
    }
}
