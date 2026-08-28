package com.byd.wjx.network

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Dns

/** Resolves with the system DNS first and advances only when that resolver fails. */
internal class FallbackDns(
    private val resolvers: List<Dns>,
    private val onFallbackUsed: (resolverIndex: Int) -> Unit = {},
    private val onCacheUsed: () -> Unit = {},
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val clockMs: () -> Long = System::currentTimeMillis,
) : Dns {
    init {
        require(resolvers.isNotEmpty()) { "At least one DNS resolver is required" }
        require(cacheTtlMs > 0L) { "DNS cache TTL must be positive" }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val cacheKey = hostname.trim().lowercase(Locale.US)
        val now = clockMs()
        lookup(resolvers.first(), hostname)?.takeIf(List<InetAddress>::isNotEmpty)?.let { addresses ->
            return cache(cacheKey, addresses, now)
        }
        cache[cacheKey]?.takeIf { now <= it.expiresAtMs }?.let {
            onCacheUsed()
            return it.addresses
        }
        resolvers.drop(1).forEachIndexed { fallbackIndex, resolver ->
            val index = fallbackIndex + 1
            val addresses = lookup(resolver, hostname)
            if (!addresses.isNullOrEmpty()) {
                onFallbackUsed(index)
                return cache(cacheKey, addresses, now)
            }
        }
        throw UnknownHostException("DNS resolution failed")
    }

    private fun lookup(resolver: Dns, hostname: String): List<InetAddress>? =
        try {
            resolver.lookup(hostname)
        } catch (_: Exception) {
            null
        }

    private fun cache(key: String, addresses: List<InetAddress>, now: Long): List<InetAddress> {
        val distinct = addresses.distinctBy(InetAddress::getHostAddress)
        if (distinct.isNotEmpty()) {
            cache[key] = CachedAddresses(distinct, now + cacheTtlMs)
        }
        return distinct
    }

    private data class CachedAddresses(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedAddresses>()

    private companion object {
        const val DEFAULT_CACHE_TTL_MS = 10 * 60 * 1_000L
    }
}
