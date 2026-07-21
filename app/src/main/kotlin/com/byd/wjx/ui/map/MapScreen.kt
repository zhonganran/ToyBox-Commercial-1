package com.byd.wjx.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.SuperToyBoxApp
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.ui.components.LocalConsumptionThresholds
import com.byd.wjx.ui.components.consumptionColor
import com.byd.wjx.ui.components.formatTime
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import com.byd.wjx.util.Gcj02Converter
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLocation = rememberCurrentLocation()
    var fitRequest by remember { mutableIntStateOf(0) }
    var locationRequest by remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 880.dp
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (wide) 20.dp else 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                ProPageHeader(
                    eyebrow = "TRIPS & ROUTES",
                    title = "行程与轨迹",
                    subtitle = "行程记录、行驶时长、里程、能耗与 GPS 轨迹",
                    modifier = Modifier.weight(1f),
                    trailing = {
                        PeriodSelector(period = state.period, onPeriodSelected = viewModel::setPeriod)
                    },
                )
            }

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MapPane(
                        state = state,
                        currentLocation = currentLocation,
                        fitRequest = fitRequest,
                        locationRequest = locationRequest,
                        onFitRoutes = { fitRequest++ },
                        onLocate = { locationRequest++ },
                        modifier = Modifier.weight(0.82f).fillMaxHeight(),
                    )
                    TripRecordsPanel(
                        state = state,
                        onSelectTrip = viewModel::selectTrip,
                        modifier = Modifier.weight(1.18f).fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MapPane(
                        state = state,
                        currentLocation = currentLocation,
                        fitRequest = fitRequest,
                        locationRequest = locationRequest,
                        onFitRoutes = { fitRequest++ },
                        onLocate = { locationRequest++ },
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                    TripRecordsPanel(
                        state = state,
                        onSelectTrip = viewModel::selectTrip,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MapPane(
    state: MapUiState,
    currentLocation: Location?,
    fitRequest: Int,
    locationRequest: Int,
    onFitRoutes: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.68f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            OsmdroidMapView(
                state = state,
                currentLocation = currentLocation,
                fitRequest = fitRequest,
                locationRequest = locationRequest,
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CardSurface.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AccentGreen, strokeWidth = 3.dp)
                }
            } else if (state.tripRoutes.isEmpty()) {
                EmptyMapHint(modifier = Modifier.align(Alignment.Center))
            }

            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapIconButton(
                    icon = Icons.Outlined.MyLocation,
                    contentDescription = "定位当前位置",
                    enabled = currentLocation != null,
                    onClick = onLocate,
                )
                MapIconButton(
                    icon = Icons.Outlined.CenterFocusStrong,
                    contentDescription = "显示全部轨迹",
                    enabled = state.tripRoutes.isNotEmpty(),
                    onClick = onFitRoutes,
                )
            }
        }
    }
}

