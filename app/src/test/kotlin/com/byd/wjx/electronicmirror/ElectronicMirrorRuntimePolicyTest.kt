package com.byd.wjx.electronicmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicMirrorRuntimePolicyTest {
    @Test
    fun `retry uses bounded exponential backoff`() {
        assertEquals(1_000L, electronicMirrorRetryDelayMs(1))
        assertEquals(2_000L, electronicMirrorRetryDelayMs(2))
        assertEquals(4_000L, electronicMirrorRetryDelayMs(3))
        assertEquals(8_000L, electronicMirrorRetryDelayMs(4))
        assertEquals(16_000L, electronicMirrorRetryDelayMs(5))
        assertEquals(16_000L, electronicMirrorRetryDelayMs(20))
    }

    @Test
    fun `cluster display matcher covers known BYD projection names`() {
        assertTrue(electronicMirrorDisplayRank("XDJAScreenProjection_1") > 0)
        assertTrue(electronicMirrorDisplayRank("fission_cluster") > 0)
        assertTrue(electronicMirrorDisplayRank("叠加视图 #1") > 0)
        assertEquals(0, electronicMirrorDisplayRank("Built-in Screen"))
    }
}
