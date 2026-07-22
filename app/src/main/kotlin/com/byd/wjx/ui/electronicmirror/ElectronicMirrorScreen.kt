package com.byd.wjx.ui.electronicmirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.electronicmirror.ElectronicMirrorGeometry
import com.byd.wjx.electronicmirror.ElectronicMirrorRuntimeStatus
import com.byd.wjx.electronicmirror.ElectronicMirrorViewpoint
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun ElectronicMirrorSettingsContent(
    viewModel: ElectronicMirrorViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose(viewModel::stopPreview)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MirrorEmbeddedStatus(
            active = active,
            status = status,
            fps = config.fps,
            qualityWidth = config.qualityWidth,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            StreamingRearMirrorCard(
                config = config,
                onUpdate = viewModel::updateAndApply,
                modifier = Modifier.weight(0.9f),
            )
            MirrorLayoutPreview(
                target = config.streamingRearDisplayTarget,
                geometry = config.streamingRearGeometryFor(),
                modifier = Modifier.weight(1.1f),
                windowLabel = "流媒体后视窗口",
            )
        }

        GeometryEditor(
            geometry = config.streamingRearGeometryFor(),
            target = config.streamingRearDisplayTarget,
            onUpdate = viewModel::updateStreamingRearGeometry,
            onCommit = viewModel::commitStreamingRearGeometry,
            title = "流媒体后视窗口调整",
            widthRange = ElectronicMirrorGeometry.MIN_STREAMING_WIDTH_PCT..
                ElectronicMirrorGeometry.MAX_STREAMING_WIDTH_PCT,
            heightRange = ElectronicMirrorGeometry.MIN_STREAMING_HEIGHT_PCT..
                ElectronicMirrorGeometry.MAX_STREAMING_HEIGHT_PCT,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MirrorControlCard(
                config = config,
                onUpdate = viewModel::updateAndApply,
                modifier = Modifier.weight(0.9f),
            )
            MirrorLayoutPreview(
                target = config.displayTarget,
                geometry = config.geometryFor(),
                modifier = Modifier.weight(1.1f),
            )
        }

        GeometryEditor(
            geometry = config.geometryFor(),
            target = config.displayTarget,
            onUpdate = viewModel::updateSelectedGeometry,
            onCommit = viewModel::commitGeometry,
            horizontalLabel = "左右窗口内收",
            horizontalRange = ElectronicMirrorGeometry.MIN_SIDE_INSET_PCT..
                ElectronicMirrorGeometry.MAX_SIDE_INSET_PCT,
            widthRange = ElectronicMirrorGeometry.MIN_SIDE_WIDTH_PCT..
                ElectronicMirrorGeometry.MAX_SIDE_WIDTH_PCT,
            heightRange = ElectronicMirrorGeometry.MIN_SIDE_HEIGHT_PCT..
                ElectronicMirrorGeometry.MAX_SIDE_HEIGHT_PCT,
            heightLabel = "高度上限（16:9）",
        )

        SourceMappingEditor(
            config = config,
            onAssign = viewModel::assignSource,
            onReset = viewModel::resetSources,
            onPreview = viewModel::preview,
        )

        ProjectionEditor(
            config = config,
            onUpdate = viewModel::updateProjection,
            onCommit = viewModel::commitProjection,
            onReset = viewModel::resetProjection,
            onPreview = viewModel::preview,
        )

        MirrorPreviewActions(
            onPreview = viewModel::preview,
            onStopPreview = viewModel::stopPreview,
        )

        Text(
            "所有开关、显示位置和图像顺序点击后立即保存；窗口与画面微调在滑块松手后自动保存。",
            color = AccentGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "原车自动泊车和360影像拥有最高相机优先级；电子后视镜、仪表歌词和桌面歌词可以同时显示。转向结束后自动恢复流媒体后视。",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun MirrorEmbeddedStatus(
    active: Boolean,
    status: ElectronicMirrorRuntimeStatus,
    fps: Int,
    qualityWidth: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) AccentGreen.copy(alpha = 0.5f) else CardBorder,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(9.dp).background(if (active) AccentGreen else TextMuted, CircleShape))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (active) "电子后视镜运行中" else "电子后视镜待机",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(status.message, color = TextSecondary, fontSize = 12.sp)
            }
            Text(
                "$fps FPS · ${qualityWidth}px",
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MirrorPreviewActions(
    onPreview: (ElectronicMirrorViewpoint) -> Unit,
    onStopPreview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewButton("预览左后侧", ElectronicMirrorViewpoint.LEFT, onPreview)
        PreviewButton("预览右后侧", ElectronicMirrorViewpoint.RIGHT, onPreview)
        PreviewButton("预览正后方", ElectronicMirrorViewpoint.REAR, onPreview)
        Button(
            onClick = onStopPreview,
            colors = ButtonDefaults.buttonColors(
                containerColor = CardSurface.copy(alpha = 0.82f),
                contentColor = TextPrimary,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(52.dp),
        ) {
            Icon(Icons.Outlined.StopCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("停止预览", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun PreviewButton(
    label: String,
    viewpoint: ElectronicMirrorViewpoint,
    onPreview: (ElectronicMirrorViewpoint) -> Unit,
) {
    Button(
        onClick = { onPreview(viewpoint) },
        colors = ButtonDefaults.buttonColors(
            containerColor = SceneBlue.copy(alpha = 0.18f),
            contentColor = TextPrimary,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(52.dp),
    ) {
        Icon(Icons.Outlined.Visibility, contentDescription = null)
        Spacer(Modifier.width(7.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}
