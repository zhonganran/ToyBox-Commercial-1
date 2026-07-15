package com.byd.wjx.data.automation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the most recent moment a default network can REALLY reach the public
 * internet. Android's `NET_CAPABILITY_VALIDATED` is a necessary but not
 * sufficient signal on DiLink: BYD ships several APNs (e.g. ims/system APN1)
 * that are flagged INTERNET+VALIDATED so the system can keep VoLTE / map
 * updates / telematics alive even when the user has toggled "Cellular Data"
 * off — but user-space apps can't actually reach the wider internet through
 * those APNs. Listening to VALIDATED alone fired the trigger in those cases.
 *
 * To distinguish a real-world reachable network we run a lightweight TCP
 * probe to a list of well-known public DNS servers in mainland China
 * (AliDNS / DNSPod / 114DNS, port 53)
 * each time Android signals a fresh VALIDATED edge. The probe socket is
 * bound to the candidate network so traffic cannot silently fall back to a
 * different default. Only after the probe succeeds do we publish
 * [_lastAvailableAt] for the AutomationEngine.
 */
@Singleton
class NetworkAvailableMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var _lastAvailableAt: Long = 0L
    // Tracks which network is currently validated. Prevents repeated capabilities
    // callbacks for the SAME network from re-running the probe — without this,
    // every signal-strength tick on a stable WiFi would re-arm the trigger.
    @Volatile private var validatedNetworkId: Long = -1L
    // True while an async TCP probe is in flight. AutomationEngine consults this
    // before seeding the per-rule watermark on first observation: seeding 0
    // mid-probe and then handing the about-to-publish edge as "new" would
    // re-introduce the false-fire it tries to prevent. One extra poll tick of
    // latency is a fair price.
    val lastAvailableAt: Long get() = _lastAvailableAt
    val probePending: Boolean get() = probeGate.pending

    private val cm: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probeGate = LatestOperationGate()
    // Single in-flight probe — newer VALIDATED edges cancel any earlier
    // probe still running, avoiding stacked socket attempts when the
    // system spams capability changes during a network transition.
    @Volatile private var probeJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val nid = network.networkHandle
            if (hasInternet && validated) {
                if (validatedNetworkId != nid) {
                    validatedNetworkId = nid
                    Log.i(TAG, "candidate validated network (nid=$nid), probing reachability")
                    WjxFileLogger.i(TAG, "candidate validated network nid=$nid")
                    probeReachability(network, nid)
                }
            } else if (validatedNetworkId == nid) {
                validatedNetworkId = -1L
                Log.i(TAG, "validation lost on tracked network (nid=$nid)")
                WjxFileLogger.i(TAG, "validation lost nid=$nid")
            }
        }

        override fun onLost(network: Network) {
            if (validatedNetworkId == network.networkHandle) {
                validatedNetworkId = -1L
                Log.i(TAG, "tracked network lost (nid=${network.networkHandle})")
                WjxFileLogger.i(TAG, "tracked network lost nid=${network.networkHandle}")
            }
        }
    }

    @Synchronized
    private fun probeReachability(network: Network, nid: Long) {
        if (!registered) return
        probeJob?.cancel()
        val token = probeGate.begin()
        val job = scope.launch {
            try {
                val reached = withContext(Dispatchers.IO) { tryProbe(network) }
                // Race guard: by the time the probe returns, the user may have
                // already switched to yet another network. Only publish the edge
                // if the network we just confirmed is still the tracked one.
                if (!reached) {
                    Log.i(TAG, "probe failed for nid=$nid — not publishing edge")
                    WjxFileLogger.i(TAG, "probe failed nid=$nid")
                    return@launch
                }
                if (!registered || !probeGate.isLatest(token) || validatedNetworkId != nid) {
                    Log.i(TAG, "probe ok for nid=$nid but tracked network changed — dropped")
                    WjxFileLogger.i(TAG, "probe ok but tracked network changed nid=$nid current=$validatedNetworkId")
                    return@launch
                }
                _lastAvailableAt = System.currentTimeMillis()
                Log.i(TAG, "validated+reachable network edge at $_lastAvailableAt (nid=$nid)")
                WjxFileLogger.i(TAG, "validated reachable network edge at=$_lastAvailableAt nid=$nid")
            } finally {
                probeGate.finish(token)
            }
        }
        probeJob = job
        job.invokeOnCompletion {
            synchronized(this@NetworkAvailableMonitor) {
                if (probeJob === job) probeJob = null
            }
        }
    }

    private fun tryProbe(network: Network): Boolean {
        for ((host, port) in PROBE_TARGETS) {
            val socket = Socket()
            try {
                network.bindSocket(socket)
                socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
                Log.i(TAG, "probe ok via $host:$port")
                WjxFileLogger.i(TAG, "probe ok via $host:$port")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "probe to $host:$port failed: ${e.message}")
            } finally {
                runCatching { socket.close() }
            }
        }
        return false
    }

    @Synchronized
    fun start() {
        if (registered) return
        // Mark first so an immediate framework callback is accepted. Roll back if registration
        // itself fails.
        registered = true
        runCatching {
            // No initial-state guard. ConnectivityManager replays the current
            // VALIDATED network state to a freshly-registered callback — that
            // replay IS the "internet is available now" edge we want users to
            // be able to react to on app launch. Repeat-fires from service
            // restarts are best handled by the rule's cooldown setting.
            cm.registerDefaultNetworkCallback(callback)
            Log.i(TAG, "started")
            WjxFileLogger.i(TAG, "started")
        }.onFailure {
            registered = false
            Log.w(TAG, "start failed: ${it.message}")
            WjxFileLogger.w(TAG, "start failed: ${it.message}")
        }
    }

    @Synchronized
    fun stop() {
        val wasRegistered = registered
        registered = false
        validatedNetworkId = -1L
        probeGate.cancelAll()
        probeJob?.cancel()
        probeJob = null
        if (!wasRegistered) return
        runCatching { cm.unregisterNetworkCallback(callback) }
        Log.i(TAG, "stopped")
        WjxFileLogger.i(TAG, "stopped")
    }

    private companion object {
        const val TAG = "NetworkAvailMon"
        const val PROBE_TIMEOUT_MS = 2000
        // Mainland China public DNS targets. Avoid 1.1.1.1 / 8.8.8.8 because
        // some DiLink builds route special system APNs differently and can
        // produce false-positive internet edges for user-space apps.
        val PROBE_TARGETS: List<Pair<String, Int>> = listOf(
            "223.5.5.5" to 53,
            "119.29.29.29" to 53,
            "114.114.114.114" to 53,
        )
    }
}
