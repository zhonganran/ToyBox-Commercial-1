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
    version = 19,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        /** First schema shipped by the fresh-install commercial product. */
        const val COMMERCIAL_BASELINE_SCHEMA_VERSION = 17

        /** Current Room schema version. Must match the @Database(version = ...) annotation above. */
        const val SCHEMA_VERSION = 19

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE automation_rules ADD COLUMN exit_actions TEXT NOT NULL DEFAULT '[]'"
                )
                db.execSQL(
                    "ALTER TABLE automation_rules ADD COLUMN repeat_interval_seconds INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS charge_points")
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
