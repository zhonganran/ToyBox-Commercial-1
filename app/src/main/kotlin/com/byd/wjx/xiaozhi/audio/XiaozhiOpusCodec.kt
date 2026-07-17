package com.byd.wjx.xiaozhi.audio

internal class XiaozhiOpusCodec {
    private var encoderHandle: Long = 0L
    private var decoderHandle: Long = 0L
    private var decoderRate: Int = 0

    @Synchronized
    fun openEncoder() {
        if (encoderHandle != 0L) return
        encoderHandle = XiaozhiNativeOpus.createEncoder(INPUT_SAMPLE_RATE, 1)
        check(encoderHandle != 0L) { "Opus编码器初始化失败" }
    }

    @Synchronized
    fun encode(pcm16LittleEndian: ByteArray): ByteArray {
        check(encoderHandle != 0L) { "Opus编码器尚未初始化" }
        return XiaozhiNativeOpus.encode(
            encoderHandle,
            pcm16LittleEndian,
            INPUT_FRAME_SAMPLES,
        )
    }

    @Synchronized
    fun closeEncoder() {
        if (encoderHandle != 0L) XiaozhiNativeOpus.destroyEncoder(encoderHandle)
        encoderHandle = 0L
    }

    @Synchronized
    fun openDecoder(sampleRate: Int) {
        val normalized = normalizeSampleRate(sampleRate)
        if (decoderHandle != 0L && decoderRate == normalized) return
        closeDecoder()
        decoderHandle = XiaozhiNativeOpus.createDecoder(normalized, 1)
        check(decoderHandle != 0L) { "Opus解码器初始化失败" }
        decoderRate = normalized
    }

    @Synchronized
    fun decode(packet: ByteArray, frameDurationMs: Int): ByteArray {
        check(decoderHandle != 0L) { "Opus解码器尚未初始化" }
        val frameSamples = (decoderRate * frameDurationMs / 1_000).coerceAtLeast(1)
        return XiaozhiNativeOpus.decode(decoderHandle, packet, frameSamples)
    }

    @Synchronized
    fun closeDecoder() {
        if (decoderHandle != 0L) XiaozhiNativeOpus.destroyDecoder(decoderHandle)
        decoderHandle = 0L
        decoderRate = 0
    }

    @Synchronized
    fun close() {
        closeEncoder()
        closeDecoder()
    }

    private fun normalizeSampleRate(sampleRate: Int): Int = when {
        sampleRate <= 8_000 -> 8_000
        sampleRate <= 12_000 -> 12_000
        sampleRate <= 16_000 -> 16_000
        sampleRate <= 24_000 -> 24_000
        else -> 48_000
    }

    companion object {
        const val INPUT_SAMPLE_RATE = 16_000
        const val INPUT_FRAME_DURATION_MS = 60
        const val INPUT_FRAME_SAMPLES = INPUT_SAMPLE_RATE * INPUT_FRAME_DURATION_MS / 1_000
    }
}
