package com.byd.wjx.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.activation.ActivationManager
import com.byd.wjx.data.backup.BackupManager
import com.byd.wjx.data.local.LocalePreferences
import com.byd.wjx.data.local.EnergyDataReader
import com.byd.wjx.data.local.HistoryImporter
import com.byd.wjx.data.local.dao.BatterySnapshotDao
import com.byd.wjx.data.local.dao.ChargeDao
import com.byd.wjx.data.local.dao.ChargePointDao
import com.byd.wjx.data.local.dao.ChargeSummary
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.SettingsDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.local.dao.TripSummary
import com.byd.wjx.data.local.entity.BatterySnapshotEntity
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.local.entity.ChargePointEntity
import com.byd.wjx.data.local.entity.IdleDrainEntity
import com.byd.wjx.data.local.entity.SettingEntity
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.data.settings.ServiceQrCache
import com.byd.wjx.service.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val logRecorders = mutableListOf<SettingsLogRecorder>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        logRecorders.forEach(SettingsLogRecorder::close)
        logRecorders.clear()
        Dispatchers.resetMain()
    }

    // --- Fake DAOs ---

    private class FakeSettingsDao : SettingsDao {
        val map = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override fun observe(key: String): Flow<String?> = flowOf(map[key])
        override suspend fun set(entity: SettingEntity) { map[entity.key] = entity.value ?: "" }
        override suspend fun setAll(settings: List<SettingEntity>) { settings.forEach { set(it) } }
        override fun getAll(): Flow<List<SettingEntity>> = flowOf(emptyList())
    }

    private class StubTripDao : TripDao {
        override suspend fun insert(trip: TripEntity): Long = 0L
        override suspend fun update(trip: TripEntity) {}
        override fun getAll(): Flow<List<TripEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): TripEntity? = null
        override fun getByDateRange(from: Long, to: Long): Flow<List<TripEntity>> = flowOf(emptyList())
        override suspend fun getTodaySummary(dayStart: Long, dayEnd: Long): TripSummary = TripSummary(0.0, 0.0)
        override fun getLastTrip(): Flow<TripEntity?> = flowOf(null)
        override fun getRecent(limit: Int): Flow<List<TripEntity>> = flowOf(emptyList())
        override suspend fun getCount(): Int = 0
        override suspend fun getByBydId(bydId: Long): TripEntity? = null
        override suspend fun getTripsWithoutSoc(): List<TripEntity> = emptyList()
        override suspend fun getTripsWithoutCost(): List<TripEntity> = emptyList()
        override suspend fun getPeriodSummary(from: Long, to: Long): TripSummary = TripSummary(0.0, 0.0)
        override suspend fun getLiveTrips(): List<TripEntity> = emptyList()
        override suspend fun getByStartTsRange(minTs: Long, maxTs: Long): TripEntity? = null
        override suspend fun getAllSnapshot(): List<TripEntity> = emptyList()
        override suspend fun deleteById(id: Long) {}
        override suspend fun deleteZeroKmTrips(): Int = 0
        override suspend fun getTripsForCapacityEstimate(minSocDelta: Int, limit: Int): List<TripEntity> = emptyList()
        override suspend fun getRecentSummary(maxTrips: Int): TripSummary = TripSummary(0.0, 0.0)
        override suspend fun getRecentForEma(limit: Int): List<TripEntity> = emptyList()
        override suspend fun getForEmaSince(fromTs: Long): List<TripEntity> = emptyList()
        override suspend fun getRecentForEmaFiltered(minKm: Double, limit: Int): List<TripEntity> = emptyList()
    }

    private class StubTripPointDao : TripPointDao {
        override suspend fun insertAll(points: List<TripPointEntity>) {}
        override suspend fun getByTripId(tripId: Long): List<TripPointEntity> = emptyList()
        override suspend fun getByTripIds(tripIds: List<Long>): List<TripPointEntity> = emptyList()
        override suspend fun getCount(): Int = 0
        override suspend fun getAll(): List<TripPointEntity> = emptyList()
        override suspend fun thinOldPoints(cutoff: Long, intervalMs: Long): Int = 0
        override suspend fun getByTimeRange(from: Long, to: Long): List<TripPointEntity> = emptyList()
        override suspend fun attachToTrip(tripId: Long, from: Long, to: Long): Int = 0
        override suspend fun attachToTripWithBydKey(
            tripId: Long,
            bydId: Long?,
            bydDate: String?,
            from: Long,
            to: Long
        ): Int = 0
        override suspend fun relinkByBydKey(tripId: Long, bydId: Long, bydDate: String?): Int = 0
        override suspend fun tagTripPointsWithBydKey(
            tripId: Long,
            bydId: Long,
            bydDate: String?,
            from: Long,
            to: Long
        ): Int = 0
        override suspend fun detachOrphanPoints(): Int = 0
        override suspend fun insert(point: TripPointEntity): Long = 0L
    }

    private class StubChargeDao : ChargeDao {
        override suspend fun insert(charge: ChargeEntity): Long = 0L
        override suspend fun update(charge: ChargeEntity) {}
        override fun getAll(): Flow<List<ChargeEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): ChargeEntity? = null
        override fun getByDateRange(from: Long, to: Long): Flow<List<ChargeEntity>> = flowOf(emptyList())
        override suspend fun getPeriodSummary(from: Long, to: Long): ChargeSummary = ChargeSummary(0, 0.0, 0.0)
        override fun getLastCharge(): Flow<ChargeEntity?> = flowOf(null)
        override suspend fun getLastSuspendedCharge(): ChargeEntity? = null
        override suspend fun getStaleSessions(cutoffTs: Long): List<ChargeEntity> = emptyList()
        override suspend fun getLastChargeSync(): ChargeEntity? = null
        override suspend fun getRecentChargesWithBatteryData(): List<ChargeEntity> = emptyList()
        override suspend fun getMaxLifetimeKwhAtFinish(): Double? = null
        override suspend fun getAllAutoserviceCharges(): List<ChargeEntity> = emptyList()
        override suspend fun hasLegacyCharges(): Boolean = false
        override suspend fun deleteEmpty(): Int = 0
        override suspend fun deletePhantomAutoserviceRows(): Int = 0
        override suspend fun delete(charge: ChargeEntity) {}
    }

    private class StubChargePointDao : ChargePointDao {
        override suspend fun insertAll(points: List<ChargePointEntity>) {}
        override suspend fun getByChargeId(chargeId: Long): List<ChargePointEntity> = emptyList()
        override suspend fun getCount(): Int = 0
        override suspend fun thinOldPoints(cutoff: Long, intervalMs: Long): Int = 0
    }

    private class StubIdleDrainDao : IdleDrainDao {
        override suspend fun insert(drain: IdleDrainEntity): Long = 0L
        override suspend fun update(drain: IdleDrainEntity) {}
        override fun getByDateRange(from: Long, to: Long): Flow<List<IdleDrainEntity>> = flowOf(emptyList())
        override suspend fun getTodayDrainKwh(dayStart: Long, dayEnd: Long): Double = 0.0
        override suspend fun getCount(): Int = 0
        override suspend fun getAll(): List<IdleDrainEntity> = emptyList()
        override suspend fun getTotalKwh(): Double = 0.0
        override suspend fun deleteAll() {}
        override suspend fun getTodayDrainHours(dayStart: Long, dayEnd: Long): Double = 0.0
        override suspend fun getKwhSince(since: Long): Double = 0.0
        override suspend fun getHoursSince(since: Long): Double = 0.0
        override suspend fun getKwhBetween(from: Long, to: Long): Double = 0.0
    }

    private class StubBatterySnapshotDao : BatterySnapshotDao {
        override fun getAll(): Flow<List<BatterySnapshotEntity>> = flowOf(emptyList())
        override fun getRecent(limit: Int): Flow<List<BatterySnapshotEntity>> = flowOf(emptyList())
        override suspend fun insert(snapshot: BatterySnapshotEntity): Long = 0L
        override suspend fun getLast(): BatterySnapshotEntity? = null
        override suspend fun getCount(): Int = 0
    }

    // --- Factory ---

    private fun buildViewModel(updateChecker: UpdateChecker? = null): SettingsViewModel {
        val ctx: Context = ApplicationProvider.getApplicationContext()

        val settingsDao = FakeSettingsDao()
        val settingsRepo = SettingsRepository(settingsDao)

        val tripDao = StubTripDao()
        val tripPointDao = StubTripPointDao()
        val tripRepo = TripRepository(tripDao, tripPointDao)

        val chargeRepo = ChargeRepository(StubChargeDao(), StubChargePointDao())
        val idleDrainDao = StubIdleDrainDao()

        val httpClient = OkHttpClient()
        val resolvedUpdateChecker = updateChecker ?: UpdateChecker(httpClient)

        val energyReader = EnergyDataReader(ctx)
        val historyImporter = HistoryImporter(
            ctx, energyReader, tripRepo, tripDao, tripPointDao, idleDrainDao,
            settingsRepo, com.byd.wjx.data.repository.LastSessionRepository()
        )

        // Stub BackupManager: no AppDatabase available in unit tests, use mockk
        val backupManager = mockk<BackupManager>(relaxed = true)
        val chargingStateStore = com.byd.wjx.data.charging.ChargingStateStore(settingsRepo)
        val logRecorder = SettingsLogRecorder(
            appContext = ctx,
            settingsRepository = settingsRepo,
            localePreferences = LocalePreferences(ctx),
            chargingStateStore = chargingStateStore,
            catchUpJournal = com.byd.wjx.data.charging.CatchUpJournal(settingsRepo),
        ).also(logRecorders::add)

        return SettingsViewModel(
            appContext = ctx,
            settingsRepository = settingsRepo,
            tripRepository = tripRepo,
            chargeRepository = chargeRepo,
            updateChecker = resolvedUpdateChecker,
            historyImporter = historyImporter,
            energyDataReader = energyReader,
            idleDrainDao = idleDrainDao,
            tripPointDao = tripPointDao,
            backupManager = backupManager,
            activationManager = mockk<ActivationManager>(relaxed = true),
            soundLibrary = com.byd.wjx.storage.SoundLibrary(ctx),
            lockSoundConfigSync = mockk<com.byd.wjx.audio.LockSoundConfigSync>(relaxed = true),
            autoParkingSoundSettings = com.byd.wjx.audio.AutoParkingSoundSettings(ctx),
            okHttpClient = httpClient,
            serviceQrCache = ServiceQrCache(ctx),
            logRecorder = logRecorder,
        )
    }

    // --- Tests ---

    @Test
    fun `initial state has default battery capacity`() = runTest {
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SettingsRepository.DEFAULT_BATTERY_CAPACITY, vm.uiState.value.batteryCapacity)
    }

    @Test
    fun `fuel tank capacity starts blank and saves as vehicle specific setting`() = runTest {
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SettingsRepository.DEFAULT_FUEL_TANK_CAPACITY, vm.uiState.value.fuelTankCapacity)

        vm.saveFuelTankCapacity("53")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("53", vm.uiState.value.fuelTankCapacity)
    }

    @Test
    fun `theme mode defaults to adaptive and can switch to dark`() = runTest {
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SettingsRepository.DEFAULT_THEME_MODE, vm.uiState.value.themeMode)

        vm.setThemeMode("dark")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("dark", vm.uiState.value.themeMode)
    }

    @Test
    fun `initial state has default home tariff`() = runTest {
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SettingsRepository.DEFAULT_HOME_TARIFF, vm.uiState.value.homeTariff)
    }

    // Issue #23: pressing "Close" while an update is downloading must cancel the
    // download coroutine and clear the Downloading state. Without cancellation the
    // progress callback keeps re-emitting Downloading (re-opening the dialog) and
    // the download eventually fires the system install prompt unexpectedly.
    @Test
    fun `closing update dialog during download cancels job and clears downloading state`() = runTest {
        val uc = mockk<UpdateChecker>(relaxed = true)
        val info = UpdateChecker.UpdateInfo(
            version = "9.9.9",
            downloadUrl = "http://example.invalid/SuperToyBox.apk",
            releaseNotes = ""
        )
        coEvery { uc.checkForUpdate(any(), any()) } returns info
        val onProgressSlot = slot<(String) -> Unit>()
        coEvery { uc.downloadAndInstall(any(), any(), capture(onProgressSlot)) } coAnswers {
            onProgressSlot.captured.invoke("10%")
            delay(10_000)                       // still downloading...
            onProgressSlot.captured.invoke("100%") // must NOT run after the dialog is closed
        }

        val vm = buildViewModel(updateChecker = uc)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.downloadUpdate()
        testDispatcher.scheduler.runCurrent() // start download, emit "10%", suspend on delay

        vm.hideUpdateDialog()
        testDispatcher.scheduler.advanceUntilIdle() // cancelled job must not resurrect state

        assertEquals(UpdateState.Idle, vm.uiState.value.updateDialogState)
    }
}
