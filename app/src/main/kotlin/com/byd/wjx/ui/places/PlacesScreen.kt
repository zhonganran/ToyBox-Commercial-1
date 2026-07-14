package com.byd.wjx.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.byd.wjx.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.data.local.entity.PlaceEntity
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import com.byd.wjx.ui.pro.ProPageHeader

@Composable
fun PlacesScreen(
    viewModel: PlacesViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    val usageCounts by viewModel.usageCounts.collectAsStateWithLifecycle()
    val tileSource by viewModel.mapTileSource.collectAsStateWithLifecycle()
    val lastLocation by viewModel.lastLocation.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Collect delete-blocked events and show snackbar.
    LaunchedEffect(viewModel.deleteBlocked) {
        viewModel.deleteBlocked.collect { count ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.places_delete_blocked_message, count)
            )
        }
    }

    // null = not editing; non-null = editing that entity
    var editing by remember { mutableStateOf<PlaceEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Show dialog when editing an existing place or adding a new one
    if (editing != null) {
        PlaceEditDialog(
            initial = editing,
            tileSource = tileSource,
            lastLocation = lastLocation,
            onDismiss = { editing = null },
            onSave = { id, name, lat, lon, r ->
                viewModel.save(id, name, lat, lon, r)
                editing = null
            }
        )
    } else if (showAddDialog) {
        PlaceEditDialog(
            initial = null,
            tileSource = tileSource,
            lastLocation = lastLocation,
            onDismiss = { showAddDialog = false },
            onSave = { id, name, lat, lon, r ->
                viewModel.save(id, name, lat, lon, r)
                showAddDialog = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.places_back_content_description),
                        tint = TextPrimary
                    )
                }
                ProPageHeader(
                    eyebrow = "PLACES",
                    title = stringResource(R.string.places_tab_title),
                    subtitle = "用于智能联动和行程地点判定",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Place list or empty state
            if (places.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.places_empty),
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(places, key = { it.id }) { place ->
                        PlaceRow(
                            place = place,
                            usageCount = usageCounts[place.id] ?: 0,
                            onEdit = { editing = place },
                            onDelete = { viewModel.delete(place) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Add button
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = AccentContent
                )
            ) {
                Text(stringResource(R.string.places_add_button), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }
}

@Composable
private fun PlaceRow(
    place: PlaceEntity,
    usageCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val inUse = usageCount > 0
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.places_row_coords, place.lat, place.lon, place.radiusM),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                if (inUse) {
                    Text(
                        text = stringResource(R.string.places_row_in_use, usageCount),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.places_edit_content_description),
                        tint = AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = !inUse,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.places_delete_content_description),
                        tint = if (inUse) TextMuted else SocRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
