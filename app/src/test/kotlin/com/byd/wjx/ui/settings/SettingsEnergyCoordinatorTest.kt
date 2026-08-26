package com.byd.wjx.ui.settings

import com.byd.wjx.data.local.HistoryImporter
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsEnergyCoordinatorTest {
    @Test
    fun `capacity normalization rejects invalid values and persists a valid pair atomically`() = runTest {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val coordinator = coordinator(settingsRepository = settingsRepository)

        assertNull(coordinator.normalizeCapacities("0", "50"))
        assertNull(coordinator.normalizeCapacities("75", "invalid"))

        val normalized = requireNotNull(coordinator.normalizeCapacities(" 75,5 ", " 53,2 "))
        coordinator.saveCapacities(normalized)

        assertEquals(NormalizedEnergyCapacities("75.5", "53.2"), normalized)
        coVerify(exactly = 1) {
            settingsRepository.setStrings(
                match {
                    it == mapOf(
                        SettingsRepository.KEY_BATTERY_CAPACITY to "75.5",
                        SettingsRepository.KEY_FUEL_TANK_CAPACITY to "53.2",
                    )
                },
            )
        }
    }

    @Test
    fun `recalculation updates electric and fuel costs and skips empty trips`() = runTest {
        val settingsRepository = mockk<SettingsRepository>()
        val tripRepository = mockk<TripRepository>()
        val updatedTrips = mutableListOf<TripEntity>()
        coEvery { settingsRepository.getTripCostTariff() } returns 0.5
        coEvery { settingsRepository.getFuelPricePerLiter() } returns 8.0
        every { tripRepository.getAllTrips() } returns flowOf(
            listOf(
                TripEntity(startTs = 1L, kwhConsumed = 10.0),
                TripEntity(startTs = 2L, fuelConsumedL = 2.0),
                TripEntity(startTs = 3L),
            ),
        )
        coEvery { tripRepository.updateTrip(capture(updatedTrips)) } returns Unit
        val coordinator = coordinator(
            settingsRepository = settingsRepository,
            tripRepository = tripRepository,
        )

        val count = coordinator.recalculateAllCosts()

        assertEquals(2, count)
        assertEquals(5.0, updatedTrips[0].cost)
        assertEquals(16.0, updatedTrips[1].fuelCost)
        coVerify(exactly = 2) { tripRepository.updateTrip(any()) }
    }

    @Test
    fun `saving tariffs recalculates only missing historical costs`() = runTest {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val historyImporter = mockk<HistoryImporter>(relaxed = true)
        coEvery { settingsRepository.getTripCostTariff() } returns 0.42
        coEvery { settingsRepository.getFuelPricePerLiter() } returns 7.65
        val coordinator = coordinator(
            settingsRepository = settingsRepository,
            historyImporter = historyImporter,
        )

        coordinator.saveTariffs("0.3", "0.8", "7.65")

        coVerify(exactly = 1) {
            settingsRepository.setStrings(
                match {
                    it[SettingsRepository.KEY_HOME_TARIFF] == "0.3" &&
                        it[SettingsRepository.KEY_DC_TARIFF] == "0.8" &&
                        it[SettingsRepository.KEY_FUEL_PRICE_PER_LITER] == "7.65"
                },
            )
        }
        coVerify(exactly = 1) { historyImporter.calculateMissingCosts(0.42, 7.65) }
    }

    private fun coordinator(
        settingsRepository: SettingsRepository = mockk(relaxed = true),
        tripRepository: TripRepository = mockk(relaxed = true),
        historyImporter: HistoryImporter = mockk(relaxed = true),
    ) = SettingsEnergyCoordinator(settingsRepository, tripRepository, historyImporter)
}
