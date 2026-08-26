package com.byd.wjx.audio

internal object RealtimeHailingAudioSpec {
    const val DEFAULT_SAMPLE_RATE = 16_000
    const val FRAMES_PER_SECOND = 50
    const val BYTES_PER_SAMPLE = 2
    const val OUTPUT_CHANNELS = 1

    fun frameSamples(sampleRate: Int): Int = sampleRate / FRAMES_PER_SECOND
}
