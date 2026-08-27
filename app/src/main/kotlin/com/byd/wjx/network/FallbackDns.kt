package com.byd.wjx.network

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/** Resolves with the system DNS first and advances only when that resolver fails. */
internal class FallbackDns(
    private val resolvers: List<Dns>,
    private val onFallbackUsed: (resolverIndex: Int) -> Unit = {},
) : Dns {
    init {
        require(resolvers.isNotEmpty()) { "At least one DNS resolver is required" }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        resolvers.forEachIndexed { index, resolver ->
            val addresses = try {
                resolver.lookup(hostname)
            } catch (_: UnknownHostException) {
                emptyList()
            }
            if (addresses.isNotEmpty()) {
                if (index > 0) onFallbackUsed(index)
                return addresses.distinctBy(InetAddress::getHostAddress)
            }
        }
        // Do not expose service domains in user-visible or uploaded exception messages.
        throw UnknownHostException("DNS resolution failed")
    }
}
