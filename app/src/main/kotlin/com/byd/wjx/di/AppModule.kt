package com.byd.wjx.di

import android.content.Context
import com.byd.wjx.data.backup.BackupManager
import com.byd.wjx.data.backup.BackupPreferencesRegistry
import com.byd.wjx.data.local.dao.OdometerSampleDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.local.database.AppDatabase
import com.byd.wjx.data.local.EnergyDataReader
import com.byd.wjx.data.local.dao.LastStateDao
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.DefaultVehicleStateRepository
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.loop.CadenceConfig
import com.byd.wjx.data.loop.SharedAdaptiveLoop
import com.byd.wjx.data.nativestack.ParsReader
import com.byd.wjx.data.trips.TripRecorder
import com.byd.wjx.domain.calculator.OdometerConsumptionBuffer
import com.byd.wjx.domain.calculator.RangeAvgSource
import com.byd.wjx.domain.calculator.RangeCalculator
import com.byd.wjx.domain.calculator.SocInterpolator
import com.byd.wjx.domain.calculator.SocInterpolatorPrefs
import com.byd.wjx.domain.calculator.SocInterpolatorPrefsImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideOdometerConsumptionBuffer(
        dao: OdometerSampleDao,
        tripRepository: com.byd.wjx.data.repository.TripRepository,
    ): OdometerConsumptionBuffer = OdometerConsumptionBuffer(
        dao = dao,
        fallbackEmaProvider = { tripRepository.getEmaConsumption() },
    )

    @Provides
    @Singleton
    fun provideSocInterpolatorPrefs(@ApplicationContext ctx: Context): SocInterpolatorPrefs =
        SocInterpolatorPrefsImpl(ctx)

    @Provides
    @Singleton
    fun provideSocInterpolator(
        prefs: SocInterpolatorPrefs,
    ): SocInterpolator = SocInterpolator(persistence = prefs)

    @Provides
    @Singleton
    fun provideRangeCalculator(
        rangeAvgSource: RangeAvgSource,
        settingsRepository: SettingsRepository,
        socInterpolator: SocInterpolator,
    ): RangeCalculator = RangeCalculator(
        buffer = rangeAvgSource,
        capacityProvider = { settingsRepository.getBatteryCapacity() },
        socInterpolator = socInterpolator,
    )

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSharedAdaptiveLoop(
        parsReader: ParsReader,
        lastStateDao: LastStateDao,
        energyDataReader: EnergyDataReader,
    ): SharedAdaptiveLoop = SharedAdaptiveLoop(
        parsReader = parsReader,
        lastStateDao = lastStateDao,
        energyDataReader = energyDataReader,
        cadence = CadenceConfig.default(),
    )

    @Provides
    @Singleton
    fun provideVehicleStateRepository(
        sharedAdaptiveLoop: SharedAdaptiveLoop,
        @ApplicationScope applicationScope: CoroutineScope,
    ): VehicleStateRepository = DefaultVehicleStateRepository(
        fullSnapshot = sharedAdaptiveLoop.snapshot,
        actionSnapshot = sharedAdaptiveLoop.actionSnapshot,
        connected = sharedAdaptiveLoop.connected,
        applicationScope = applicationScope,
    )

    @Provides
    @Singleton
    fun provideTripTracker(
        tripPointDao: TripPointDao,
        settingsRepository: SettingsRepository,
    ): com.byd.wjx.domain.tracker.TripTracker =
        com.byd.wjx.domain.tracker.TripTracker(
            tripPointDao = tripPointDao,
            recordGpsEnabled = { settingsRepository.isTripGpsRecordingEnabled() },
        )

    @Provides
    @Singleton
    fun provideTripRecorder(
        tripDao: TripDao,
        lastStateDao: LastStateDao,
        energyDataReader: EnergyDataReader,
        settingsRepository: SettingsRepository,
    ): TripRecorder = TripRecorder(
        tripDao = tripDao,
        lastStateDao = lastStateDao,
        energyDataReader = energyDataReader,
        batteryCapacityKwh = { settingsRepository.getBatteryCapacity() },
    )

    @Provides
    @Singleton
    fun provideCarProfileDetector(): com.byd.wjx.vehicle.CarProfileDetector =
        com.byd.wjx.vehicle.CarProfileDetector()

    @Provides
    @Singleton
    fun provideWriteAllowlist(@ApplicationContext context: Context): com.byd.wjx.data.vehicle.WriteAllowlist =
        com.byd.wjx.data.vehicle.WriteAllowlist.loadProduction {
            context.assets.open("vehicle-actions.json").bufferedReader().use { it.readText() }
        }

    @Provides
    @Singleton
    fun provideSeatChannelStore(@ApplicationContext context: Context): com.byd.wjx.data.vehicle.SeatChannelStore =
        com.byd.wjx.data.vehicle.SeatChannelStorePrefs(
            context.getSharedPreferences(
                com.byd.wjx.data.vehicle.SeatChannelStorePrefs.PREFS_NAME,
                Context.MODE_PRIVATE,
            ),
        )

    @Provides
    @Singleton
    fun provideBackupManager(
        @ApplicationContext ctx: Context,
        db: AppDatabase,
    ): BackupManager = BackupManager(
        context = ctx,
        appDatabase = db,
        prefsFileNames = BackupPreferencesRegistry.USER_CONFIGURATION_FILES,
        deviceProtectedPrefsFileNames =
            BackupPreferencesRegistry.DEVICE_PROTECTED_USER_CONFIGURATION_FILES,
    )

}
