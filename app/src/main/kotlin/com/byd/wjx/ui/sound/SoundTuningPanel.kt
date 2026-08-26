package com.byd.wjx.ui.sound

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byd.wjx.audio.ExteriorHailingSettings
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.SceneViolet
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun AudioTuningSidePanel(
    playbackVolumePercent: Int,
    realtimeVolumePercent: Int,
    realtimeNoiseSuppressionPercent: Int,
    realtimeMicGainPercent: Int,
    onDismiss: () -> Unit,
    onPlaybackVolumeChanged: (Int) -> Unit,
    onRealtimeVolumeChanged: (Int) -> Unit,
    onNoiseSuppressionChanged: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        val panelInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                    .widthIn(min = 390.dp, max = 470.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardSurfaceElevated)
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SceneBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = SceneBlue,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("声音调节", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        Text("音效播放与实时喊话", color = TextSecondary, fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = TextPrimary)
                    }
                }

                SoundTuningSlider(
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    label = "音效播放音量",
                    value = playbackVolumePercent,
                    min = ExteriorHailingSettings.MIN_VOLUME_PERCENT,
                    max = ExteriorHailingSettings.MAX_PLAYBACK_VOLUME_PERCENT,
                    color = SceneBlue,
                    onValueChanged = onPlaybackVolumeChanged,
                )
                SoundTuningSlider(
                    icon = Icons.Outlined.Mic,
                    label = "实时喊话音量",
                    value = realtimeVolumePercent,
                    min = ExteriorHailingSettings.MIN_VOLUME_PERCENT,
                    max = ExteriorHailingSettings.MAX_REALTIME_VOLUME_PERCENT,
                    color = ScenePink,
                    onValueChanged = onRealtimeVolumeChanged,
                )
                SoundTuningSlider(
                    icon = Icons.Outlined.GraphicEq,
                    label = "实时喊话降噪",
                    value = realtimeNoiseSuppressionPercent,
                    min = ExteriorHailingSettings.MIN_NOISE_SUPPRESSION_PERCENT,
                    max = ExteriorHailingSettings.MAX_NOISE_SUPPRESSION_PERCENT,
                    color = AccentGreen,
                    onValueChanged = onNoiseSuppressionChanged,
                )
                AutoMicSensitivityRow(value = realtimeMicGainPercent)

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardSurface,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Text("恢复默认", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
@Composable
private fun SoundTuningSlider(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Int,
    min: Int,
    max: Int,
    color: Color,
    onValueChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
            Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$value%", color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / 2 - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun AutoMicSensitivityRow(value: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = SceneViolet, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text("麦克风灵敏度", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("自动 $value%", color = SceneViolet, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(SceneViolet.copy(alpha = 0.16f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value.coerceIn(0, 100) / 100f))
                    .fillMaxHeight()
                    .background(SceneViolet, RoundedCornerShape(999.dp)),
            )
        }
    }
}
