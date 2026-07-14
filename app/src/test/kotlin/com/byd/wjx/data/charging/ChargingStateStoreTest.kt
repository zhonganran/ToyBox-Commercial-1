package com.byd.wjx.data.charging

import com.byd.wjx.data.local.entity.SettingEntity
import com.byd.wjx.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingStateStoreTest {

    private class FakeSettingsDao(initial: Map<String, String> = emptyMap()) :
        com.byd.wjx.data.local.dao.SettingsDao {
        private val map = mutableMapOf<String, String>().also { it.putAll(initial) }
        override suspend fun get(key: String): String? = map[key]
        override fun observe(key: String): Flow<String?> = flowOf(map[key])
        override suspend fun set(entity: SettingEntity) { map[entity.key] = entity.value ?: "" }
        override suspend fun setAll(settings: List<SettingEntity>) { settings.forEach { set(it) } }
        override fun getAll(): Flow<List<SettingEntity>> = flowOf(emptyList())
    }

    private fun store(initial: Map<String, String> = emptyMap()): ChargingStateStore {
        val dao = FakeSettingsDao(initial)
        val settings = SettingsRepository(dao)
        return ChargingStateStore(settings)
    }

    @Test
    fun `load on empty settings returns State with all nulls and zero ts`() = runTest {
        val s = store()
        val state = s.load()
        assertNull(state.socPercent)
        assertNull(state.mileageKm)
        assertNull(state.capacityKwh)
        assertEquals(0L, state.ts)
    }

    @Test
    fun `save then load returns same values`() = runTest {
        val s = store()
        s.save(socPercent = 85, mileageKm = 10500.5f, capacityKwh = 7.8f, ts = 1700000000000L)
        val state = s.load()
        assertEquals(85, state.socPercent)
        assertEquals(10500.5f, state.mileageKm!!, 0.01f)
        assertEquals(7.8f, state.capacityKwh!!, 0.01f)
        assertEquals(1700000000000L, state.ts)
    }

    @Test
    fun `save with null socPercent does not overwrite existing soc`() = runTest {
        val initial = mapOf(SettingsRepository.KEY_CHARGING_BASELINE_SOC to "80")
        val s = store(initial)
        // Save with null soc — should not overwrite the pre-existing 80
        s.save(socPercent = null, mileageKm = 100f, capacityKwh = 5f, ts = 1000L)
        val state = s.load()
        assertEquals(80, state.socPercent)
    }

    @Test
    fun `chargePending defaults to false and round-trips`() = runTest {
        val s = store()
        assertFalse(s.loadChargePending())
        s.setChargePending(true)
        assertTrue(s.loadChargePending())
        s.setChargePending(false)
        assertFalse(s.loadChargePending())
    }

    @Test
    fun `save with null capacityKwh persists as null`() = runTest {
        val s = store()
        s.save(socPercent = 70, mileageKm = null, capacityKwh = null, ts = 2000L)
        val state = s.load()
        assertNull(state.mileageKm)
        assertNull(state.capacityKwh)
        assertEquals(2000L, state.ts)
    }

    @Test
    fun `save writes the whole anchor in a single setAll batch`() = runTest {
        // A process kill mid-save must never leave a mixed anchor (new mileage
        // + stale SOC → false odometerMoved). Room makes a list-@Upsert one
        // transaction, so save() must issue exactly one setAll and no set().
        class RecordingDao : com.byd.wjx.data.local.dao.SettingsDao {
            val map = mutableMapOf<String, String>()
            var setCalls = 0
            var setAllCalls = 0
            override suspend fun get(key: String): String? = map[key]
            override fun observe(key: String): Flow<String?> = flowOf(map[key])
            override suspend fun set(entity: SettingEntity) {
                setCalls++; map[entity.key] = entity.value ?: ""
            }
            override suspend fun setAll(settings: List<SettingEntity>) {
                setAllCalls++; settings.forEach { map[it.key] = it.value ?: "" }
            }
            override fun getAll(): Flow<List<SettingEntity>> = flowOf(emptyList())
        }
        val dao = RecordingDao()
        val s = ChargingStateStore(SettingsRepository(dao))
        s.save(socPercent = 85, mileageKm = 100f, capacityKwh = 5f, ts = 1000L)
        assertEquals(1, dao.setAllCalls)
        assertEquals(0, dao.setCalls)
    }

    @Test
    fun `parked anchor skips noisy writes but persists soc and meaningful mileage changes`() = runTest {
        class RecordingDao : com.byd.wjx.data.local.dao.SettingsDao {
            val map = mutableMapOf<String, String>()
            var setAllCalls = 0
            override suspend fun get(key: String): String? = map[key]
            override fun observe(key: String): Flow<String?> = flowOf(map[key])
            override suspend fun set(entity: SettingEntity) { map[entity.key] = entity.value ?: "" }
            override suspend fun setAll(settings: List<SettingEntity>) {
                setAllCalls++
                settings.forEach { map[it.key] = it.value ?: "" }
            }
            override fun getAll(): Flow<List<SettingEntity>> = flowOf(emptyList())
        }
        val dao = RecordingDao()
        val store = ChargingStateStore(SettingsRepository(dao))
        store.save(socPercent = 80, mileageKm = 100f, capacityKwh = null, ts = 1_000L)

        store.saveParkedAnchor(socPercent = 80, mileageKm = 100.05f, ts = 2_000L)
        assertEquals(1, dao.setAllCalls)

        store.saveParkedAnchor(socPercent = 79, mileageKm = 100.05f, ts = 3_000L)
        assertEquals(2, dao.setAllCalls)

        store.saveParkedAnchor(socPercent = 79, mileageKm = 100.3f, ts = 4_000L)
        assertEquals(3, dao.setAllCalls)
    }
}
