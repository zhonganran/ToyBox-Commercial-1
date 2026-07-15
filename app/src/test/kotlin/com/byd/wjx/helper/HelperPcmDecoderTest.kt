package com.byd.wjx.helper

import android.media.AudioFormat
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperPcmDecoderTest {
    @Test fun `pcm8 is centered and expanded to signed pcm16`() {
        val output = ByteArrayOutputStream()

        val written = HelperPcmDecoder.writePcm16Chunk(
            out = output,
            chunk = byteArrayOf(0, 128.toByte(), 255.toByte()),
            encoding = AudioFormat.ENCODING_PCM_8BIT,
            sourceChannels = 1,
            outputChannels = 1,
        )

        assertEquals(6L, written)
        assertArrayEquals(
            byteArrayOf(0x00, 0x80.toByte(), 0x00, 0x00, 0x00, 0x7f),
            output.toByteArray(),
        )
    }

    @Test fun `pcm16 chunks are frame aligned`() {
        val output = ByteArrayOutputStream()

        val written = HelperPcmDecoder.writePcm16Chunk(
            out = output,
            chunk = byteArrayOf(1, 2, 3, 4, 5),
            encoding = AudioFormat.ENCODING_PCM_16BIT,
            sourceChannels = 2,
            outputChannels = 2,
        )

        assertEquals(4L, written)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), output.toByteArray())
    }

    @Test fun `pcm cache usability validates every playback field`() {
        assertTrue(PcmConfig("/tmp/a.pcm", 44_100, 2, 4).usable())
        assertFalse(PcmConfig("", 44_100, 2, 4).usable())
        assertFalse(PcmConfig("/tmp/a.pcm", 0, 2, 4).usable())
        assertFalse(PcmConfig("/tmp/a.pcm", 44_100, 3, 4).usable())
        assertFalse(PcmConfig("/tmp/a.pcm", 44_100, 2, 0).usable())
    }
}