@Composable
private fun MapIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CardSurface.copy(alpha = 0.92f)),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) TextPrimary else TextMuted,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun TripRecordsPanel(
    state: MapUiState,
    onSelectTrip: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.68f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.period.panelTitle(), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${state.trips.size} 次行程 · ${state.tripRoutes.size} 条可用轨迹",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                if (state.selectedTripId != null) {
                    Text(
                        "显示全部",
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectTrip(null) }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripSummaryMetric("行驶时长", formatTripDuration(state.totalDurationMs), AccentBlue, Modifier.weight(1f))
                TripSummaryMetric("总里程", "%.1f km".format(Locale.CHINA, state.totalKm), AccentGreen, Modifier.weight(1f))
                TripSummaryMetric("总用电", "%.1f kWh".format(Locale.CHINA, state.totalKwh), SceneAmber, Modifier.weight(1f))
                TripSummaryMetric("总用油", "%.1f L".format(Locale.CHINA, state.totalFuelL), AccentBlue, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripSummaryMetric("总电费", "%.2f 元".format(Locale.CHINA, state.totalElectricCost), AccentGreen, Modifier.weight(1f))
                TripSummaryMetric("总油费", "%.2f 元".format(Locale.CHINA, state.totalFuelCost), SceneAmber, Modifier.weight(1f))
                TripSummaryMetric(
                    "平均电耗",
                    if (state.avgConsumption > 0) "%.1f/100km".format(Locale.CHINA, state.avgConsumption) else "--",
                    if (state.avgConsumption > 0) consumptionColor(state.avgConsumption) else TextMuted,
                    Modifier.weight(1f),
                )
                TripSummaryMetric(
                    "平均油耗",
                    if (state.avgFuelConsumption > 0) "%.1f L/100km".format(Locale.CHINA, state.avgFuelConsumption) else "--",
                    AccentBlue,
                    Modifier.weight(1f),
                )
            }

            TripListHeader()
            if (state.trips.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("所选时间段暂无行程记录", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(state.trips.take(120), key = { it.id }) { trip ->
                        val hasRoute = state.tripRoutes.any { it.trip.id == trip.id }
                        TripRecordRow(
                            trip = trip,
                            hasRoute = hasRoute,
                            selected = state.selectedTripId == trip.id,
                            onClick = { if (hasRoute) onSelectTrip(trip.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripSummaryMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
        Text(
            value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TripListHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp)) {
        Text("时间 / 时长", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1.35f))
        Text("里程", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(0.72f))
        Text("用电", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(0.72f))
        Text("用油", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(0.68f))
        Text("电费 / 油费", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1.05f))
    }
}

@Composable
private fun TripRecordRow(
    trip: TripEntity,
    hasRoute: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentGreen.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = hasRoute, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1.35f)) {
            Text(formatTime(trip.startTs), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(
                (trip.endTs?.let { formatTripDuration(it - trip.startTs) } ?: "进行中") +
                    if (hasRoute) " · 有轨迹" else " · 无轨迹",
                color = if (hasRoute) AccentGreen else TextMuted,
                fontSize = 10.sp,
            )
        }
        Text(
            trip.distanceKm?.let { "%.1f km".format(Locale.CHINA, it) } ?: "--",
            color = TextPrimary,
            fontSize = 11.sp,
            modifier = Modifier.weight(0.72f),
        )
        Text(
            trip.kwhConsumed?.let { "%.1f kWh".format(Locale.CHINA, it) } ?: "--",
            color = TextPrimary,
            fontSize = 11.sp,
            modifier = Modifier.weight(0.72f),
        )
        Text(
            trip.fuelConsumedL?.let { "%.1f L".format(Locale.CHINA, it) } ?: "--",
            color = TextPrimary,
            fontSize = 11.sp,
            modifier = Modifier.weight(0.68f),
        )
        Column(modifier = Modifier.weight(1.05f)) {
            Text(
                trip.cost?.let { "电 %.2f 元".format(Locale.CHINA, it) } ?: "电 --",
                color = AccentGreen,
                fontSize = 10.sp,
            )
            Text(
                trip.fuelCost?.let { "油 %.2f 元".format(Locale.CHINA, it) } ?: "油 --",
                color = SceneAmber,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun PeriodSelector(
    period: MapPeriod,
    onPeriodSelected: (MapPeriod) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PeriodChip("日", period == MapPeriod.DAY) { onPeriodSelected(MapPeriod.DAY) }
        PeriodChip("周", period == MapPeriod.WEEK) { onPeriodSelected(MapPeriod.WEEK) }
        PeriodChip("月", period == MapPeriod.MONTH) { onPeriodSelected(MapPeriod.MONTH) }
        PeriodChip("年", period == MapPeriod.YEAR) { onPeriodSelected(MapPeriod.YEAR) }
        PeriodChip("全部", period == MapPeriod.ALL) { onPeriodSelected(MapPeriod.ALL) }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.height(40.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
            containerColor = CardSurface,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = CardBorder.copy(alpha = 0.55f),
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected,
        ),
    )
}

@Composable
private fun EmptyMapHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface.copy(alpha = 0.90f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("当前没有可显示的 GPS 轨迹", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("系统历史行程仍会显示在右侧记录中", color = TextSecondary, fontSize = 11.sp)
    }
}

private fun MapPeriod.panelTitle(): String = when (this) {
    MapPeriod.DAY -> "今日行程"
    MapPeriod.WEEK -> "近 7 天行程"
    MapPeriod.MONTH -> "近 30 天行程"
    MapPeriod.YEAR -> "近一年行程"
    MapPeriod.ALL -> "全部行程"
}

internal fun formatTripDuration(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        else -> "${minutes}分钟"
    }
}

private fun TripPointEntity.toMapGeoPoint(): GeoPoint {
    val (mapLat, mapLon) = Gcj02Converter.wgs84ToGcj02(lat, lon)
    return GeoPoint(mapLat, mapLon)
}

private fun Location.toMapGeoPoint(): GeoPoint {
    val (mapLat, mapLon) = Gcj02Converter.wgs84ToGcj02(latitude, longitude)
    return GeoPoint(mapLat, mapLon)
}

private class MapRenderState {
    var centeredOnCurrent = false
    var lastFitRequest = 0
    var lastLocationRequest = 0
    var selectionInitialized = false
    var lastSelectedTripId: Long? = null
}

@Composable
private fun OsmdroidMapView(
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
        currentPoint ?: lastRoutePoint ?: Gcj02Converter.wgs84ToGcj02(22.5431, 114.0579)
            .let { (lat, lon) -> GeoPoint(lat, lon) }
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
                val routePoints = decimateTripPoints(route.points, 350)
                val routeColor = route.kwhPer100km?.let {
                    consumptionColor(it, thresholds.good, thresholds.bad)
                } ?: AccentGreen
                val selected = state.selectedTripId == route.trip.id
                map.overlays.add(
                    Polyline().apply {
                        outlinePaint.color = routeColor.toArgb()
                        outlinePaint.strokeWidth = if (selected) 9f else 6f
                        outlinePaint.isAntiAlias = true
                        infoWindow = null
                        setPoints(routePoints.map { it.toMapGeoPoint() })
                    },
                )
            }

            displayedRoutes.forEach { route ->
                val first = route.points.firstOrNull()
                val last = route.points.lastOrNull()
                if (first != null) {
                    map.overlays.add(
                        routeBoundaryMarker(
                            mapView = map,
                            position = first.toMapGeoPoint(),
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

            currentPoint?.let { point ->
                map.overlays.add(
                    Marker(map).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "当前位置"
                    },
                )
            }

            val routeGeoPoints = displayedRoutes.flatMap { route ->
                decimateTripPoints(route.points, 350).map { it.toMapGeoPoint() }
            }
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
                selectionChanged -> fitMapToPoints(map, routeGeoPoints)
                manualFit -> fitMapToPoints(map, routeGeoPoints)
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
            map.invalidate()
        },
    )
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

@Composable
private fun rememberCurrentLocation(): Location? {
    val context = LocalContext.current
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    var currentLocation by remember(context, hasPermission) { mutableStateOf<Location?>(null) }
    DisposableEffect(context, hasPermission) {
        currentLocation = null
        if (!hasPermission) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@DisposableEffect onDispose {}
        currentLocation = bestLastKnownLocation(manager)
        val provider = when {
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
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

@Suppress("MissingPermission")
private fun bestLastKnownLocation(manager: LocationManager): Location? =
    listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
