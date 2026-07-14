package com.byd.wjx.vehicle

import com.byd.wjx.audio.ExteriorChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class CarProfileDetectorTest {

    @Test
    fun `auto channel maps 23 controller to channel A`() {
        val profile = CarProfileDetector { name ->
            if (name == "apps.setting.product.outswver") "23.1.0" else ""
        }.detect()

        assertEquals(23, profile.series)
        assertEquals(ExteriorChannel.Channel0, profile.channel)
    }

    @Test
    fun `auto channel maps 34 and 57 controllers to channel B`() {
        listOf("34.0.0", "57.2.1").forEach { raw ->
            val profile = CarProfileDetector { name ->
                if (name == "apps.setting.product.outswver") raw else ""
            }.detect()

            assertEquals(raw.substringBefore(".").toInt(), profile.series)
            assertEquals(ExteriorChannel.Channel1, profile.channel)
        }
    }

    @Test
    fun `fallback properties can still identify controller series`() {
        val profile = CarProfileDetector { name ->
            when (name) {
                "apps.setting.product.outswver" -> ""
                "ro.build.display.id" -> "23-controller_release"
                else -> ""
            }
        }.detect()

        assertEquals(23, profile.series)
        assertEquals(ExteriorChannel.Channel0, profile.channel)
    }

    @Test
    fun `unknown controller falls back to channel B`() {
        val profile = CarProfileDetector { "" }.detect()

        assertEquals(0, profile.series)
        assertEquals(ExteriorChannel.Channel1, profile.channel)
    }

    @Test
    fun `unknown startup result is retried after short cache expires`() {
        var nowMs = 0L
        var controller = ""
        val detector = CarProfileDetector(
            propertyReader = { name ->
                if (name == "apps.setting.product.outswver") controller else ""
            },
            clock = { nowMs },
        )

        assertEquals(0, detector.detect().series)
        controller = "23.1.0"
        nowMs = 30_001L

        assertEquals(23, detector.detect().series)
        assertEquals(ExteriorChannel.Channel0, detector.detect().channel)
    }
}
