package com.byd.wjx.network

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackDnsTest {
    @Test
    fun `primary result is used without calling fallback`() {
        val primaryAddress = address(10, 0, 0, 1)
        var fallbackCalls = 0
        val dns = FallbackDns(
            resolvers = listOf(
                FakeDns { listOf(primaryAddress) },
                FakeDns {
                    fallbackCalls += 1
                    listOf(address(10, 0, 0, 2))
                },
            ),
        )

        assertEquals(listOf(primaryAddress), dns.lookup("example.test"))
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `fallback result is used after primary lookup failure`() {
        val fallbackAddress = address(223, 5, 5, 5)
        var fallbackIndex = -1
        val dns = FallbackDns(
            resolvers = listOf(
                FakeDns { throw UnknownHostException("system failure") },
                FakeDns { listOf(fallbackAddress, fallbackAddress) },
            ),
            onFallbackUsed = { fallbackIndex = it },
        )

        assertEquals(listOf(fallbackAddress), dns.lookup("example.test"))
        assertEquals(1, fallbackIndex)
    }

    @Test
    fun `all resolver failures return sanitized error`() {
        val dns = FallbackDns(
            resolvers = listOf(
                FakeDns { throw UnknownHostException("private.example.test") },
                FakeDns { emptyList() },
            ),
        )

        val failure = assertThrows(UnknownHostException::class.java) {
            dns.lookup("private.example.test")
        }

        assertEquals("DNS resolution failed", failure.message)
        assertFalse(failure.message.orEmpty().contains("private.example.test"))
    }

    @Test
    fun `recent successful address is reused during a transient resolver outage`() {
        val cachedAddress = address(10, 0, 0, 3)
        var now = 1_000L
        var cacheUsed = false
        var firstLookup = true
        var fallbackLookupCount = 0
        val dns = FallbackDns(
            resolvers = listOf(
                FakeDns {
                    if (firstLookup) {
                        firstLookup = false
                        listOf(cachedAddress)
                    } else {
                        throw UnknownHostException("temporary outage")
                    }
                },
                FakeDns {
                    fallbackLookupCount += 1
                    throw UnknownHostException("fallback unavailable")
                },
            ),
            onCacheUsed = { cacheUsed = true },
            cacheTtlMs = 60_000L,
            clockMs = { now },
        )

        assertEquals(listOf(cachedAddress), dns.lookup("example.test"))
        now += 1_000L
        assertEquals(listOf(cachedAddress), dns.lookup("example.test"))
        assertTrue(cacheUsed)
        assertEquals(0, fallbackLookupCount)
    }

    private class FakeDns(private val lookupResult: () -> List<InetAddress>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> = lookupResult()
    }

    private fun address(a: Int, b: Int, c: Int, d: Int): InetAddress = InetAddress.getByAddress(
        byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()),
    )
}
