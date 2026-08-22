package com.byd.wjx.phonecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneCastConfigTest {
    @Test
    fun `normalizes host-only address`() {
        assertEquals(
            "rtsp://192.168.43.1:8086/live",
            PhoneCastUrl.normalize("192.168.43.1:8086/live"),
        )
    }

    @Test
    fun `keeps credentials and query without exposing them in endpoint label`() {
        val input = "rtsp://user:pass@10.0.0.8:8554/stream?profile=main"
        assertEquals(input, PhoneCastUrl.normalize(input))
        assertEquals("10.0.0.8:8554", PhoneCastUrl.endpointLabel(input))
    }

    @Test
    fun `adds root path when omitted`() {
        assertEquals("rtsp://10.0.0.2/", PhoneCastUrl.normalize("rtsp://10.0.0.2"))
    }

    @Test
    fun `preserves encoded path and query`() {
        val input = "rtsp://10.0.0.2/live%20view?name=a%2Fb"
        assertEquals(input, PhoneCastUrl.normalize(input))
    }

    @Test
    fun `rejects unsupported or malformed addresses`() {
        assertNull(PhoneCastUrl.normalize(""))
        assertNull(PhoneCastUrl.normalize("https://10.0.0.2/live"))
        assertNull(PhoneCastUrl.normalize("rtsp://:8554/live"))
        assertNull(PhoneCastUrl.normalize("rtsp://10.0.0.2:99999/live"))
    }

    @Test
    fun `unknown scale falls back to crop`() {
        assertEquals(PhoneCastScaleMode.CROP, PhoneCastScaleMode.fromStored("unknown"))
        assertEquals(PhoneCastScaleMode.FIT, PhoneCastScaleMode.fromStored("fit"))
    }
}
