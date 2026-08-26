package com.byd.wjx.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

@Composable
internal fun rememberCurrentLocation(): Location? {
    val context = LocalContext.current
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(hasPermission) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var currentLocation by remember(context, hasPermission) { mutableStateOf<Location?>(null) }
    DisposableEffect(context, hasPermission) {
        currentLocation = null
        if (!hasPermission) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@DisposableEffect onDispose {}
        currentLocation = bestLastKnownLocation(manager)
        val provider = when {
            manager.providerEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.providerEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) return@DisposableEffect onDispose {}
        val cancellationSignal = CancellationSignal()
        runCatching {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (location != null) currentLocation = location
            }
        }
        onDispose { cancellationSignal.cancel() }
    }
    return currentLocation
}

private fun LocationManager.providerEnabled(provider: String): Boolean =
    runCatching { isProviderEnabled(provider) }.getOrDefault(false)

@Suppress("MissingPermission")
private fun bestLastKnownLocation(manager: LocationManager): Location? =
    listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull(Location::getTime)
