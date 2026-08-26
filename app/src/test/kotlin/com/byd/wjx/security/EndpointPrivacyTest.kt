package com.byd.wjx.security

import com.byd.wjx.BuildConfig
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPrivacyTest {
    @Test
    fun `configured server domain family is hidden without exposing sibling hosts`() {
        val host = URI(BuildConfig.UPDATE_API_URL).host
        val labels = host.split('.')
        val sibling = "cdn.${labels.takeLast(2).joinToString(".")}"

        val redacted = EndpointPrivacy.redact("连接 $host 失败，下载节点 $sibling 不可用")

        assertFalse(redacted.contains(host, ignoreCase = true))
        assertFalse(redacted.contains(sibling, ignoreCase = true))
        assertTrue(redacted.contains(EndpointPrivacy.HIDDEN_ENDPOINT))
    }

    @Test
    fun `urls and server addresses are hidden while version numbers remain readable`() {
        val redacted = EndpointPrivacy.redact(
            "GET https://server.example/path failed at 203.0.113.8:443 and 2001:db8::8; " +
                "controller=57.1.3.2501010.1 time=12:00:00.000",
        )

        assertFalse(redacted.contains("server.example"))
        assertFalse(redacted.contains("203.0.113.8"))
        assertFalse(redacted.contains("2001:db8::8"))
        assertTrue(redacted.contains("57.1.3.2501010.1"))
        assertTrue(redacted.contains("12:00:00.000"))
    }

    @Test
    fun `an endpoint-only exception falls back to a useful generic message`() {
        val error = IllegalStateException("https://server.example/private")

        assertEquals("服务器连接失败", EndpointPrivacy.userMessage(error, "服务器连接失败"))
    }
}
