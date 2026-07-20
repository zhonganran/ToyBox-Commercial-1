package com.byd.wjx.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.data.local.entity.TripEntity
import com.byd.wjx.data.local.entity.TripPointEntity
import com.byd.wjx.ui.components.LocalConsumptionThresholds
import com.byd.wjx.ui.components.consumptionColor
import com.byd.wjx.ui.components.formatTime
import com.byd.wjx.SuperToyBoxApp
import com.byd.wjx.ui.theme.*
import com.byd.wjx.util.Gcj02Converter
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var fitRequest by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Map area
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                OsmdroidMapView(state = state, fitRequest = fitRequest)

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapHeader(
                        routeCount = state.tripRoutes.size,
                        chargeCount = state.charges.size,
                        onBack = onBack,
                    )
                    PeriodSelector(
                        period = state.period,
                        onBack = null,
                        onPeriodSelected = viewModel::setPeriod,
                    )
                }

                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CardSurface.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentGreen, strokeWidth = 3.dp)
                    }
                } else if (state.tripRoutes.isEmpty() && state.charges.isEmpty()) {
                    EmptyMapHint(modifier = Modifier.align(Alignment.Center))
                }

                // Zoom-to-fit button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CardSurface.copy(alpha = 0.9f))
                        .clickable { fitRequest++ },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⊙", color = TextPrimary, fontSize = 18.sp)
                }

                // Panel toggle if collapsed
                if (!state.panelExpanded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CardSurface.copy(alpha = 0.9f))
                            .clickable { viewModel.togglePanel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("◀", color = TextPrimary, fontSize = 16.sp)
                    }
                }
            }

            // Side panel
            AnimatedVisibility(
                visible = state.panelExpanded,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it })
            ) {
                SidePanel(
                    state = state,
                    onClose = { viewModel.togglePanel() },
                    onSelectTrip = viewModel::selectTrip
                )
            }
        }
    }
}

