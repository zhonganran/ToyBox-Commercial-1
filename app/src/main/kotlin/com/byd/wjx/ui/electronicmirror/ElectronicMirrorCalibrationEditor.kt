package com.byd.wjx.ui.electronicmirror

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.electronicmirror.ElectronicMirrorConfig
import com.byd.wjx.electronicmirror.ElectronicMirrorProjectionSpec
import com.byd.wjx.electronicmirror.ElectronicMirrorViewpoint
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun ProjectionEditor(
    config: ElectronicMirrorConfig,
    onUpdate: (
        ElectronicMirrorViewpoint,
        (ElectronicMirrorProjectionSpec) -> ElectronicMirrorProjectionSpec,
    ) -> Unit,
    onCommit: () -> Unit,
    onReset: (ElectronicMirrorViewpoint) -> Unit,
    onPreview: (ElectronicMirrorViewpoint) -> Unit,
) {
    var viewpointName by rememberSaveable { mutableStateOf(ElectronicMirrorViewpoint.REAR.name) }
    val viewpoint = ElectronicMirrorViewpoint.entries.firstOrNull { it.name == viewpointName }
        ?: ElectronicMirrorViewpoint.REAR
    val projection = config.projectionFor(viewpoint)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("画面微调", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "默认按实车验证值：左 -0.12、右 +0.12；可继续微调后向范围",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
                Text("全景母流裁切 · 零帧拷贝", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProjectionViewpointChip(
                    label = "左后方",
                    selected = viewpoint == ElectronicMirrorViewpoint.LEFT,
                ) { viewpointName = ElectronicMirrorViewpoint.LEFT.name }
                ProjectionViewpointChip(
                    label = "右后方",
                    selected = viewpoint == ElectronicMirrorViewpoint.RIGHT,
                ) { viewpointName = ElectronicMirrorViewpoint.RIGHT.name }
                ProjectionViewpointChip(
                    label = "正后方",
                    selected = viewpoint == ElectronicMirrorViewpoint.REAR,
                ) { viewpointName = ElectronicMirrorViewpoint.REAR.name }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ProjectionSlider(
                    label = "左右视角",
                    value = projection.horizontalPermille,
                    range = when (viewpoint) {
                        ElectronicMirrorViewpoint.LEFT ->
                            ElectronicMirrorProjectionSpec.MIN_SIDE_HORIZONTAL..0
                        ElectronicMirrorViewpoint.RIGHT ->
                            0..ElectronicMirrorProjectionSpec.MAX_SIDE_HORIZONTAL
                        ElectronicMirrorViewpoint.REAR ->
                            ElectronicMirrorProjectionSpec.MIN_REAR_HORIZONTAL..
                                ElectronicMirrorProjectionSpec.MAX_REAR_HORIZONTAL
                    },
                    displayValue = ::signedPercent,
                    onChange = { value ->
                        onUpdate(viewpoint) { it.copy(horizontalPermille = value).sanitized() }
                    },
                    onCommit = onCommit,
                    modifier = Modifier.weight(1f),
                )
                ProjectionSlider(
                    label = "画面高度",
                    value = projection.heightOffsetPermille,
                    range = if (viewpoint == ElectronicMirrorViewpoint.REAR) {
                        ElectronicMirrorProjectionSpec.MIN_REAR_HEIGHT_OFFSET..
                            ElectronicMirrorProjectionSpec.MAX_REAR_HEIGHT_OFFSET
                    } else {
                        ElectronicMirrorProjectionSpec.MIN_SIDE_HEIGHT_OFFSET..
                            ElectronicMirrorProjectionSpec.MAX_SIDE_HEIGHT_OFFSET
                    },
                    displayValue = ::signedPercent,
                    onChange = { value ->
                        onUpdate(viewpoint) { it.copy(heightOffsetPermille = value).sanitized() }
                    },
                    onCommit = onCommit,
                    modifier = Modifier.weight(1f),
                )
                ProjectionSlider(
                    label = "画面缩放",
                    value = projection.viewScalePermille,
                    range = if (viewpoint == ElectronicMirrorViewpoint.REAR) {
                        ElectronicMirrorProjectionSpec.MIN_REAR_VIEW_SCALE..
                            ElectronicMirrorProjectionSpec.MAX_REAR_VIEW_SCALE
                    } else {
                        ElectronicMirrorProjectionSpec.MIN_SIDE_VIEW_SCALE..
                            ElectronicMirrorProjectionSpec.MAX_SIDE_VIEW_SCALE
                    },
                    displayValue = { "${it / 10f}%" },
                    onChange = { value ->
                        onUpdate(viewpoint) { it.copy(viewScalePermille = value).sanitized() }
                    },
                    onCommit = onCommit,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onReset(viewpoint) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardSurface,
                        contentColor = TextPrimary,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("重置当前视角", fontWeight = FontWeight.Bold)
                }
                PreviewButton("预览当前视角", viewpoint, onPreview)
                Text(
                    "滑块松手即保存并立即应用",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ProjectionSlider(
    label: String,
    value: Int,
    range: IntRange,
    displayValue: (Int) -> String,
    onChange: (Int) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                displayValue(value),
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onCommit,
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun ProjectionViewpointChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen.copy(alpha = 0.18f),
            selectedLabelColor = TextPrimary,
            containerColor = CardSurface.copy(alpha = 0.16f),
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CardBorder,
            selectedBorderColor = AccentGreen.copy(alpha = 0.6f),
        ),
    )
}

private fun signedPercent(value: Int): String {
    val percent = value / 10f
    return if (value > 0) "+$percent%" else "$percent%"
}
