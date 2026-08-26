package com.byd.wjx.ui.settings

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ServiceQrRepositoryTest {

    @Test
    fun `parser keeps enabled entries and applies title fallback`() {
        val parsed = parseServiceQrCodes(
            """
            {
              "items": [
                {"key":"support", "enabled":true, "imageUrl":"https://example.test/qr"},
                {"key":"sales", "enabled":false},
                {"key":"", "enabled":true}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(setOf("support"), parsed.keys)
        assertEquals("support", parsed.getValue("support").title)
        assertFalse("sales" in parsed)
    }

    @Test
    fun `parser treats invalid responses as empty`() {
        assertTrue(parseServiceQrCodes("").isEmpty())
        assertTrue(parseServiceQrCodes("not-json").isEmpty())
        assertTrue(parseServiceQrCodes("{}").isEmpty())
    }

    @Test
    fun `remote payload and images stay on the single service origin`() {
        val metadata = "https://wjx.example.test/api/v1/service/qrcodes"

        assertTrue(isValidServiceQrPayload("{\"items\":[]}"))
        assertFalse(isValidServiceQrPayload("{}"))
        assertTrue(isAllowedServiceQrMetadataUrl(metadata))
        assertFalse(isAllowedServiceQrMetadataUrl("http://wjx.example.test/qrcodes"))
        assertTrue(isAllowedServiceQrMetadataUrl("https://wjx.example.test/redirected", metadata))
        assertFalse(isAllowedServiceQrMetadataUrl("https://other.example.test/redirected", metadata))
        assertTrue(isAllowedServiceQrImageUrl("https://wjx.example.test/cdn/qr.png", metadata))
        assertFalse(isAllowedServiceQrImageUrl("https://cdn.example.test/qr.png", metadata))
        assertFalse(isAllowedServiceQrImageUrl("http://wjx.example.test/cdn/qr.png", metadata))
        assertFalse(isAllowedServiceQrImageUrl("https://evil.test/qr.png", metadata))
        assertFalse(isAllowedServiceQrImageUrl("https://wjx.example.test:8443/qr.png", metadata))
        assertFalse(isAllowedServiceQrImageUrl("https://user:pass@wjx.example.test/qr.png", metadata))
    }

    @Test
    fun `image cache names are fixed hashes and response reads enforce size limit`() {
        val first = serviceQrImageCacheName("../wechat", "https://api.example.test/one")
        val second = serviceQrImageCacheName("../wechat", "https://api.example.test/two")

        assertTrue(first.matches(Regex("[0-9a-f]{64}\\.img")))
        assertFalse(first.contains(".."))
        assertFalse(first == second)
        assertEquals(byteArrayOf(1, 2, 3).toList(), readLimitedBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3).toList())
        assertThrows(IllegalArgumentException::class.java) {
            readLimitedBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3)
        }
    }
}
