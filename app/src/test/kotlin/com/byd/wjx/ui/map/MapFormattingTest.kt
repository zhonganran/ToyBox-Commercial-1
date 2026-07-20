package com.byd.wjx.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapFormattingTest {
    @Test
    fun `formats trip duration for summary and rows`() {
        assertEquals("0分钟", formatTripDuration(0L))
        assertEquals("42分钟", formatTripDuration(42L * 60_000L))
        assertEquals("1小时30分", formatTripDuration(90L * 60_000L))
    }
}
