package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperClusterContainerTest {
    @Test
    fun `enable sends only command 16`() {
        val commands = mutableListOf<Int>()

        assertTrue(setClusterContainerMode(true, call = { commands += it; true }, pause = {}))
        assertEquals(listOf(16), commands)
    }

    @Test
    fun `disable detaches waits and then disables`() {
        val events = mutableListOf<String>()

        assertTrue(
            setClusterContainerMode(
                false,
                call = { events += "call:$it"; true },
                pause = { events += "wait:$it" },
            ),
        )
        assertEquals(listOf("call:18", "wait:1000", "call:0"), events)
    }

    @Test
    fun `service name fallback is used only after primary failure`() {
        val scripts = mutableListOf<String>()
        val ok = autoContainerCall(16) { script, _ ->
            scripts += script
            if (scripts.size == 1) 1 else 0
        }

        assertTrue(ok)
        assertEquals(2, scripts.size)
        assertTrue(scripts.first().contains("auto_container"))
        assertTrue(scripts.last().contains("AutoContainer"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unlisted compositor command is rejected`() {
        autoContainerCall(17) { _, _ -> 0 }
    }

    @Test
    fun `failed detach still attempts final disable`() {
        val commands = mutableListOf<Int>()
        val ok = setClusterContainerMode(
            false,
            call = { command -> commands += command; command == 0 },
            pause = {},
        )

        assertFalse(ok)
        assertEquals(listOf(18, 0), commands)
    }
}
