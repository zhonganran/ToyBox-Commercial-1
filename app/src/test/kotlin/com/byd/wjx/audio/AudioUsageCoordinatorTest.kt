package com.byd.wjx.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioUsageCoordinatorTest {
    @Test
    fun tracksIndependentActiveOwners() {
        val coordinator = AudioUsageCoordinator()
        coordinator.setActive(AudioOwner.RealtimeHailing, true)
        coordinator.setActive(AudioOwner.ExteriorPlayback, true)

        assertTrue(coordinator.isAnyActive())
        coordinator.setActive(AudioOwner.RealtimeHailing, false)
        assertTrue(coordinator.isAnyActive())
        coordinator.setActive(AudioOwner.ExteriorPlayback, false)
        assertFalse(coordinator.isAnyActive())
    }

    @Test
    fun waitCompletesOnlyAfterRequestedOwnersRelease() = runBlocking {
        val coordinator = AudioUsageCoordinator()
        coordinator.setActive(AudioOwner.RealtimeHailing, true)
        coordinator.setActive(AudioOwner.ExteriorPlayback, true)

        assertFalse(
            coordinator.awaitInactive(
                owners = setOf(AudioOwner.RealtimeHailing),
                timeoutMs = 20L,
            ),
        )
        coordinator.setActive(AudioOwner.RealtimeHailing, false)
        assertTrue(
            coordinator.awaitInactive(
                owners = setOf(AudioOwner.RealtimeHailing),
                timeoutMs = 20L,
            ),
        )
        assertTrue(coordinator.isActive(AudioOwner.ExteriorPlayback))
    }
}
