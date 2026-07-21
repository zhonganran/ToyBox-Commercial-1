package com.byd.wjx.statusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusBarWeatherRepositoryTest {
    @Test
    fun parser_readsOpenMeteoCurrentWeather() {
        val result = parseStatusBarWeather(
            """{"current":{"temperature_2m":25.6,"weather_code":2}}""",
            nowMs = 123L,
        )

        assertEquals(26, result?.temperatureC)
        assertEquals(2, result?.weatherCode)
        assertEquals("多云 26°C", result?.displayText)
        assertEquals(123L, result?.updatedAtMs)
    }

    @Test
    fun parser_rejectsMissingCurrentWeather() {
        assertNull(parseStatusBarWeather("{}", nowMs = 1L))
    }
}
