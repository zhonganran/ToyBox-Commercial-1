package com.byd.wjx.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundLibraryImportLimitTest {
    @Test
    fun `bounded copy preserves content at the limit`() {
        val content = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()

        val copied = copySoundBytes(ByteArrayInputStream(content), output, maxBytes = 4)

        assertEquals(4L, copied)
        assertArrayEquals(content, output.toByteArray())
    }

    @Test(expected = IllegalStateException::class)
    fun `bounded copy rejects content over the limit`() {
        copySoundBytes(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
            output = ByteArrayOutputStream(),
            maxBytes = 4,
        )
    }
}
