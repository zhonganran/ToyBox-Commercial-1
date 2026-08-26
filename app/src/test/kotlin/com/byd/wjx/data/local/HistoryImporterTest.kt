package com.byd.wjx.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.byd.wjx.data.local.dao.IdleDrainDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.local.dao.TripPointDao
import com.byd.wjx.data.repository.LastSessionRepository
import com.byd.wjx.data.repository.SettingsRepository
import com.byd.wjx.data.repository.TripRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryImporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val energyReader = mockk<EnergyDataReader>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)

    @Test
    fun `unchanged energy source skips reads and watermark writes`() = runTest {
        every { energyReader.peekSourceChanged(any()) } returns false

        val result = importer().syncFromEnergyData()

        assertEquals("没有变化", result.details)
        coVerify(exactly = 0) { energyReader.readTripsSince(any()) }
        verify(exactly = 0) { energyReader.markSourceProcessed(any()) }
    }

    @Test
    fun `failed energy import does not advance source watermark`() = runTest {
        every { energyReader.peekSourceChanged(any()) } returns true
        coEvery { settings.getLastEnergyImportTs() } returns 0L
        coEvery { energyReader.readTripsSince(0L) } throws EnergyDataException("broken database")

        val result = importer().syncFromEnergyData()

        assertTrue(result.isError)
        assertEquals("broken database", result.error)
        verify(exactly = 0) { energyReader.markSourceProcessed(any()) }
    }

    @Test
    fun `successful empty import commits source watermark`() = runTest {
        every { energyReader.peekSourceChanged(any()) } returns true
        coEvery { settings.getLastEnergyImportTs() } returns 10L
        coEvery { settings.getFuelPricePerLiter() } returns 7.5
        coEvery { energyReader.readTripsSince(10L) } returns emptyList()

        val result = importer().syncFromEnergyData()

        assertEquals("没有新的记录", result.details)
        verify(exactly = 1) { energyReader.markSourceProcessed(any()) }
    }

    @Test
    fun `failed DiPlus read does not advance source watermark`() = runTest {
        val diPlus = mockk<DiPlusDbReader>(relaxed = true)
        every { diPlus.isAvailable() } returns true
        every { diPlus.peekSourceChanged() } returns true
        coEvery { diPlus.readTripInfo() } throws EnergyDataException("damaged TripInfo")

        val result = importer(diPlus).syncFromDiPlus()

        assertTrue(result.isError)
        assertEquals("damaged TripInfo", result.error)
        verify(exactly = 0) { diPlus.markSourceProcessed() }
    }

    private fun importer(
        diPlusDbReader: DiPlusDbReader = mockk(relaxed = true),
    ) = HistoryImporter(
        context = context,
        energyDataReader = energyReader,
        tripRepository = mockk<TripRepository>(relaxed = true),
        tripDao = mockk<TripDao>(relaxed = true),
        tripPointDao = mockk<TripPointDao>(relaxed = true),
        idleDrainDao = mockk<IdleDrainDao>(relaxed = true),
        settingsRepository = settings,
        lastSessionRepository = mockk<LastSessionRepository>(relaxed = true),
        diPlusDbReader = diPlusDbReader,
    )
}
