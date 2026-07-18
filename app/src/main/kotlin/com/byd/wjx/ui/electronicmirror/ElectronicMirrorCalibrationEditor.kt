package com.byd.wjx.ui.electronicmirror

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.electronicmirror.ElectronicMirrorCalibration
import com.byd.wjx.electronicmirror.ElectronicMirrorCalibrationPoint
import com.byd.wjx.electronicmirror.ElectronicMirrorConfig
import com.byd.wjx.electronicmirror.ElectronicMirrorViewpoint
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun CalibrationEditor(
    config: ElectronicMirrorConfig,
    onUpdate: (
        ElectronicMirrorViewpoint,
        (ElectronicMirrorCalibration) -> ElectronicMirrorCalibration,
    ) -> Unit,
    onCommit: () -> Unit,
    onReset: (ElectronicMirrorViewpoint) -> Unit,
    onPreview: (ElectronicMirrorViewpoint) -> Unit,
) {
    var viewpointName by rememberSaveable { mutableStateOf(ElectronicMirrorViewpoint.LEFT.name) }
    val viewpoint = ElectronicMirrorViewpoint.entries.firstOrNull { it.name == viewpointName }
        ?: ElectronicMirrorViewpoint.LEFT
    val calibration = config.calibrationFor(viewpoint)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("全景画面微调", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "拖动四个红点截取需要的后方区域；拖动或角度调节松手后自动保存并应用",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
                Text("GPU 透视 · 零帧拷贝", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CalibrationViewpointChip(
                    label = "左后方",
                    selected = viewpoint == ElectronicMirrorViewpoint.LEFT,
                ) { viewpointName = ElectronicMirrorViewpoint.LEFT.name }
                CalibrationViewpointChip(
                    label = "右后方",
                    selected = viewpoint == ElectronicMirrorViewpoint.RIGHT,
                ) { viewpointName = ElectronicMirrorViewpoint.RIGHT.name }
                CalibrationViewpointChip(
                    label = "正后方",
                    selected = viewpoint == ElectronicMirrorViewpoint.REAR,
                ) { viewpointName = ElectronicMirrorViewpoint.REAR.name }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalibrationQuadrilateral(
                    viewpoint = viewpoint,
                    calibration = calibration,
                    onChange = { changed -> onUpdate(viewpoint) { changed } },
                    onCommit = onCommit,
                    modifier = Modifier
                        .weight(1.15f)
                        .aspectRatio(16f / 9f),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CalibrationAngleSlider(
                        label = "偏转角",
                        value = calibration.yawDegrees,
                        onChange = { value -> onUpdate(viewpoint) { it.copy(yawDegrees = value).sanitized() } },
                        onCommit = onCommit,
                    )
                    CalibrationAngleSlider(
                        label = "翻滚角",
                        value = calibration.rollDegrees,
                        onChange = { value -> onUpdate(viewpoint) { it.copy(rollDegrees = value).sanitized() } },
                        onCommit = onCommit,
                    )
                    CalibrationAngleSlider(
                        label = "俯仰角",
                        value = calibration.pitchDegrees,
                        onChange = { value -> onUpdate(viewpoint) { it.copy(pitchDegrees = value).sanitized() } },
                        onCommit = onCommit,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            Text("重置", fontWeight = FontWeight.Bold)
                        }
                        PreviewButton("预览此视角", viewpoint, onPreview)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationQuadrilateral(
    viewpoint: ElectronicMirrorViewpoint,
    calibration: ElectronicMirrorCalibration,
    onChange: (ElectronicMirrorCalibration) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val paddingPx = with(density) { 20.dp.toPx() }
    val touchRadiusPx = with(density) { 42.dp.toPx() }
    val latestCalibration = rememberUpdatedState(calibration)
    var activePoint by remember(viewpoint) { mutableStateOf<Int?>(null) }
    val pointColor = Color(0xFFFF5A64)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF07131A), Color(0xFF12303A), Color(0xFF09161D)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .pointerInput(viewpoint) {
                fun pointOffset(point: ElectronicMirrorCalibrationPoint): Offset {
                    val usableWidth = (size.width - paddingPx * 2f).coerceAtLeast(1f)
                    val usableHeight = (size.height - paddingPx * 2f).coerceAtLeast(1f)
                    return Offset(
                        x = paddingPx + usableWidth * point.xPermille / 1000f,
                        y = paddingPx + usableHeight * point.yPermille / 1000f,
                    )
                }

                detectDragGestures(
                    onDragStart = { position ->
                        activePoint = latestCalibration.value.points
                            .mapIndexed { index, point -> index to (pointOffset(point) - position).getDistance() }
                            .minByOrNull { it.second }
                            ?.takeIf { it.second <= touchRadiusPx }
                            ?.first
                    },
                    onDragCancel = {
                        if (activePoint != null) onCommit()
                        activePoint = null
                    },
                    onDragEnd = {
                        if (activePoint != null) onCommit()
                        activePoint = null
                    },
                    onDrag = { change, _ ->
                        val index = activePoint ?: return@detectDragGestures
                        val usableWidth = (size.width - paddingPx * 2f).coerceAtLeast(1f)
                        val usableHeight = (size.height - paddingPx * 2f).coerceAtLeast(1f)
                        val point = ElectronicMirrorCalibrationPoint(
                            xPermille = (((change.position.x - paddingPx) / usableWidth) * 1000f).roundToInt(),
                            yPermille = (((change.position.y - paddingPx) / usableHeight) * 1000f).roundToInt(),
                        )
                        onChange(latestCalibration.value.withPoint(index, point))
                        change.consume()
                    },
                )
            },
    ) {
        val usableWidth = (size.width - paddingPx * 2f).coerceAtLeast(1f)
        val usableHeight = (size.height - paddingPx * 2f).coerceAtLeast(1f)
        val offsets = calibration.points.map { point ->
            Offset(
                x = paddingPx + usableWidth * point.xPermille / 1000f,
                y = paddingPx + usableHeight * point.yPermille / 1000f,
            )
        }

        for (step in 1..3) {
            val x = paddingPx + usableWidth * step / 4f
            val y = paddingPx + usableHeight * step / 4f
            drawLine(Color.White.copy(alpha = 0.08f), Offset(x, paddingPx), Offset(x, size.height - paddingPx))
            drawLine(Color.White.copy(alpha = 0.08f), Offset(paddingPx, y), Offset(size.width - paddingPx, y))
        }

        val path = Path().apply {
            moveTo(offsets[0].x, offsets[0].y)
            lineTo(offsets[1].x, offsets[1].y)
            lineTo(offsets[2].x, offsets[2].y)
            lineTo(offsets[3].x, offsets[3].y)
            close()
        }
        drawPath(path, AccentGreen.copy(alpha = 0.13f))
        drawPath(path, AccentGreen, style = Stroke(width = 3.dp.toPx()))
        offsets.forEachIndexed { index, offset ->
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 10.dp.toPx(), center = offset)
            drawCircle(
                if (activePoint == index) Color.White else pointColor,
                radius = if (activePoint == index) 7.dp.toPx() else 6.dp.toPx(),
                center = offset,
            )
        }
    }
}

@Composable
private fun CalibrationAngleSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                if (value > 0) "+$value°" else "$value°",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onCommit,
            valueRange = ElectronicMirrorCalibration.MIN_ANGLE_DEGREES.toFloat()..
                ElectronicMirrorCalibration.MAX_ANGLE_DEGREES.toFloat(),
            steps = ElectronicMirrorCalibration.MAX_ANGLE_DEGREES -
                ElectronicMirrorCalibration.MIN_ANGLE_DEGREES - 1,
        )
    }
}

@Composable
private fun CalibrationViewpointChip(
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
            containerColor = Color.Transparent,
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
