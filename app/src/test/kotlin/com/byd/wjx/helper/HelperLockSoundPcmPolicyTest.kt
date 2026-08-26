package com.byd.wjx.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HelperLockSoundPcmPolicyTest {

    @Test
    fun `frame bytes clamp channel count to mono or stereo pcm16`() {
        assertEquals(2, lockSoundPcmFrameBytes(0))
        assertEquals(2, lockSoundPcmFrameBytes(1))
        assertEquals(4, lockSoundPcmFrameBytes(2))
        assertEquals(4, lockSoundPcmFrameBytes(6))
    }

    @Test
    fun `pcm byte alignment never returns a partial frame`() {
        assertEquals(0, alignLockSoundPcmBytes(0, 4))
        assertEquals(4, alignLockSoundPcmBytes(7, 4))
        assertEquals(8, alignLockSoundPcmBytes(8, 4))
        assertEquals(5, alignLockSoundPcmBytes(5, 0))
    }

    @Test
    fun `lead silence keeps the existing four hundred twenty milliseconds`() {
        assertEquals(
            74_088,
            lockSoundPcmLeadBytes(PcmConfig(sampleRate = 44_100, channelCount = 2)),
        )
        assertEquals(
            6_720,
            lockSoundPcmLeadBytes(PcmConfig(sampleRate = 8_000, channelCount = 1)),
        )
    }

    @Test
    fun `static pcm payload accepts exactly eight megabytes and rejects larger data`() {
        val pcm = PcmConfig(sampleRate = 44_100, channelCount = 2)
        val leadBytes = lockSoundPcmLeadBytes(pcm)
        val maximumPcmBytes = LOCK_SOUND_PCM_STATIC_MAX_BYTES - leadBytes

        assertEquals(
            LOCK_SOUND_PCM_STATIC_MAX_BYTES.toInt(),
            lockSoundStaticPayloadBytes(pcm, maximumPcmBytes),
        )
        assertNull(lockSoundStaticPayloadBytes(pcm, maximumPcmBytes + 1L))
    }
}
