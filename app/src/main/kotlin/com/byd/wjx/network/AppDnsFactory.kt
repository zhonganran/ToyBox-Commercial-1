package com.byd.wjx.network

import android.content.Context
import com.byd.wjx.log.WjxFileLogger
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl

object AppDnsFactory {
    fun create(context: Context): Dns {
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, DNS_CACHE_DIRECTORY), DNS_CACHE_BYTES))
            .build()

        val aliDns = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(ALI_DOH_URL.toHttpUrl())
            .bootstrapDnsHosts(*literalAddresses(ALI_BOOTSTRAP_IPS))
            .includeIPv6(false)
            .build()
        val dnsPod = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(DNSPOD_DOH_URL.toHttpUrl())
            .bootstrapDnsHosts(*literalAddresses(DNSPOD_BOOTSTRAP_IPS))
            .includeIPv6(false)
            .build()

        return FallbackDns(
            resolvers = listOf(Dns.SYSTEM, aliDns, dnsPod),
            onFallbackUsed = ::logFallbackUsed,
        )
    }

    private fun literalAddresses(values: List<String>): Array<InetAddress> =
        values.map(InetAddress::getByName).toTypedArray()

    private fun logFallbackUsed(resolverIndex: Int) {
        val now = System.currentTimeMillis()
        val previous = lastFallbackLogAt.get()
        if (now - previous < FALLBACK_LOG_INTERVAL_MS || !lastFallbackLogAt.compareAndSet(previous, now)) return
        WjxFileLogger.i(TAG, "system DNS unavailable; encrypted fallback active resolver=$resolverIndex")
    }

    private const val TAG = "AppDns"
    private const val DOH_TIMEOUT_SECONDS = 4L
    private const val DNS_CACHE_BYTES = 2L * 1024L * 1024L
    private const val DNS_CACHE_DIRECTORY = "dns_http_cache"
    private const val FALLBACK_LOG_INTERVAL_MS = 60_000L
    private const val ALI_DOH_URL = "https://dns.alidns.com/dns-query"
    private const val DNSPOD_DOH_URL = "https://doh.pub/dns-query"
    private val ALI_BOOTSTRAP_IPS = listOf("223.5.5.5", "223.6.6.6")
    private val DNSPOD_BOOTSTRAP_IPS = listOf("1.12.12.12", "120.53.53.53")
    private val lastFallbackLogAt = AtomicLong(0L)
}
