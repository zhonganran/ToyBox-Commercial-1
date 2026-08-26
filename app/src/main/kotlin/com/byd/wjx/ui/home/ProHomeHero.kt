package com.byd.wjx.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.byd.wjx.ui.dashboard.DashboardUiState

@Composable
internal fun HomeHero(
    state: DashboardUiState,
    image: ImageBitmap?,
    onPickImage: () -> Unit,
    onDeleteImage: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    if (stacked) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().height(225.dp)) {
                SocGauge(state, Modifier.weight(1f).fillMaxHeight())
                SpeedGauge(state, Modifier.weight(1f).fillMaxHeight())
            }
            VehicleStage(
                state = state,
                image = image,
                onPickImage = onPickImage,
                onDeleteImage = onDeleteImage,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            SocGauge(state, Modifier.width(310.dp).fillMaxHeight())
            VehicleStage(
                state = state,
                image = image,
                onPickImage = onPickImage,
                onDeleteImage = onDeleteImage,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SpeedGauge(state, Modifier.width(310.dp).fillMaxHeight())
        }
    }
}
