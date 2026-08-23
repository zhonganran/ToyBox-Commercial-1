package com.byd.wjx.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.byd.wjx.phonecast.PhoneCastActivity
import com.byd.wjx.phonecast.PhoneCastPreferences
import com.byd.wjx.phonecast.PhoneCastScaleMode
import com.byd.wjx.phonecast.PhoneCastUrl
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
internal fun PhoneCastSettingsCard() {
    val context = LocalContext.current
    val preferences = remember(context) { PhoneCastPreferences(context) }
    val initial = remember(preferences) { preferences.snapshot() }
    var url by remember { mutableStateOf(initial.url) }
    var scaleMode by remember { mutableStateOf(initial.scaleMode) }
    val normalizedUrl = remember(url) { PhoneCastUrl.normalize(url) }

    SectionHeader(text = "手机投屏")
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.CastConnected, contentDescription = null, tint = AccentBlue)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Android 手机投屏", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("RTSP / H.264 全屏接收", color = TextSecondary, fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("RTSP 地址") },
                placeholder = { Text("rtsp://192.168.43.1:8086/live") },
                leadingIcon = { Icon(Icons.Outlined.CastConnected, contentDescription = null) },
                singleLine = true,
                isError = url.isNotBlank() && normalizedUrl == null,
                supportingText = if (url.isNotBlank() && normalizedUrl == null) {
                    { Text("请输入有效的 RTSP 地址") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("显示比例", color = TextPrimary, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhoneCastScaleMode.entries.forEach { mode ->
                        UnitChip(
                            label = mode.label,
                            selected = scaleMode == mode,
                            onClick = {
                                scaleMode = mode
                                preferences.setScaleMode(mode)
                            },
                        )
                    }
                }
                Text(
                    when (scaleMode) {
                        PhoneCastScaleMode.FIT -> "完整显示手机画面，比例不一致时保留黑边"
                        PhoneCastScaleMode.CROP -> "保持比例铺满车机，超出部分会被裁剪"
                        PhoneCastScaleMode.STRETCH -> "强制铺满整个车机屏幕，画面可能变形"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }

            Button(
                onClick = {
                    val target = normalizedUrl
                    if (target == null) {
                        Toast.makeText(context, "请先填写手机端显示的 RTSP 地址", Toast.LENGTH_SHORT).show()
                    } else {
                        url = target
                        preferences.save(target, scaleMode)
                        PhoneCastActivity.start(context, target, scaleMode)
                    }
                },
                enabled = normalizedUrl != null,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Outlined.OpenInFull, contentDescription = null)
                Text("开始全屏投屏", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
