package com.byd.wjx.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HailingAudioProcessorTest {
    @Test
    fun `zero microphone gain produces silence`() {
        val processor = HailingAudioProcessor(
            RealtimeHailingTuning(micGainPercent = 0, noiseSuppressionPercent = 0),
        )
        val output = ShortArray(4) { Short.MAX_VALUE }

        val count = processor.process(shortArrayOf(100, -200, 300, -400), output)

        assertEquals(4, count)
        assertTrue(output.all { it == 0.toShort() })
    }

    @Test
    fun `noise gate suppresses low level microphone input`() {
        val processor = HailingAudioProcessor(
            RealtimeHailingTuning(micGainPercent = 100, noiseSuppressionPercent = 100),
        )
        val output = ShortArray(4)

        processor.process(shortArrayOf(40, -80, 120, -160), output)

        assertTrue(output.all { it == 0.toShort() })
    }

    @Test
    fun `loud input is compressed without short overflow`() {
        val processor = HailingAudioProcessor(
            RealtimeHailingTuning(micGainPercent = 500, noiseSuppressionPercent = 0),
        )
        val output = ShortArray(4)

        processor.process(
            shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, 20_000, -20_000),
            output,
        )

        assertTrue(output.all { abs(it.toInt()) <= Short.MAX_VALUE.toInt() })
        assertTrue(output.any { it > 0 })
        assertTrue(output.any { it < 0 })
    }

    @Test
    fun `processor tuning update applies without reconstruction`() {
        val processor = HailingAudioProcessor(
            RealtimeHailingTuning(micGainPercent = 0, noiseSuppressionPercent = 0),
        )
        val output = ShortArray(2)
        processor.process(shortArrayOf(1_000, -1_000), output)
        assertTrue(output.all { it == 0.toShort() })

        processor.update(RealtimeHailingTuning(micGainPercent = 100, noiseSuppressionPercent = 0))
        processor.process(shortArrayOf(1_000, -1_000), output)

        assertTrue(output[0] > 0)
        assertTrue(output[1] < 0)
    }

    @Test
    fun `enhancer gain follows clamped realtime volume`() {
        assertEquals(0, realtimeHailingEnhancerGain(RealtimeHailingTuning(volumePercent = 0)))
        assertEquals(3_200, realtimeHailingEnhancerGain(RealtimeHailingTuning(volumePercent = 100)))
        assertEquals(6_400, realtimeHailingEnhancerGain(RealtimeHailingTuning(volumePercent = 500)))
    }

    @Test
    fun `shared audio spec keeps engine and device buffers on the same frame size`() {
        assertEquals(320, RealtimeHailingAudioSpec.frameSamples(16_000))
        assertEquals(960, RealtimeHailingAudioSpec.frameSamples(48_000))
        assertEquals(1, RealtimeHailingAudioSpec.OUTPUT_CHANNELS)
        assertEquals(2, RealtimeHailingAudioSpec.BYTES_PER_SAMPLE)
    }
}
