package com.byd.wjx.ui.sound

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCoral
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun SoundSceneHeader(
    hailingRunning: Boolean,
    hailingBusy: Boolean,
    onToggleHailing: () -> Unit,
    onOpenTuning: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(24.dp, 40.dp, 58.dp, 36.dp, 20.dp).forEachIndexed { index, barHeight ->
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    listOf(SceneViolet, SceneBlue, SceneCyan, ScenePink, SceneCoral)[index],
                                ),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("个性音效", color = TextPrimary, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
                    Text("让每一次出发都有自己的声音", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = "我的音效",
                color = TextPrimary.copy(alpha = 0.82f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            RealtimeHailingIconButton(
                running = hailingRunning,
                busy = hailingBusy,
                onToggle = onToggleHailing,
            )
            IconButton(
                onClick = onOpenTuning,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CardSurface.copy(alpha = 0.68f)),
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = "声音调节",
                    tint = TextPrimary,
                    modifier = Modifier.size(21.dp),
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CardSurface.copy(alpha = 0.68f)),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = TextPrimary, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun RealtimeHailingIconButton(
    running: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
) {
    Box(modifier = Modifier.size(46.dp)) {
        IconButton(
            onClick = onToggle,
            enabled = !busy,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (running) Color(0xFFE85D76) else Color.White.copy(alpha = 0.36f))
                .border(
                    width = if (running) 2.dp else 1.dp,
                    color = if (running) ScenePink else Color.White.copy(alpha = 0.64f),
                    shape = CircleShape,
                ),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = if (running) Color.White else TextPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(21.dp),
                )
            } else {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = if (running) "停止实时喊话" else "开始实时喊话",
                    tint = if (running) Color.White else TextPrimary,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        if (running) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF314F))
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

@Composable
internal fun MessageBar(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(message, color = TextSecondary, fontSize = 13.sp)
    }
}
