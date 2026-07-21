package com.byd.wjx.rotation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRotationConfigTest {
    private val target = AppRotationTarget("com.example.video", "视频")

    @Test
    fun `only an enabled matching package requests landscape`() {
        val enabled = AppRotationConfig(enabled = true, targets = listOf(target))

        assertTrue(enabled.shouldForceLandscape("com.example.video"))
        assertFalse(enabled.shouldForceLandscape("com.example.music"))
        assertFalse(enabled.shouldForceLandscape(null))
        assertFalse(enabled.copy(enabled = false).shouldForceLandscape("com.example.video"))
    }
}
