package com.byd.wjx.ui.electronicmirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun ElectronicMirrorScreen(
    onBack: () -> Unit,
    viewModel: ElectronicMirrorViewModel = hiltViewModel(),
) {
    ElectronicMirrorSettingsContent(
        embedded = false,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@Composable
fun ElectronicMirrorSettingsContent(
    embedded: Boolean,
    onBack: () -> Unit = {},
    viewModel: ElectronicMirrorViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose(viewModel::stopPreview)
    }

    Column(
        modifier = if (embedded) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 22.dp)
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (embedded) {
            MirrorEmbeddedStatus(active = active, status = status)
        } else {
            MirrorHeader(onBack = onBack, active = active, status = status)
        }

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
        )

        SourceMappingEditor(
            config = config,
            onAssign = viewModel::assignSource,
            onReset = viewModel::resetSources,
            onPreview = viewModel::preview,
        )

        CalibrationEditor(
            config = config,
            onUpdate = viewModel::updateCalibration,
            onCommit = viewModel::commitCalibration,
            onReset = viewModel::resetCalibration,
            onPreview = viewModel::preview,
        )

        MirrorPreviewActions(
            onPreview = viewModel::preview,
            onStopPreview = viewModel::stopPreview,
        )

        Text(
            "所有开关、显示位置和图像顺序点击后立即保存；滑块与裁剪在松手后自动保存。",
            color = AccentGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "画面优先级：原车自动泊车/全景影像 ＞ 倒挡 ＞ 转向灯 ＞ 仪表投屏和歌词。退出转向后会自动恢复此前内容。",
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
                "30 FPS · Surface 直出",
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MirrorHeader(
    onBack: () -> Unit,
    active: Boolean,
    status: ElectronicMirrorRuntimeStatus,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CardSurface.copy(alpha = 0.72f)),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = TextPrimary)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("电子后视镜", color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("转向灯触发 APA 侧后方画面，仪表盘与中控屏可分别调整布局", color = TextSecondary, fontSize = 14.sp)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (active) AccentGreen.copy(alpha = 0.18f) else CardSurface.copy(alpha = 0.72f))
                .border(1.dp, if (active) AccentGreen.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(if (active) AccentGreen else TextMuted, CircleShape))
            Text(
                status.message,
                color = if (active) AccentGreen else TextSecondary,
                fontSize = 13.sp,
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
