package com.byd.wjx.data.loop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotPersistenceGateTest {

    private val base = SnapshotPersistenceSample(
        soc = 80,
        mileageKm = 100.0,
        totalEnergyKwh = 20.0,
        ignition = 2,
        energyDataAvailable = true,
    )

    @Test fun `first sample persists and identical ticks are throttled`() {
        val gate = SnapshotPersistenceGate(maxIntervalMs = 5_000L)

        assertTrue(gate.shouldPersist(base, 1_000L))
        gate.markPersisted(base, 1_000L)

        assertFalse(gate.shouldPersist(base, 5_999L))
        assertTrue(gate.shouldPersist(base, 6_000L))
    }

    @Test fun `important state edges persist immediately`() {
        val gate = SnapshotPersistenceGate(maxIntervalMs = 5_000L)
        gate.markPersisted(base, 1_000L)

        assertTrue(gate.shouldPersist(base.copy(soc = 79), 1_100L))
        assertTrue(gate.shouldPersist(base.copy(ignition = 0), 1_100L))
        assertTrue(gate.shouldPersist(base.copy(energyDataAvailable = false), 1_100L))
    }

    @Test fun `small analog drift is coalesced but meaningful movement persists`() {
        val gate = SnapshotPersistenceGate(
            maxIntervalMs = 5_000L,
            mileageDeltaKm = 0.1,
            totalEnergyDeltaKwh = 0.05,
        )
        gate.markPersisted(base, 1_000L)

        assertFalse(gate.shouldPersist(base.copy(mileageKm = 100.09), 1_100L))
        assertFalse(gate.shouldPersist(base.copy(totalEnergyKwh = 20.049), 1_100L))
        assertTrue(gate.shouldPersist(base.copy(mileageKm = 100.1), 1_100L))
        assertTrue(gate.shouldPersist(base.copy(totalEnergyKwh = 20.05), 1_100L))
    }

    @Test fun `clock rollback forces a recovery checkpoint`() {
        val gate = SnapshotPersistenceGate(maxIntervalMs = 5_000L)
        gate.markPersisted(base, 10_000L)

        assertTrue(gate.shouldPersist(base, 9_000L))
    }
}
