package com.byd.wjx.cluster

import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterOverlayEpochTest {
    @Test
    fun `overlay epoch advances monotonically`() {
        val before = ClusterOverlayEpoch.current()
        val first = ClusterOverlayEpoch.markChanged()
        val second = ClusterOverlayEpoch.markChanged()

        assertTrue(first > before)
        assertTrue(second > first)
        assertTrue(ClusterOverlayEpoch.current() >= second)
    }
}
