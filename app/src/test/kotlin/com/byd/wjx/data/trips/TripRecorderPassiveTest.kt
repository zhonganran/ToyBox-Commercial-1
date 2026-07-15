package com.byd.wjx.data.trips

import com.byd.wjx.data.local.EnergyDataReader
import com.byd.wjx.data.local.dao.LastStateDao
import com.byd.wjx.data.local.dao.TripDao
import com.byd.wjx.data.remote.diParsData
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorderPassiveTest {
    @Test fun `passive mode never inserts trip rows nor writes open trip to last_state`() = runTest {
        val tripDao = mockk<TripDao>(relaxed = true)
        val lastState = mockk<LastStateDao>(relaxed = true)
        val energy = mockk<EnergyDataReader> { every { isAvailable() } returns true }
        val recorder = TripRecorder(tripDao, lastState, energy, batteryCapacityKwh = { 72.9 })

        recorder.consume(diParsData(powerState = 1, soc = 80, mileage = 100.0))   // ON-idle
        recorder.consume(diParsData(powerState = 3, soc = 80, mileage = 100.0))   // OK / drive-ready
        recorder.consume(diParsData(powerState = 3, soc = 70, mileage = 110.0))   // driving
        recorder.consume(diParsData(powerState = 1, soc = 70, mileage = 110.0))   // back to ON-idle

        coVerify(exactly = 0) { tripDao.insert(any()) }
        coVerify(exactly = 0) { lastState.openTrip(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { lastState.clearOpenTrip() }
    }
}
