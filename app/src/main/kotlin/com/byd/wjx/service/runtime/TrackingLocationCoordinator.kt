package com.byd.wjx.service.runtime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.byd.wjx.data.repository.TrackingStateStore
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Process-level owner for platform location registration used by trip recording. */
@Singleton
class TrackingLocationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackingStateStore: TrackingStateStore,
) : LocationListener {
    private var locationManager: LocationManager? = null
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted, skipping location updates")
            WjxFileLogger.w(TAG, "ACCESS_FINE_LOCATION not granted, skipping location updates")
            return
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = manager
        val gpsEnabled = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val networkEnabled = runCatching {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)

        Log.i(TAG, "Location providers: gps=$gpsEnabled network=$networkEnabled")
        WjxFileLogger.i(TAG, "Location providers gps=$gpsEnabled network=$networkEnabled")

        if (gpsEnabled) {
            registerProvider(manager, LocationManager.GPS_PROVIDER, "GPS")
        }
        if (networkEnabled) {
            registerProvider(manager, LocationManager.NETWORK_PROVIDER, "Network")
        }

        publishLastKnown(manager, gpsEnabled, networkEnabled)
        if (!gpsEnabled && !networkEnabled) {
            Log.e(TAG, "No location provider enabled")
            WjxFileLogger.e(TAG, "No location provider enabled; GPS will not work")
        }
        started = true
    }

    @Synchronized
    fun stop() {
        if (!started && locationManager == null) return
        try {
            locationManager?.removeUpdates(this)
            Log.d(TAG, "Location updates removed")
        } catch (e: Exception) {
            WjxFileLogger.w(TAG, "Failed to remove location updates: ${e.message}", e)
        } finally {
            locationManager = null
            started = false
        }
    }

    override fun onLocationChanged(location: Location) {
        trackingStateStore.setLastLocation(location)
        Log.d(
            TAG,
            "GPS fix: lat=${location.latitude} lon=${location.longitude} " +
                "acc=${"%.1f".format(location.accuracy)}m " +
                "speed=${"%.1f".format(location.speed * 3.6f)}km/h provider=${location.provider}",
        )
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun registerProvider(
        manager: LocationManager,
        provider: String,
        label: String,
    ) {
        try {
            manager.requestLocationUpdates(
                provider,
                LOCATION_INTERVAL_MS,
                LOCATION_MIN_DISTANCE_M,
                this,
                Looper.getMainLooper(),
            )
            Log.i(TAG, "requestLocationUpdates($provider) registered")
            WjxFileLogger.i(TAG, "$label provider registered")
        } catch (e: Exception) {
            Log.w(TAG, "$label provider registration failed: ${e.message}", e)
            WjxFileLogger.w(TAG, "$label provider registration failed: ${e.message}", e)
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun publishLastKnown(
        manager: LocationManager,
        gpsEnabled: Boolean,
        networkEnabled: Boolean,
    ) {
        try {
            val lastKnown = when {
                gpsEnabled -> manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                networkEnabled -> manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                else -> null
            }
            if (lastKnown == null) {
                Log.w(TAG, "lastKnownLocation is null")
                WjxFileLogger.w(TAG, "lastKnownLocation is null")
                return
            }
            trackingStateStore.setLastLocation(lastKnown)
            val ageSeconds = (System.currentTimeMillis() - lastKnown.time) / 1_000L
            Log.i(
                TAG,
                "lastKnownLocation: lat=${lastKnown.latitude} lon=${lastKnown.longitude} " +
                    "provider=${lastKnown.provider} age=${ageSeconds}s",
            )
            WjxFileLogger.i(TAG, "lastKnownLocation provider=${lastKnown.provider} age=${ageSeconds}s")
        } catch (e: Exception) {
            Log.w(TAG, "getLastKnownLocation failed: ${e.message}")
            WjxFileLogger.w(TAG, "getLastKnownLocation failed: ${e.message}", e)
        }
    }

    private companion object {
        const val TAG = "TrackingLocation"
        const val LOCATION_INTERVAL_MS = 2_000L
        const val LOCATION_MIN_DISTANCE_M = 8.0f
    }
}
