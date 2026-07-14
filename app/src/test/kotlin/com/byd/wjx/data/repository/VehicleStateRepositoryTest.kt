package com.byd.wjx.data.repository

import com.byd.wjx.data.remote.diParsData
import com.byd.wjx.data.vehicle.VehicleReadSource
import com.byd.wjx.data.vehicle.VehicleSnapshot
import com.byd.wjx.data.vehicle.VehicleSnapshotKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleStateRepositoryTest {

    @Test
    fun `repository combines full action and connection state`() = runTest {
        val full = MutableStateFlow<VehicleSnapshot?>(null)
        val action = MutableStateFlow<VehicleSnapshot?>(null)
        val connected = MutableStateFlow(false)
        val repository = DefaultVehicleStateRepository(
            fullSnapshot = full,
            actionSnapshot = action,
            connected = connected,
            applicationScope = backgroundScope,
        )
        val fullValue = snapshot(
            soc = 72,
            completedAtMs = 1_000L,
            kind = VehicleSnapshotKind.FULL,
        )
        val actionValue = snapshot(
            soc = 73,
            completedAtMs = 1_100L,
            kind = VehicleSnapshotKind.ACTION,
        )

        full.value = fullValue
        action.value = actionValue
        connected.value = true
        runCurrent()

        assertEquals(72, repository.state.value.data?.soc)
        assertEquals(73, repository.state.value.actionData?.soc)
        assertEquals(1_000L, repository.state.value.lastUpdatedAtMs)
        assertEquals(true, repository.state.value.connected)
    }

    @Test
    fun `freshness distinguishes unavailable stale and disconnected snapshots`() {
        val now = 100_000L
        assertEquals(
            VehicleDataFreshness.UNAVAILABLE,
            VehicleState().freshnessAt(now),
        )
        val recent = VehicleState(
            fullSnapshot = snapshot(50, now - 1_000L, VehicleSnapshotKind.FULL),
            connected = true,
        )
        assertEquals(VehicleDataFreshness.FRESH, recent.freshnessAt(now))

        val stale = recent.copy(
            fullSnapshot = snapshot(50, now - 70_000L, VehicleSnapshotKind.FULL),
        )
        assertEquals(VehicleDataFreshness.STALE, stale.freshnessAt(now))
        assertEquals(
            VehicleDataFreshness.DISCONNECTED,
            recent.copy(connected = false).freshnessAt(now),
        )
    }

    private fun snapshot(
        soc: Int,
        completedAtMs: Long,
        kind: VehicleSnapshotKind,
    ) = VehicleSnapshot(
        data = diParsData(soc = soc),
        kind = kind,
        source = VehicleReadSource.HELPER_BINDER,
        readStartedAtMs = completedAtMs - 10L,
        readCompletedAtMs = completedAtMs,
        requestedFieldCount = 1,
        transportSuccessCount = 1,
        decodedFieldCount = 1,
    )
}