@Composable
private fun MapHeader(
    routeCount: Int,
    chargeCount: Int,
    onBack: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                MapActionChip("返回", onBack)
            }
            Column {
                Text("行程地图", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${routeCount} 条轨迹 · ${chargeCount} 个充电记录",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    period: MapPeriod,
    onBack: (() -> Unit)?,
    onPeriodSelected: (MapPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (onBack != null) {
            MapActionChip("返回", onBack)
        }
        PeriodChip("日", period == MapPeriod.DAY) { onPeriodSelected(MapPeriod.DAY) }
        PeriodChip("周", period == MapPeriod.WEEK) { onPeriodSelected(MapPeriod.WEEK) }
        PeriodChip("月", period == MapPeriod.MONTH) { onPeriodSelected(MapPeriod.MONTH) }
        PeriodChip("年", period == MapPeriod.YEAR) { onPeriodSelected(MapPeriod.YEAR) }
        PeriodChip("全部", period == MapPeriod.ALL) { onPeriodSelected(MapPeriod.ALL) }
    }
}

@Composable
private fun EmptyMapHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface.copy(alpha = 0.88f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("所选时间段暂无轨迹", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("有 GPS 点的行程会自动显示在这里", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun MapActionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface.copy(alpha = 0.92f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SidePanel(
    state: MapUiState,
    onClose: () -> Unit,
    onSelectTrip: (Long?) -> Unit
) {
    val selectedTrip = remember(state.trips, state.selectedTripId) {
        state.trips.firstOrNull { it.id == state.selectedTripId }
    }

    Card(
        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = Modifier
            .width(244.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val periodLabel = state.period.panelTitle()
                Text(periodLabel, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("▶", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.clickable { onClose() })
            }

            // Stats
            StatLine("距离", "%.1f km".format(state.totalKm))
            StatLine("能耗", "%.1f 千瓦时".format(state.totalKwh))
            if (state.avgConsumption > 0) {
                StatLine("平均电耗", "%.1f/100km".format(state.avgConsumption),
                    valueColor = consumptionColor(state.avgConsumption))
            }
            StatLine("行程", "${state.trips.size}")
            StatLine("轨迹", "${state.tripRoutes.size}")
            StatLine("充电", "${state.charges.size}")

            if (selectedTrip != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("当前只看", color = TextSecondary, fontSize = 12.sp)
                SelectedTripSummary(trip = selectedTrip, onShowAll = { onSelectTrip(null) })
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("行程", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.trips.take(80), key = { it.id }) { trip ->
                    val hasRoute = state.tripRoutes.any { it.trip.id == trip.id }
                    val selected = state.selectedTripId == trip.id
                    val time = formatTime(trip.startTs)
                    val dist = trip.distanceKm?.let { "%.1f km".format(it) } ?: "—"
                    val consumption = trip.kwhPer100km
                    val color = consumption?.let { consumptionColor(it) } ?: TextSecondary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) AccentGreen.copy(alpha = 0.16f) else Color.Transparent
                            )
                            .clickable(enabled = hasRoute) { onSelectTrip(trip.id) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(time, color = if (hasRoute) TextSecondary else TextMuted, fontSize = 12.sp)
                        Text(dist, color = if (hasRoute) TextPrimary else TextMuted, fontSize = 12.sp)
                        Text(
                            if (hasRoute) consumption?.let { "%.0f".format(it) } ?: "—" else "无轨迹",
                            color = if (hasRoute) color else TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedTripSummary(trip: TripEntity, onShowAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NavyDark.copy(alpha = 0.42f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        StatLine("时间", formatTime(trip.startTs))
        StatLine("距离", trip.distanceKm?.let { "%.1f km".format(it) } ?: "—")
        trip.kwhPer100km?.let {
            StatLine("电耗", "%.1f/100km".format(it), consumptionColor(it))
        }
        Text(
            "显示全部轨迹",
            color = AccentGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onShowAll() }
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun StatLine(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = valueColor ?: TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.height(44.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
            containerColor = CardSurface.copy(alpha = 0.9f),
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected
        )
    )
}

private fun MapPeriod.panelTitle(): String = when (this) {
    MapPeriod.DAY -> "今日轨迹"
    MapPeriod.WEEK -> "近7天轨迹"
    MapPeriod.MONTH -> "近30天轨迹"
    MapPeriod.YEAR -> "近一年轨迹"
    MapPeriod.ALL -> "全部轨迹"
}

private fun TripPointEntity.toMapGeoPoint(): GeoPoint {
    val (mapLat, mapLon) = Gcj02Converter.wgs84ToGcj02(lat, lon)
    return GeoPoint(mapLat, mapLon)
}

@Composable
private fun OsmdroidMapView(state: MapUiState, fitRequest: Int) {
    val context = LocalContext.current
    val thresholds = LocalConsumptionThresholds.current

    val displayedRoutes = remember(state.tripRoutes, state.selectedTripId) {
        state.selectedTripId?.let { selectedId ->
            state.tripRoutes.filter { it.trip.id == selectedId }
        } ?: state.tripRoutes
    }
    val displayedCharges = remember(state.charges, state.selectedTripId) {
        if (state.selectedTripId == null) state.charges else emptyList()
    }

    val lastPoint = remember(displayedRoutes) {
        displayedRoutes.lastOrNull()?.points?.lastOrNull()?.let {
            it.toMapGeoPoint()
        }
    }

    val defaultCenter = remember {
        val (lat, lon) = Gcj02Converter.wgs84ToGcj02(22.5431, 114.0579)
        GeoPoint(lat, lon)
    }
    val center = lastPoint ?: defaultCenter

    val mapView = remember(context) {
        OsmdroidRuntime.ensureInitialized(context)
        MapView(context).apply {
            setTileSource(SuperToyBoxApp.AmapTileSource)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(center)
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

            // Draw trip routes colored by consumption
            for (route in displayedRoutes) {
                if (route.points.size < 2) continue
                val routePoints = decimateTripPoints(route.points, 350)

                val routeColor = route.kwhPer100km?.let {
                    consumptionColor(it, thresholds.good, thresholds.bad)
                } ?: AccentGreen
                val selected = state.selectedTripId == route.trip.id

                val polyline = Polyline().apply {
                    outlinePaint.color = routeColor.toArgb()
                    outlinePaint.strokeWidth = if (selected) 9f else 6f
                    outlinePaint.isAntiAlias = true
                    infoWindow = null
                }

                val pts = routePoints.map { it.toMapGeoPoint() }
                polyline.setPoints(pts)
                map.overlays.add(polyline)
            }

            for (route in displayedRoutes) {
                val first = route.points.firstOrNull()
                val last = route.points.lastOrNull()
                if (first != null) {
                    map.overlays.add(
                        routeBoundaryMarker(
                            mapView = map,
                            position = first.toMapGeoPoint(),
                            title = "起点 ${formatTime(route.trip.startTs)}",
                            kind = RouteBoundaryKind.Start
                        )
                    )
                }
                if (last != null && last != first) {
                    val endTitle = route.trip.endTs?.let { "终点 ${formatTime(it)}" } ?: "终点"
                    map.overlays.add(
                        routeBoundaryMarker(
                            mapView = map,
                            position = last.toMapGeoPoint(),
                            title = endTitle,
                            kind = RouteBoundaryKind.End
                        )
                    )
                }
            }

            // Charge markers colored by type
            for (charge in displayedCharges) {
                val lat = charge.lat ?: continue
                val lon = charge.lon ?: continue
                val (mLat, mLon) = Gcj02Converter.wgs84ToGcj02(lat, lon)

                val marker = Marker(map).apply {
                    position = GeoPoint(mLat, mLon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = buildString {
                        append(charge.type ?: "充电")
                        charge.kwhCharged?.let { append(" %.1f 千瓦时".format(it)) }
                    }
                }
                map.overlays.add(marker)
            }

            // Zoom to fit all points
            val allGeoPoints = mutableListOf<GeoPoint>()
            displayedRoutes.forEach { route ->
                decimateTripPoints(route.points, 350).forEach {
                    allGeoPoints.add(it.toMapGeoPoint())
                }
            }
            displayedCharges.forEach { charge ->
                val lat = charge.lat ?: return@forEach
                val lon = charge.lon ?: return@forEach
                val (mLat, mLon) = Gcj02Converter.wgs84ToGcj02(lat, lon)
                allGeoPoints.add(GeoPoint(mLat, mLon))
            }
            if (allGeoPoints.size >= 2) {
                try {
                    val bbox = BoundingBox.fromGeoPoints(allGeoPoints)
                    map.zoomToBoundingBox(bbox, fitRequest > 0, 50)
                } catch (_: Exception) {
                    map.controller.setCenter(center)
                }
            } else if (allGeoPoints.size == 1) {
                map.controller.setZoom(15.0)
                map.controller.setCenter(allGeoPoints.first())
            } else if (lastPoint != null) {
                map.controller.setCenter(lastPoint)
            }

            map.invalidate()
        }
    )
}
