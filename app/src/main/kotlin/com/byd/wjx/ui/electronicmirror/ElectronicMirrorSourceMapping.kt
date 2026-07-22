package com.byd.wjx.ui.electronicmirror

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.electronicmirror.ElectronicMirrorCameraSource
import com.byd.wjx.electronicmirror.ElectronicMirrorConfig
import com.byd.wjx.electronicmirror.ElectronicMirrorViewpoint
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun SourceMappingEditor(
    config: ElectronicMirrorConfig,
    onAssign: (ElectronicMirrorViewpoint, ElectronicMirrorCameraSource) -> Unit,
    onReset: () -> Unit,
    onPreview: (ElectronicMirrorViewpoint) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = null,
                    tint = SceneBlue,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("图像顺序", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "所有画面都从四格全景母流中裁切，可在这里统一交换左、右、后方顺序",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardSurface,
                        contentColor = TextPrimary,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("恢复默认顺序", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SourceMappingColumn(
                    title = "左后方",
                    viewpoint = ElectronicMirrorViewpoint.LEFT,
                    selectedSource = config.sourceFor(ElectronicMirrorViewpoint.LEFT),
                    onAssign = onAssign,
                    onPreview = onPreview,
                    modifier = Modifier.weight(1f),
                )
                SourceMappingColumn(
                    title = "右后方",
                    viewpoint = ElectronicMirrorViewpoint.RIGHT,
                    selectedSource = config.sourceFor(ElectronicMirrorViewpoint.RIGHT),
                    onAssign = onAssign,
                    onPreview = onPreview,
                    modifier = Modifier.weight(1f),
                )
                SourceMappingColumn(
                    title = "正后方",
                    viewpoint = ElectronicMirrorViewpoint.REAR,
                    selectedSource = config.sourceFor(ElectronicMirrorViewpoint.REAR),
                    onAssign = onAssign,
                    onPreview = onPreview,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SourceMappingColumn(
    title: String,
    viewpoint: ElectronicMirrorViewpoint,
    selectedSource: ElectronicMirrorCameraSource,
    onAssign: (ElectronicMirrorViewpoint, ElectronicMirrorCameraSource) -> Unit,
    onPreview: (ElectronicMirrorViewpoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "当前：${sourceLabel(selectedSource)}",
                    color = AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = { onPreview(viewpoint) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SceneBlue.copy(alpha = 0.18f),
                    contentColor = TextPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("预览", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ElectronicMirrorCameraSource.entries.forEach { source ->
                FilterChip(
                    selected = selectedSource == source,
                    onClick = { onAssign(viewpoint, source) },
                    label = {
                        Text(
                            "${source.displayName.removePrefix("来源 ")} · 格${source.stripTileIndex + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentGreen.copy(alpha = 0.18f),
                        selectedLabelColor = TextPrimary,
                        containerColor = Color.Transparent,
                        labelColor = TextSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedSource == source,
                        borderColor = CardBorder,
                        selectedBorderColor = AccentGreen.copy(alpha = 0.6f),
                    ),
                )
            }
        }
    }
}

private fun sourceLabel(source: ElectronicMirrorCameraSource): String =
    "${source.displayName} · 全景母流第 ${source.stripTileIndex + 1} 格"
