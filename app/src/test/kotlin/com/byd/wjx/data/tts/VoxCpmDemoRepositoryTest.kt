package com.byd.wjx.data.tts

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class VoxCpmDemoRepositoryTest {
    @Test
    fun `parses valid event id`() {
        assertEquals("abc12345", parseVoxCpmEventId("{\"event_id\":\"abc12345\"}"))
    }

    @Test(expected = IOException::class)
    fun `rejects missing event id`() {
        parseVoxCpmEventId("{}")
    }

    @Test
    fun `parses audio url from complete server event`() {
        val body = """
            event: complete
            data: [{"url":"https://openbmb-voxcpm-demo.hf.space/gradio_api/file=/tmp/result.mp3"}]
        """.trimIndent()

        assertEquals(
            "https://openbmb-voxcpm-demo.hf.space/gradio_api/file=/tmp/result.mp3",
            parseVoxCpmAudioUrl(body),
        )
    }

    @Test(expected = IOException::class)
    fun `rejects server event without audio`() {
        parseVoxCpmAudioUrl("event: complete\ndata: [null]")
    }
}
