package com.byd.wjx.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.byd.wjx.data.local.dao.BatterySnapshotDao
import com.byd.wjx.data.local.dao.ChargeDao
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.OdometerSampleDao
import com.byd.wjx.data.local.dao.PlaceDao
import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.dao.RuleLogDao
import com.byd.wjx.data.local.dao.SettingsDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.dao.LastStateDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.local.dao.VehicleWriteLogDao
import com.byd.wjx.data.local.entity.BatterySnapshotEntity
import com.byd.wjx.data.local.entity.LastStateEntity
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.local.entity.IdleDrainEntity
import com.byd.wjx.data.local.entity.OdometerSampleEntity
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.data.local.entity.RuleEntity
import com.byd.wjx.data.local.entity.RuleLogEntity
import com.byd.wjx.data.local.entity.SettingEntity
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.data.local.entity.VehicleWriteLogEntity

@Database(
    entities = [
        TripEntity::class,
        TripPointEntity::class,
        ChargeEntity::class,
        SettingEntity::class,
        IdleDrainEntity::class,
        BatterySnapshotEntity::class,
        RuleEntity::class,
        RuleLogEntity::class,
        PlaceEntity::class,
        OdometerSampleEntity::class,
        LastStateEntity::class,
        VehicleWriteLogEntity::class
    ],
    version = 21,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        /** Current Room schema version. Must match the @Database(version = ...) annotation above. */
        const val SCHEMA_VERSION = 21

        /**
         * Repairs the two schema-20 layouts released under the same Room version.
         *
         * Builds upgraded from the previous release still contain the retired lifetime-kWh
         * columns, while fresh installs of app version code 38 do not. Both layouts report
         * user_version=20, so normalize them to the current table before Room validates v21.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val chargeColumns = db.query("PRAGMA table_info(charges)").use { cursor ->
                    buildSet {
                        val nameIndex = cursor.getColumnIndexOrThrow("name")
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                }
                if (
                    "lifetime_kwh_at_start" !in chargeColumns &&
                    "lifetime_kwh_at_finish" !in chargeColumns
                ) {
                    return
                }

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `charges_v21` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `start_ts` INTEGER NOT NULL,
                        `end_ts` INTEGER,
                        `soc_start` INTEGER,
                        `soc_end` INTEGER,
                        `kwh_charged` REAL,
                        `kwh_charged_soc` REAL,
                        `max_power_kw` REAL,
                        `type` TEXT,
                        `cost` REAL,
                        `lat` REAL,
                        `lon` REAL,
                        `bat_temp_avg` REAL,
                        `bat_temp_max` REAL,
                        `bat_temp_min` REAL,
                        `avg_power_kw` REAL,
                        `status` TEXT NOT NULL,
                        `cell_voltage_min` REAL,
                        `cell_voltage_max` REAL,
                        `voltage_12v` REAL,
                        `exterior_temp` INTEGER,
                        `merged_count` INTEGER NOT NULL,
                        `gun_state` INTEGER,
                        `detection_source` TEXT
                    )""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO `charges_v21` (
                        `id`, `start_ts`, `end_ts`, `soc_start`, `soc_end`, `kwh_charged`,
                        `kwh_charged_soc`, `max_power_kw`, `type`, `cost`, `lat`, `lon`,
                        `bat_temp_avg`, `bat_temp_max`, `bat_temp_min`, `avg_power_kw`, `status`,
                        `cell_voltage_min`, `cell_voltage_max`, `voltage_12v`, `exterior_temp`,
                        `merged_count`, `gun_state`, `detection_source`
                    ) SELECT
                        `id`, `start_ts`, `end_ts`, `soc_start`, `soc_end`, `kwh_charged`,
                        `kwh_charged_soc`, `max_power_kw`, `type`, `cost`, `lat`, `lon`,
                        `bat_temp_avg`, `bat_temp_max`, `bat_temp_min`, `avg_power_kw`, `status`,
                        `cell_voltage_min`, `cell_voltage_max`, `voltage_12v`, `exterior_temp`,
                        `merged_count`, `gun_state`, `detection_source`
                    FROM `charges`""".trimIndent()
                )
                db.execSQL("DROP TABLE `charges`")
                db.execSQL("ALTER TABLE `charges_v21` RENAME TO `charges`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_charges_start_ts` ON `charges` (`start_ts`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_charges_status` ON `charges` (`status`)"
                )
            }
        }
    }

    abstract fun tripDao(): TripDao
    abstract fun tripPointDao(): TripPointDao
    abstract fun chargeDao(): ChargeDao
    abstract fun settingsDao(): SettingsDao
    abstract fun idleDrainDao(): IdleDrainDao
    abstract fun batterySnapshotDao(): BatterySnapshotDao
    abstract fun ruleDao(): RuleDao
    abstract fun ruleLogDao(): RuleLogDao
    abstract fun placeDao(): PlaceDao
    abstract fun odometerSampleDao(): OdometerSampleDao
    abstract fun lastStateDao(): LastStateDao
    abstract fun vehicleWriteLogDao(): VehicleWriteLogDao
}
