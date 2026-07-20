package com.byd.wjx.ui.dashboard

import com.byd.wjx.data.autoservice.BatteryReading
import com.byd.wjx.data.vehicle.VehicleApi
import com.byd.wjx.data.local.dao.BatterySnapshotDao
import com.byd.wjx.data.local.dao.ChargeDao
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
import com.byd.wjx.data.energy.EnergyFlowStatsRepository
import com.byd.wjx.data.repository.ChargeRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.FakeTrackingStateRepository
import com.byd.wjx.data.repository.TripRepository
import com.byd.wjx.data.repository.VehicleDataFreshness
import com.byd.wjx.data.repository.VehicleState
import com.byd.wjx.data.repository.VehicleStateRepository
import com.byd.wjx.data.nativestack.OnDemandVehicleTelemetryReader
import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind
import com.byd.wjx.domain.calculator.ConsumptionAggregator
import com.byd.wjx.domain.insight.LocalInsightEngine
import com.byd.wjx.domain.insight.LocalInsightStatsAggregator
import com.byd.wjx.domain.energy.EnergyFlowStats
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
import org.junit.Assert.assertNull
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
        private val mutableState = MutableStateFlow(VehicleState())
        override val state: StateFlow<VehicleState> = mutableState

        fun update(data: com.byd.wjx.data.remote.DiParsData) {
            val snapshot = VehicleSnapshot.synthetic(data, VehicleSnapshotKind.FULL)
            fullSnapshot.value = snapshot
            connected.value = true
            freshness.value = VehicleDataFreshness.FRESH
            mutableState.value = VehicleState(
                fullSnapshot = snapshot,
                connected = true,
                freshness = VehicleDataFreshness.FRESH,
            )
        }
    }

    private class FakeOnDemandTelemetryReader(
        var data: com.byd.wjx.data.remote.DiParsData? = null,
    ) : OnDemandVehicleTelemetryReader {
        override suspend fun readTireTelemetry() = data
        override suspend fun readPowertrainTelemetry() = data
    }

    private class FakeEnergyFlowStatsRepository : EnergyFlowStatsRepository {
        override val state = MutableStateFlow(EnergyFlowStats())
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
        private val values = mutableMapOf<String, MutableStateFlow<String?>>()
        override suspend fun get(key: String): String? = map[key]
        override fun observe(key: String): Flow<String?> =
            values.getOrPut(key) { MutableStateFlow(map[key]) }
        override suspend fun set(entity: SettingEntity) {
            val value = entity.value ?: ""
            map[entity.key] = value
            values.getOrPut(entity.key) { MutableStateFlow(null) }.value = value
        }
        override suspend fun setAll(settings: List<SettingEntity>) { settings.forEach { set(it) } }
        override fun getAll(): Flow<List<SettingEntity>> = flowOf(emptyList())
    }

    // --- Stub DAOs ---

    private class StubTripDao : TripDao {
        override suspend fun insert(trip: TripEntity): Long = 0L
        override suspend fun update(trip: TripEntity) {}
        override fun getAll(): Flow<List<TripEntity>> = flowOf(emptyList())
        override fun getByDateRange(from: Long, to: Long): Flow<List<TripEntity>> = flowOf(emptyList())
        override fun getLastTrip(): Flow<TripEntity?> = flowOf(null)
        override fun getRecent(limit: Int): Flow<List<TripEntity>> = flowOf(emptyList())
        override suspend fun getByBydId(bydId: Long): TripEntity? = null
        override suspend fun getTripsWithoutCost(): List<TripEntity> = emptyList()
        override suspend fun getPeriodSummary(from: Long, to: Long): TripSummary = TripSummary(0.0, 0.0)
        override suspend fun getByStartTsRange(minTs: Long, maxTs: Long): TripEntity? = null
        override suspend fun getAllSnapshot(): List<TripEntity> = emptyList()
        override suspend fun getRecentForEma(limit: Int): List<TripEntity> = emptyList()
        override suspend fun getRecentForEmaFiltered(minKm: Double, limit: Int): List<TripEntity> = emptyList()
    }

    private class StubTripPointDao : TripPointDao {
        override suspend fun insertAll(points: List<TripPointEntity>) {}
        override suspend fun getByTripIds(tripIds: List<Long>): List<TripPointEntity> = emptyList()
        override suspend fun getCount(): Int = 0
        override suspend fun getAll(): List<TripPointEntity> = emptyList()
        override suspend fun thinOldPoints(cutoff: Long, intervalMs: Long): Int = 0
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
        override suspend fun getTodayDrainHours(dayStart: Long, dayEnd: Long): Double = 0.0
    }

    private class StubBatterySnapshotDao : BatterySnapshotDao {
        override fun getAll(): Flow<List<BatterySnapshotEntity>> = flowOf(emptyList())
        override fun getRecent(limit: Int): Flow<List<BatterySnapshotEntity>> = flowOf(emptyList())
        override suspend fun insert(snapshot: BatterySnapshotEntity): Long = 0L
        override suspend fun getLast(): BatterySnapshotEntity? = null
    }

    private class StubChargeDao(initialLastCharge: ChargeEntity? = null) : ChargeDao {
        private val lastCharge = MutableStateFlow(initialLastCharge)
        override suspend fun insert(charge: ChargeEntity): Long = 0L
        override suspend fun update(charge: ChargeEntity) {}
        override fun getAll(): Flow<List<ChargeEntity>> = flowOf(emptyList())
        override fun getByDateRange(from: Long, to: Long): Flow<List<ChargeEntity>> = flowOf(emptyList())
        override fun getLastCharge(): Flow<ChargeEntity?> = lastCharge
        override suspend fun getAllAutoserviceCharges(): List<ChargeEntity> = emptyList()
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
        fakeAutoservice: VehicleApi,
        onDemandTelemetryReader: OnDemandVehicleTelemetryReader = FakeOnDemandTelemetryReader(),
        initialSettings: Map<String, String> = emptyMap(),
        settingsDao: FakeSettingsDao = FakeSettingsDao(),
        vehicleStateRepository: FakeVehicleStateRepository = FakeVehicleStateRepository(),
        latestCharge: ChargeEntity? = null,
    ): DashboardViewModel {
        settingsDao.map.putAll(initialSettings)
        val settingsRepo = SettingsRepository(settingsDao)

        val tripDao = StubTripDao()
        val tripPointDao = StubTripPointDao()
        val tripRepo = TripRepository(tripDao, tripPointDao)
        val chargeDao = StubChargeDao(latestCharge)
        val chargeRepo = ChargeRepository(chargeDao)

        val idleDrainDao = StubIdleDrainDao()
        val batterySnapshotDao = StubBatterySnapshotDao()

        val trackingStateRepository = FakeTrackingStateRepository()
        val localInsightStatsAggregator = LocalInsightStatsAggregator(
            tripDao = tripDao,
            chargeDao = chargeDao,
            idleDrainDao = idleDrainDao,
            batterySnapshotDao = batterySnapshotDao,
            vehicleApi = fakeAutoservice,
            vehicleStateRepository = vehicleStateRepository,
        )

        return DashboardViewModel(
            tripRepository = tripRepo,
            chargeRepository = chargeRepo,
            settingsRepository = settingsRepo,
            idleDrainDao = idleDrainDao,
            vehicleStateRepository = vehicleStateRepository,
            trackingStateRepository = trackingStateRepository,
            consumptionAggregator = ConsumptionAggregator(),
            localInsightStatsAggregator = localInsightStatsAggregator,
            localInsightEngine = LocalInsightEngine(),
            onDemandTelemetryReader = onDemandTelemetryReader,
            energyFlowStatsRepository = FakeEnergyFlowStatsRepository(),
        )
    }

    // --- Tests ---

    @Test
    fun `remaining energy uses configured capacities without extra vehicle reads`() = runTest {
        val vehicleStateRepository = FakeVehicleStateRepository()
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(sampleReading),
            initialSettings = mapOf(
                SettingsRepository.KEY_BATTERY_CAPACITY to "72.9",
                SettingsRepository.KEY_FUEL_TANK_CAPACITY to "60",
            ),
            vehicleStateRepository = vehicleStateRepository,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vehicleStateRepository.update(diParsData(soc = 50).copy(fuelLevel = 40))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(36.45, vm.uiState.value.batteryRemainingKwh ?: 0.0, 0.001)
        assertEquals(24.0, vm.uiState.value.fuelRemainingLiters ?: 0.0, 0.001)
    }

    @Test
    fun `remaining amount rejects missing capacity and invalid percent`() {
        assertNull(remainingAmount(50, null))
        assertNull(remainingAmount(50, 0.0))
        assertNull(remainingAmount(-1, 72.9))
        assertNull(remainingAmount(101, 72.9))
    }

    @Test
    fun `saving fuel capacity updates remaining liters without recreating home view model`() = runTest {
        val settingsDao = FakeSettingsDao()
        val vehicleStateRepository = FakeVehicleStateRepository()
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(sampleReading),
            settingsDao = settingsDao,
            vehicleStateRepository = vehicleStateRepository,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vehicleStateRepository.update(diParsData(soc = 50).copy(fuelLevel = 40))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.fuelRemainingLiters)

        settingsDao.set(SettingEntity(SettingsRepository.KEY_FUEL_TANK_CAPACITY, "60"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(24.0, vm.uiState.value.fuelRemainingLiters ?: 0.0, 0.001)
    }

    @Test
    fun `latest charge is exposed for the home screen`() = runTest {
        val charge = ChargeEntity(
            id = 42L,
            startTs = 1_700_000_000_000L,
            endTs = 1_700_003_600_000L,
            socStart = 25,
            socEnd = 80,
            kwhCharged = 41.2,
        )
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(sampleReading),
            latestCharge = charge,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(charge, vm.uiState.value.latestCharge)
    }

    private val sampleReading = BatteryReading(
        sohPercent = 100f,
        socPercent = 91f,
        lifetimeKwh = 602f,
        lifetimeMileageKm = 2091f,
        voltage12v = 14f,
        readAtMs = 0L
    )

    @Test
    fun `home telemetry reads tire states only while active`() = runTest {
        val telemetryReader = FakeOnDemandTelemetryReader(
            diParsData(
                tireTempFL = 31,
                tireTempFR = 32,
                tireTempRL = 33,
                tireTempRR = 34,
                tireStateFL = 0,
                tireStateRR = 2,
            ),
        )
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(sampleReading),
            onDemandTelemetryReader = telemetryReader,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setHomeTelemetryActive(true)
        testDispatcher.scheduler.runCurrent()

        assertEquals(31, vm.uiState.value.tireTempFL)
        assertEquals(34, vm.uiState.value.tireTempRR)
        assertEquals(2, vm.uiState.value.tireStateRR)
        vm.setHomeTelemetryActive(false)
    }

    @Test
    fun `powertrain telemetry is lifecycle controlled`() = runTest {
        val telemetryReader = FakeOnDemandTelemetryReader(
            diParsData(
                frontMotorSpeed = 12_345,
                rearMotorSpeed = 8_765,
                frontMotorCurrent = 12.3,
                waterTemp = 92,
            ),
        )
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(sampleReading),
            onDemandTelemetryReader = telemetryReader,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setPowertrainTelemetryActive(true)
        testDispatcher.scheduler.runCurrent()

        assertEquals(12_345, vm.uiState.value.frontMotorSpeed)
        assertEquals(8_765, vm.uiState.value.rearMotorSpeed)
        assertEquals(12.3, vm.uiState.value.frontMotorCurrent ?: 0.0, 0.001)
        assertEquals(92, vm.uiState.value.waterTemp)
        vm.setPowertrainTelemetryActive(false)
    }

    @Test
    fun `currentSoh is populated from shared full snapshot`() = runTest {
        val vehicleStateRepository = FakeVehicleStateRepository()
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(sampleReading, available = true),
            vehicleStateRepository = vehicleStateRepository,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vehicleStateRepository.update(
            diParsData(soc = 91).copy(
                soh = 100,
                mileage = 2091.0,
                totalElecConsumption = 602.0,
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(100f, vm.uiState.value.currentSoh!!, 0.01f)
        assertEquals(2091f, vm.uiState.value.currentLifetimeKm!!, 0.01f)
        assertEquals(602f, vm.uiState.value.currentLifetimeKwh!!, 0.01f)
    }

    @Test
    fun `fresh snapshot clears unavailable drive and work modes instead of showing stale values`() = runTest {
        val vehicleStateRepository = FakeVehicleStateRepository()
        val vm = buildViewModel(
            fakeAutoservice = FakeAutoservice(sampleReading, available = true),
            vehicleStateRepository = vehicleStateRepository,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vehicleStateRepository.update(diParsData(soc = 80, driveMode = 2, workMode = 3))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.driveMode)
        assertEquals(3, vm.uiState.value.workMode)

        vehicleStateRepository.update(diParsData(soc = 79, driveMode = null, workMode = null))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.driveMode)
        assertNull(vm.uiState.value.workMode)
    }
}
