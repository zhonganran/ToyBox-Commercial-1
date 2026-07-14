package com.byd.wjx.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingStateRepositoryTest {

    @Test
    fun `store publishes service trip range and camera state`() = runTest {
        val store = TrackingStateStore(backgroundScope)

        store.setRunning(true)
        store.setRangeKm(418.5)
        store.setSessionStartedAt(1_234L)
        store.setTripDistanceKm(12.6)
        store.setCameraActive(true)
        runCurrent()

        assertEquals(true, store.state.value.isRunning)
        assertEquals(418.5, store.state.value.rangeKm)
        assertEquals(1_234L, store.state.value.sessionStartedAt)
        assertEquals(12.6, store.state.value.tripDistanceKm)
        assertEquals(true, store.state.value.cameraActive)
    }
}
