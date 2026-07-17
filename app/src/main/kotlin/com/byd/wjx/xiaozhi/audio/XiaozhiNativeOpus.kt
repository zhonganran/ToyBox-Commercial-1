package com.byd.wjx.xiaozhi.audio

internal object XiaozhiNativeOpus {
    init {
        System.loadLibrary("wjx_xiaozhi_opus")
    }

    external fun createEncoder(sampleRate: Int, channels: Int): Long
    external fun encode(handle: Long, pcm16LittleEndian: ByteArray, frameSamples: Int): ByteArray
    external fun destroyEncoder(handle: Long)

    external fun createDecoder(sampleRate: Int, channels: Int): Long
    external fun decode(handle: Long, packet: ByteArray, frameSamples: Int): ByteArray
    external fun destroyDecoder(handle: Long)
}
