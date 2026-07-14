package com.byd.wjx.ui.dashboard

import com.byd.wjx.data.autoservice.BatteryReading
import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.data.local.dao.BatterySnapshotDao
import com.byd.wjx.data.local.dao.ChargeDao
import com.byd.wjx.data.local.dao.ChargeSummary
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.SettingsDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.local.dao.TripSummary
import com.byd.wjx.data.local.entity.BatterySnapshotEntity
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.data.local.entity.IdleDrainEntity
import com.byd.wjx.data.local.entity.SettingEntity
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.data.repository.BatteryHealthRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.FakeTrackingStateRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.data.repository.VehicleDataFreshness
import com.byd.wjx.data.repository.VehicleState
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.domain.battery.BatteryStateRepository
import com.byd.wjx.domain.calculator.ConsumptionAggregator
import com.byd.wjx.domain.insight.LocalInsightEngine
import com.byd.wjx.domain.insight.LocalInsightStatsAggregator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeVehicleStateRepository : VehicleStateRepository {
        override val fullSnapshot = MutableStateFlow<VehicleSnapshot?>(null)
        override val actionSnapshot = MutableStateFlow<VehicleSnapshot?>(null)
        override val connected = MutableStateFlow(false)
        override val freshness = MutableStateFlow(VehicleDataFreshness.UNAVAILABLE)
        override val state: StateFlow<VehicleState> = MutableStateFlow(VehicleState())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Fake SettingsDao ---

    private class FakeSettingsDao : SettingsDao {
        val map = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override fun observe(key: String): Flow<String?> = flowOf(map[key])
        override suspend fun set(entity: SettingEntity) { map[entity.key] = entity.value ?: "" }
        override suspend fun setAll(settings: List<SettingEntity>) { settings.forEach { set(it) } }
        override fun getAll(): Flow<List<SettingEntity>> = flowOf(emptyList())
    }

    // --- Stub DAOs ---

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

    // --- Fake vehicle API ---

    private class FakeAutoservice(
        private val battery: BatteryReading?,
        private val available: Boolean = true
    ) : VehicleApi {
        override suspend fun readBatterySnapshot(): BatteryReading? = battery.takeIf { available }
        override suspend fun readSnapshot(): com.byd.wjx.data.remote.DiParsData? = null
        override suspend fun dispatch(commandString: String): Result<Unit> = Result.success(Unit)
        override suspend fun writeAcOn(): Result<Unit> = Result.success(Unit)
        override suspend fun writeAcOff(): Result<Unit> = Result.success(Unit)
        override suspend fun writeSetDriverTemp(celsius: Int): Result<Unit> = Result.success(Unit)
        override suspend fun writeWindowDriver(percent: Int): Result<Unit> = Result.success(Unit)
        override suspend fun writeWindowPassenger(percent: Int): Result<Unit> = Result.success(Unit)
        override suspend fun writeWindowRearLeft(percent: Int): Result<Unit> = Result.success(Unit)
        override suspend fun writeWindowRearRight(percent: Int): Result<Unit> = Result.success(Unit)
        override suspend fun writeLockDoors(): Result<Unit> = Result.success(Unit)
        override suspend fun writeUnlockDoors(): Result<Unit> = Result.success(Unit)
        override suspend fun writeSunroof(mode: com.byd.wjx.data.vehicle.SunroofMode): Result<Unit> = Result.success(Unit)
        override suspend fun writeSunshade(open: Boolean): Result<Unit> = Result.success(Unit)
    }

    // --- Factory ---

    private fun buildViewModel(
        fakeAutoservice: VehicleApi
    ): DashboardViewModel {
        val settingsDao = FakeSettingsDao()
        val settingsRepo = SettingsRepository(settingsDao)

        val tripDao = StubTripDao()
        val tripPointDao = StubTripPointDao()
        val tripRepo = TripRepository(tripDao, tripPointDao)

        val idleDrainDao = StubIdleDrainDao()
        val batterySnapshotDao = StubBatterySnapshotDao()

        val batteryHealthRepo = BatteryHealthRepository(batterySnapshotDao)
        val batteryStateRepo = BatteryStateRepository(fakeAutoservice, batteryHealthRepo)
        val vehicleStateRepository = FakeVehicleStateRepository()
        val trackingStateRepository = FakeTrackingStateRepository()
        val localInsightStatsAggregator = LocalInsightStatsAggregator(
            tripDao = tripDao,
            chargeDao = StubChargeDao(),
            idleDrainDao = idleDrainDao,
            batterySnapshotDao = batterySnapshotDao,
            vehicleApi = fakeAutoservice,
            vehicleStateRepository = vehicleStateRepository,
        )

        return DashboardViewModel(
            tripRepository = tripRepo,
            settingsRepository = settingsRepo,
            idleDrainDao = idleDrainDao,
            batteryStateRepository = batteryStateRepo,
            vehicleStateRepository = vehicleStateRepository,
            trackingStateRepository = trackingStateRepository,
            consumptionAggregator = ConsumptionAggregator(),
            localInsightStatsAggregator = localInsightStatsAggregator,
            localInsightEngine = LocalInsightEngine(),
        )
    }

    // --- Tests ---

    private val sampleReading = BatteryReading(
        sohPercent = 100f,
        socPercent = 91f,
        lifetimeKwh = 602f,
        lifetimeMileageKm = 2091f,
        voltage12v = 14f,
        readAtMs = 0L
    )

    @Test
    fun `adbConnected is true when autoservice connected`() = runTest {
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(sampleReading, available = true)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, vm.uiState.value.adbConnected)
    }

    @Test
    fun `adbConnected is false when autoservice unavailable`() = runTest {
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(battery = null, available = false)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.adbConnected!!)
    }

    @Test
    fun `currentSoh is populated from autoservice snapshot`() = runTest {
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(sampleReading, available = true)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(100f, vm.uiState.value.currentSoh!!, 0.01f)
    }
}
