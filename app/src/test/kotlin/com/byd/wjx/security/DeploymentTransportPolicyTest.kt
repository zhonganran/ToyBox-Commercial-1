package com.byd.wjx.security

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeploymentTransportPolicyTest {
    @Test
    fun `deployment endpoints require credential-free HTTPS`() {
        assertTrue(isSecureDeploymentEndpoint("https://api.example.test/v1"))
        assertFalse(isSecureDeploymentEndpoint("http://api.example.test/v1"))
        assertFalse(isSecureDeploymentEndpoint("https://user:pass@api.example.test/v1"))
        assertFalse(isSecureDeploymentEndpoint("not-a-url"))
    }

    @Test
    fun `redirects stay on the configured origin`() {
        val configured = "https://api.example.test:8443/v1/update"

        assertTrue(isSameDeploymentOrigin("https://api.example.test:8443/redirected", configured))
        assertFalse(isSameDeploymentOrigin("https://cdn.example.test:8443/redirected", configured))
        assertFalse(isSameDeploymentOrigin("https://api.example.test:9443/redirected", configured))
    }

    @Test
    fun `downloads stay on the single configured HTTPS host`() {
        val configured = "https://wjx.example.test/v1/update"

        assertTrue(isSameDeploymentOrigin("https://wjx.example.test/app.apk", configured))
        assertFalse(isSameDeploymentOrigin("https://cdn.example.test/app.apk", configured))
        assertFalse(isSameDeploymentOrigin("http://wjx.example.test/app.apk", configured))
        assertFalse(isSameDeploymentOrigin("https://evil.test/app.apk", configured))
        assertFalse(isSameDeploymentOrigin("https://wjx.example.test:8443/app.apk", configured))
        assertFalse(isSameDeploymentOrigin("https://user@wjx.example.test/app.apk", configured))
    }

    @Test
    fun `bounded response reader rejects declared and streamed overflow`() {
        assertEquals("abc", readBoundedUtf8Body("abc".toResponseBody(JSON), 3))
        assertThrows(IllegalStateException::class.java) {
            readBoundedUtf8Body("abcd".toResponseBody(JSON), 3)
        }
        val streamed = object : ResponseBody() {
            override fun contentType() = JSON
            override fun contentLength() = -1L
            override fun source(): BufferedSource = Buffer().writeUtf8("abcd")
        }
        assertThrows(IllegalStateException::class.java) {
            readBoundedUtf8Body(streamed, 3)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
