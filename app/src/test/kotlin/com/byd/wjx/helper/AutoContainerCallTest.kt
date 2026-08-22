package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoContainerCallTest {
    @Test fun `uses snake case service first`() {
        val calls = mutableListOf<Pair<String, String>>()
        val ok = autoContainerCall(16) { script, argument ->
            calls += script to argument
            0
        }
        assertTrue(ok)
        assertEquals(1, calls.size)
        assertTrue(calls.single().first.contains("auto_container"))
        assertEquals("16", calls.single().second)
    }

    @Test fun `falls back to camel case service`() {
        val calls = mutableListOf<String>()
        val ok = autoContainerCall(18) { script, _ ->
            calls += script
            if (calls.size == 1) 1 else 0
        }
        assertTrue(ok)
        assertEquals(2, calls.size)
        assertTrue(calls[1].contains("AutoContainer"))
    }

    @Test fun `rejects commands outside the compositor allowlist`() {
        assertThrows(IllegalArgumentException::class.java) {
            autoContainerCall(99) { _, _ -> 0 }
        }
        assertFalse(autoContainerCall(0) { _, _ -> 1 })
    }
}
