package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedResourceRegistryTest {
    @Test
    fun `release removes ownership before invoking resource cleanup`() {
        val released = mutableListOf<String>()
        val registry = OwnedResourceRegistry<Int, String> { released += it }
        registry.register(7, "display-7")

        assertTrue(registry.release(7))
        assertFalse(registry.release(7))
        assertEquals(listOf("display-7"), released)
    }

    @Test
    fun `release of an unknown resource is idempotent`() {
        var releaseCount = 0
        val registry = OwnedResourceRegistry<Int, String> { releaseCount++ }

        assertFalse(registry.release(42))
        assertEquals(0, releaseCount)
    }
}
