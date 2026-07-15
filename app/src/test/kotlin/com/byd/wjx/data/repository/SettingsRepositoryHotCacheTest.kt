package com.byd.wjx.data.repository

import com.byd.wjx.data.local.dao.SettingsDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRepositoryHotCacheTest {

    @Test
    fun `battery capacity hot reads hit Room once and update after write`() = runTest {
        val dao = mockk<SettingsDao>(relaxed = true)
        coEvery { dao.get(SettingsRepository.KEY_BATTERY_CAPACITY) } returns "71.8"
        val repository = SettingsRepository(dao)

        assertEquals(71.8, repository.getBatteryCapacity(), 0.001)
        assertEquals(71.8, repository.getBatteryCapacity(), 0.001)
        coVerify(exactly = 1) { dao.get(SettingsRepository.KEY_BATTERY_CAPACITY) }

        repository.setString(SettingsRepository.KEY_BATTERY_CAPACITY, "75.0")
        assertEquals(75.0, repository.getBatteryCapacity(), 0.001)
        coVerify(exactly = 1) { dao.get(SettingsRepository.KEY_BATTERY_CAPACITY) }
    }

    @Test
    fun `trip gps hot reads use cached value after setting change`() = runTest {
        val dao = mockk<SettingsDao>(relaxed = true)
        coEvery { dao.get(SettingsRepository.KEY_RECORD_TRIP_GPS) } returns "true"
        val repository = SettingsRepository(dao)

        assertEquals(true, repository.isTripGpsRecordingEnabled())
        repository.setTripGpsRecordingEnabled(false)
        assertFalse(repository.isTripGpsRecordingEnabled())
        coVerify(exactly = 1) { dao.get(SettingsRepository.KEY_RECORD_TRIP_GPS) }
    }

    @Test
    fun `fuel tank capacity hot reads hit Room once and reject invalid values`() = runTest {
        val dao = mockk<SettingsDao>(relaxed = true)
        coEvery { dao.get(SettingsRepository.KEY_FUEL_TANK_CAPACITY) } returns "60"
        val repository = SettingsRepository(dao)

        assertEquals(60.0, repository.getFuelTankCapacity() ?: 0.0, 0.001)
        assertEquals(60.0, repository.getFuelTankCapacity() ?: 0.0, 0.001)
        coVerify(exactly = 1) { dao.get(SettingsRepository.KEY_FUEL_TANK_CAPACITY) }

        repository.setString(SettingsRepository.KEY_FUEL_TANK_CAPACITY, "0")
        assertNull(repository.getFuelTankCapacity())
        coVerify(exactly = 1) { dao.get(SettingsRepository.KEY_FUEL_TANK_CAPACITY) }
    }

    @Test
    fun `invalid tariffs fall back to the displayed commercial defaults`() = runTest {
        val dao = mockk<SettingsDao>(relaxed = true)
        coEvery { dao.get(SettingsRepository.KEY_HOME_TARIFF) } returns "invalid"
        coEvery { dao.get(SettingsRepository.KEY_DC_TARIFF) } returns "invalid"
        val repository = SettingsRepository(dao)

        assertEquals(SettingsRepository.DEFAULT_HOME_TARIFF.toDouble(), repository.getHomeTariff(), 0.001)
        assertEquals(SettingsRepository.DEFAULT_DC_TARIFF.toDouble(), repository.getDcTariff(), 0.001)
    }

    @Test
    fun `invalid theme mode falls back to system adaptive mode`() = runTest {
        val dao = mockk<SettingsDao>(relaxed = true)
        coEvery { dao.get(SettingsRepository.KEY_THEME_MODE) } returns "sepia"
        val repository = SettingsRepository(dao)

        assertEquals(SettingsRepository.DEFAULT_THEME_MODE, repository.getThemeMode())
    }
}
