package com.byd.wjx.cluster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringWheelButtonsTest {

    @Test fun `name table covers our and validated keycodes`() {
        val expected = setOf(
            80, 87, 88, 289, 291, 292, 294, 304, 305,
            307, 308, 309, 310, 318, 319, 320, 321, 322, 328,
            351, 352, 353, 383, 384, 386,
        )
        assertEquals(expected, KNOWN_BUTTON_NAMES.keys)
    }

    @Test fun `every known keycode maps to a real string resource`() {
        // R.string ids are non-zero generated ints; 0 would mean a missing resource reference.
        assertTrue(KNOWN_BUTTON_NAMES.values.all { it != 0 })
    }
}
