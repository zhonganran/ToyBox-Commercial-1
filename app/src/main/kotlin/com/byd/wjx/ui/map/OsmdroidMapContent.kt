package com.byd.wjx.ui.map

import android.location.Location
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.byd.wjx.SuperToyBoxApp
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.ui.components.LocalConsumptionThresholds
import com.byd.wjx.ui.components.consumptionColor
import com.byd.wjx.ui.components.formatTime
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.util.Gcj02Converter
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private class MapRenderState {
    var centeredOnCurrent = false
    var lastFitRequest = 0
    var lastLocationRequest = 0
    var selectionInitialized = false
    var lastSelectedTripId: Long? = null
}

@Composable
internal fun OsmdroidMapView(
    state: MapUiState,
    currentLocation: Location?,
    fitRequest: Int,
    locationRequest: Int,
) {
    val context = LocalContext.current
    val thresholds = LocalConsumptionThresholds.current
    val displayedRoutes = remember(state.tripRoutes, state.selectedTripId) {
        state.selectedTripId?.let { selectedId ->
            state.tripRoutes.filter { it.trip.id == selectedId }
        } ?: state.tripRoutes
    }
    val currentPoint = remember(currentLocation?.latitude, currentLocation?.longitude) {
        currentLocation?.toMapGeoPoint()
    }
    val lastRoutePoint = remember(displayedRoutes) {
        displayedRoutes.firstOrNull()?.points?.lastOrNull()?.toMapGeoPoint()
    }
    val defaultCenter = remember(currentPoint, lastRoutePoint) {
        currentPoint ?: lastRoutePoint ?: defaultMapCenter()
    }
    val mapView = remember(context) {
        OsmdroidRuntime.ensureInitialized(context)
        MapView(context).apply {
            setTileSource(SuperToyBoxApp.AmapTileSource)
            setMultiTouchControls(true)
            controller.setZoom(14.5)
            controller.setCenter(defaultCenter)
        }
    }
    val renderState = remember(mapView) {
        MapRenderState().apply {
            lastFitRequest = fitRequest
            lastLocationRequest = locationRequest
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { map ->
            map.overlays.clear()
            displayedRoutes.forEach { route ->
                if (route.points.size < 2) return@forEach
                val routePoints = decimateTripPoints(route.points, MAX_ROUTE_POINTS)
                val routeColor = route.kwhPer100km?.let {
                    consumptionColor(it, thresholds.good, thresholds.bad)
                } ?: AccentGreen
                map.overlays.add(
                    Polyline().apply {
                        outlinePaint.color = routeColor.toArgb()
                        outlinePaint.strokeWidth = if (state.selectedTripId == route.trip.id) 9f else 6f
                        outlinePaint.isAntiAlias = true
                        infoWindow = null
                        setPoints(routePoints.map(TripPointEntity::toMapGeoPoint))
                    },
                )
            }
            displayedRoutes.forEach { route -> addRouteBoundaries(map, route) }
            currentPoint?.let { point ->
                map.overlays.add(
                    Marker(map).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "当前位置"
                    },
                )
            }
            val routePoints = displayedRoutes.flatMap { route ->
                decimateTripPoints(route.points, MAX_ROUTE_POINTS)
                    .map(TripPointEntity::toMapGeoPoint)
            }
            updateMapViewport(
                map = map,
                state = state,
                currentPoint = currentPoint,
                routePoints = routePoints,
                fitRequest = fitRequest,
                locationRequest = locationRequest,
                renderState = renderState,
            )
            map.invalidate()
        },
    )
}

private fun addRouteBoundaries(map: MapView, route: TripRoute) {
    val first = route.points.firstOrNull()
    val last = route.points.lastOrNull()
    first?.let { point ->
        map.overlays.add(
            routeBoundaryMarker(
                mapView = map,
                position = point.toMapGeoPoint(),
                title = "起点 ${formatTime(route.trip.startTs)}",
                kind = RouteBoundaryKind.Start,
            ),
        )
    }
    if (last != null && last != first) {
        map.overlays.add(
            routeBoundaryMarker(
                mapView = map,
                position = last.toMapGeoPoint(),
                title = route.trip.endTs?.let { "终点 ${formatTime(it)}" } ?: "终点",
                kind = RouteBoundaryKind.End,
            ),
        )
    }
}

private fun updateMapViewport(
    map: MapView,
    state: MapUiState,
    currentPoint: GeoPoint?,
    routePoints: List<GeoPoint>,
    fitRequest: Int,
    locationRequest: Int,
    renderState: MapRenderState,
) {
    val manualLocate = locationRequest != renderState.lastLocationRequest
    val manualFit = fitRequest != renderState.lastFitRequest
    val selectionChanged = renderState.selectionInitialized &&
        state.selectedTripId != renderState.lastSelectedTripId
    when {
        manualLocate && currentPoint != null -> {
            map.controller.setZoom(16.0)
            map.controller.animateTo(currentPoint)
            renderState.centeredOnCurrent = true
        }
        selectionChanged || manualFit -> fitMapToPoints(map, routePoints)
        !renderState.centeredOnCurrent && currentPoint != null && state.selectedTripId == null -> {
            map.controller.setZoom(16.0)
            map.controller.setCenter(currentPoint)
            renderState.centeredOnCurrent = true
        }
    }
    renderState.lastFitRequest = fitRequest
    renderState.lastLocationRequest = locationRequest
    renderState.lastSelectedTripId = state.selectedTripId
    renderState.selectionInitialized = true
}

private fun fitMapToPoints(map: MapView, points: List<GeoPoint>) {
    when {
        points.size >= 2 -> runCatching {
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 42)
        }
        points.size == 1 -> {
            map.controller.setZoom(16.0)
            map.controller.animateTo(points.first())
        }
    }
}

private fun TripPointEntity.toMapGeoPoint(): GeoPoint {
    val (mapLatitude, mapLongitude) = Gcj02Converter.wgs84ToGcj02(lat, lon)
    return GeoPoint(mapLatitude, mapLongitude)
}

private fun Location.toMapGeoPoint(): GeoPoint {
    val (mapLatitude, mapLongitude) = Gcj02Converter.wgs84ToGcj02(latitude, longitude)
    return GeoPoint(mapLatitude, mapLongitude)
}

private fun defaultMapCenter(): GeoPoint =
    Gcj02Converter.wgs84ToGcj02(22.5431, 114.0579)
        .let { (latitude, longitude) -> GeoPoint(latitude, longitude) }

private const val MAX_ROUTE_POINTS = 350
