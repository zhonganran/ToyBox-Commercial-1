package com.byd.wjx.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ProHomeFormattingTest {

    @Test
    fun `cell voltage delta stays in volts`() {
        assertEquals("0.012 V", formatCellVoltageDelta(0.012))
        assertEquals("--", formatCellVoltageDelta(null))
    }

    @Test
    fun `duration is compact and readable`() {
        assertEquals("48分", formatDuration(48 * 60_000L))
        assertEquals("1小时12分", formatDuration(72 * 60_000L))
        assertEquals("--", formatDuration(-1L))
    }
}
