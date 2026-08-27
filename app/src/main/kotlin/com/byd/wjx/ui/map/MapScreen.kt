package com.byd.wjx.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.pro.ProPageHeader
import com.byd.wjx.ui.theme.TextPrimary

@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLocation = rememberCurrentLocation()
    var fitRequest by remember { mutableIntStateOf(0) }
    var locationRequest by remember { mutableIntStateOf(0) }
    var dashcamPlaybackTarget by remember { mutableStateOf<DashcamPlaybackTarget?>(null) }
    val dashcamFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let(viewModel::selectDashcamFolder)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 880.dp
        Column(
            modifier = Modifier.fillMaxSize().padding(
                horizontal = if (wide) 20.dp else 12.dp,
                vertical = 10.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapHeader(state, onBack, viewModel::setPeriod)
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
                        onOpenDashcamAtTime = { dashcamPlaybackTarget = it },
                        modifier = Modifier.weight(0.82f).fillMaxHeight(),
                    )
                    TripRecordsOrVideoPanel(
                        state = state,
                        playbackTarget = dashcamPlaybackTarget,
                        onSelectTrip = viewModel::selectTrip,
                        onSelectDashcamFolder = { dashcamFolderLauncher.launch(null) },
                        onRefreshDashcamVideos = viewModel::refreshDashcamVideos,
                        onClearDashcamFolder = {
                            dashcamPlaybackTarget = null
                            viewModel.clearDashcamFolder()
                        },
                        onOpenDashcamVideos = { tripId ->
                            dashcamPlaybackTarget = DashcamPlaybackTarget(tripId = tripId)
                        },
                        onDismissVideo = { dashcamPlaybackTarget = null },
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
                        onOpenDashcamAtTime = { dashcamPlaybackTarget = it },
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                    TripRecordsOrVideoPanel(
                        state = state,
                        playbackTarget = dashcamPlaybackTarget,
                        onSelectTrip = viewModel::selectTrip,
                        onSelectDashcamFolder = { dashcamFolderLauncher.launch(null) },
                        onRefreshDashcamVideos = viewModel::refreshDashcamVideos,
                        onClearDashcamFolder = {
                            dashcamPlaybackTarget = null
                            viewModel.clearDashcamFolder()
                        },
                        onOpenDashcamVideos = { tripId ->
                            dashcamPlaybackTarget = DashcamPlaybackTarget(tripId = tripId)
                        },
                        onDismissVideo = { dashcamPlaybackTarget = null },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TripRecordsOrVideoPanel(
    state: MapUiState,
    playbackTarget: DashcamPlaybackTarget?,
    onSelectTrip: (Long?) -> Unit,
    onSelectDashcamFolder: () -> Unit,
    onRefreshDashcamVideos: () -> Unit,
    onClearDashcamFolder: () -> Unit,
    onOpenDashcamVideos: (Long) -> Unit,
    onDismissVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = playbackTarget?.let { target ->
        state.trips.firstOrNull { it.id == target.tripId }
    }
    val clips = playbackTarget?.let { target ->
        state.dashcamVideosByTrip[target.tripId].orEmpty()
    }.orEmpty()
    if (playbackTarget != null && trip != null && clips.isNotEmpty()) {
        DashcamVideoPanel(
            trip = trip,
            clips = clips,
            initialClipUri = playbackTarget.clipUri,
            initialPositionMs = playbackTarget.positionMs,
            onDismiss = onDismissVideo,
            modifier = modifier,
        )
    } else {
        TripRecordsPanel(
            state = state,
            onSelectTrip = onSelectTrip,
            onSelectDashcamFolder = onSelectDashcamFolder,
            onRefreshDashcamVideos = onRefreshDashcamVideos,
            onClearDashcamFolder = onClearDashcamFolder,
            onOpenDashcamVideos = onOpenDashcamVideos,
            modifier = modifier,
        )
    }
}

@Composable
private fun MapHeader(
    state: MapUiState,
    onBack: (() -> Unit)?,
    onPeriodSelected: (MapPeriod) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        ProPageHeader(
            eyebrow = "TRIPS & ROUTES",
            title = "行程与轨迹",
            subtitle = "行程记录、行驶时长、里程、能耗与 GPS 轨迹",
            modifier = Modifier.weight(1f),
            trailing = {
                PeriodSelector(state.period, onPeriodSelected)
            },
        )
    }
}
