package com.byd.wjx.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class OutsidePlaybackPlayerTest {
    @Test
    fun `channel zero keeps the higher calibrated gain`() {
        assertEquals(8_400, outsidePlaybackEnhancerGain(ExteriorChannel.Channel0, 100))
        assertEquals(3_200, outsidePlaybackEnhancerGain(ExteriorChannel.Channel1, 100))
    }

    @Test
    fun `gain follows configured playback volume`() {
        assertEquals(4_200, outsidePlaybackEnhancerGain(ExteriorChannel.Channel0, 50))
        assertEquals(1_600, outsidePlaybackEnhancerGain(ExteriorChannel.Channel1, 50))
    }

    @Test
    fun `gain clamps negative and oversized volume`() {
        assertEquals(0, outsidePlaybackEnhancerGain(ExteriorChannel.Channel0, -1))
        assertEquals(10_000, outsidePlaybackEnhancerGain(ExteriorChannel.Channel0, 500))
        assertEquals(6_400, outsidePlaybackEnhancerGain(ExteriorChannel.Channel1, 500))
    }
}
