package com.byd.wjx.di

import android.content.Context
import androidx.room.Room
import com.byd.wjx.data.local.dao.BatterySnapshotDao
import com.byd.wjx.data.local.dao.ChargeDao
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.LastStateDao
import com.byd.wjx.data.local.dao.OdometerSampleDao
import com.byd.wjx.data.local.dao.PlaceDao
import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.dao.RuleLogDao
import com.byd.wjx.data.local.dao.SettingsDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.local.dao.VehicleWriteLogDao
import com.byd.wjx.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Database construction and DAO exposure only. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "wjx.db")
            .build()

    @Provides fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()
    @Provides fun provideTripPointDao(db: AppDatabase): TripPointDao = db.tripPointDao()
    @Provides fun provideChargeDao(db: AppDatabase): ChargeDao = db.chargeDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
    @Provides fun provideIdleDrainDao(db: AppDatabase): IdleDrainDao = db.idleDrainDao()
    @Provides fun provideBatterySnapshotDao(db: AppDatabase): BatterySnapshotDao = db.batterySnapshotDao()
    @Provides fun provideRuleDao(db: AppDatabase): RuleDao = db.ruleDao()
    @Provides fun provideRuleLogDao(db: AppDatabase): RuleLogDao = db.ruleLogDao()
    @Provides fun providePlaceDao(db: AppDatabase): PlaceDao = db.placeDao()
    @Provides fun provideOdometerSampleDao(db: AppDatabase): OdometerSampleDao = db.odometerSampleDao()
    @Provides fun provideLastStateDao(db: AppDatabase): LastStateDao = db.lastStateDao()
    @Provides fun provideVehicleWriteLogDao(db: AppDatabase): VehicleWriteLogDao = db.vehicleWriteLogDao()
}
