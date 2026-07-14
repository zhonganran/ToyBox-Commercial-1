package com.byd.wjx.data.charging

import com.byd.wjx.data.remote.diParsData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingCatchUpCoordinatorTest {

    @Test
    fun `startup catch-up and gun edge preserve peak charging power`() = runTest {
        val detector = mockk<AutoserviceChargingDetector>()
        val journal = mockk<CatchUpJournal>(relaxed = true)
        every { detector.onSample(any()) } returns Unit
        coEvery { detector.recordParkedAnchor(any(), any()) } returns false
        coEvery { detector.runCatchUp(any(), any()) } returnsMany listOf(
            CatchUpResult(CatchUpOutcome.BASELINE_INITIALIZED),
            CatchUpResult(CatchUpOutcome.SESSION_CREATED),
        )
        val coordinator = ChargingCatchUpCoordinator(detector, journal)
        coordinator.start(backgroundScope)
        runCurrent()

        coordinator.onSample(diParsData(soc = 50, chargeGunState = 2, power = -12.5))
        runCurrent()
        coordinator.onSample(diParsData(soc = 60, chargeGunState = 1, power = -4.0))
        runCurrent()

        verify(exactly = 2) { detector.onSample(any()) }
        coVerify(exactly = 1) { detector.runCatchUp(any(), null) }
        coVerify(exactly = 1) { detector.runCatchUp(any(), 12.5) }
    }
}
