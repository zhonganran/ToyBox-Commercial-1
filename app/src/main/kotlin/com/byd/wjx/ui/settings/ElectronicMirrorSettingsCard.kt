package com.byd.wjx.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.byd.wjx.cluster.ClusterEntryPoint
import com.byd.wjx.electronicmirror.ElectronicMirrorPositionOverlay
import com.byd.wjx.electronicmirror.ElectronicMirrorPreferences
import com.byd.wjx.ui.components.bydSwitchColors
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ElectronicMirrorSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.getSharedPreferences(ElectronicMirrorPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, ClusterEntryPoint::class.java)
    }
    var enabled by remember {
        mutableStateOf(prefs.getBoolean(ElectronicMirrorPreferences.KEY_ENABLED, false))
    }
    var thresholdKmh by remember {
        mutableIntStateOf(
            prefs.getInt(
                ElectronicMirrorPreferences.KEY_THRESHOLD_KMH,
                ElectronicMirrorPreferences.DEFAULT_THRESHOLD_KMH,
            ),
        )
    }
    var pipWidthPct by remember {
        mutableIntStateOf(
            prefs.getInt(
                ElectronicMirrorPreferences.KEY_PIP_WIDTH_PCT,
                ElectronicMirrorPreferences.DEFAULT_PIP_WIDTH_PCT,
            ).coerceIn(
                ElectronicMirrorPreferences.MIN_PIP_WIDTH_PCT,
                ElectronicMirrorPreferences.MAX_PIP_WIDTH_PCT,
            ),
        )
    }
    var bsdGlow by remember {
        mutableStateOf(prefs.getBoolean(ElectronicMirrorPreferences.KEY_BSD_GLOW, true))
    }
    var placing by remember { mutableStateOf(false) }
    val positionOverlay = remember {
        ElectronicMirrorPositionOverlay().apply { onHidden = { placing = false } }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) positionOverlay.hide()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            positionOverlay.hide()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            enabled = false
            prefs.edit().putBoolean(ElectronicMirrorPreferences.KEY_ENABLED, false).apply()
            Toast.makeText(context, "未授予摄像头权限，电子后视镜无法开启", Toast.LENGTH_SHORT).show()
        }
    }

    fun prepareOverlay(afterGrant: () -> Unit = {}) {
        scope.launch {
            val helperReady = entryPoint.helperBootstrap().ensureCurrent()
            val granted = helperReady && entryPoint.helperClient().grantOverlayPermission()
            if (granted) {
                repeat(5) {
                    if (Settings.canDrawOverlays(context)) return@repeat
                    delay(150L)
                }
            }
            if (Settings.canDrawOverlays(context)) {
                afterGrant()
            } else {
                Toast.makeText(context, "悬浮窗权限未生效，电子后视镜暂时无法显示", Toast.LENGTH_SHORT).show()
            }
        }
    }

    SectionHeader(text = "电子后视镜")
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
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = AccentGreen)
                Column(modifier = Modifier.weight(1f)) {
                    Text("电子后视镜", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "左转显示左侧摄像头，右转显示右侧摄像头",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { requested ->
                        enabled = requested
                        prefs.edit().putBoolean(ElectronicMirrorPreferences.KEY_ENABLED, requested).apply()
                        if (requested) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            prepareOverlay()
                        } else {
                            positionOverlay.hide()
                        }
                    },
                    colors = bydSwitchColors(),
                )
            }

            ElectronicMirrorSlider(
                label = "触发速度",
                value = thresholdKmh,
                suffix = "km/h",
                enabled = enabled,
                range = ElectronicMirrorPreferences.MIN_THRESHOLD_KMH..
                    ElectronicMirrorPreferences.MAX_THRESHOLD_KMH,
                onChange = { thresholdKmh = it },
                onFinished = {
                    prefs.edit()
                        .putInt(ElectronicMirrorPreferences.KEY_THRESHOLD_KMH, thresholdKmh)
                        .apply()
                },
            )

            ElectronicMirrorSlider(
                label = "中控画面宽度",
                value = pipWidthPct,
                suffix = "%",
                enabled = enabled,
                range = ElectronicMirrorPreferences.MIN_PIP_WIDTH_PCT..
                    ElectronicMirrorPreferences.MAX_PIP_WIDTH_PCT,
                onChange = {
                    pipWidthPct = it
                    prefs.edit()
                        .putInt(ElectronicMirrorPreferences.KEY_PIP_WIDTH_PCT, pipWidthPct)
                        .apply()
                    if (placing) positionOverlay.refreshSize()
                },
                onFinished = {
                    prefs.edit()
                        .putInt(ElectronicMirrorPreferences.KEY_PIP_WIDTH_PCT, pipWidthPct)
                        .apply()
                    if (placing) positionOverlay.refreshSize()
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("中控画面位置", color = TextPrimary, fontSize = 14.sp)
                    Text("调整右转画面的位置；无仪表投射屏时左转画面会镜像显示", color = TextSecondary, fontSize = 12.sp)
                }
                Button(
                    enabled = enabled,
                    onClick = {
                        if (positionOverlay.isShown) {
                            positionOverlay.hide()
                        } else {
                            prepareOverlay {
                                placing = positionOverlay.show(context)
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                ) {
                    Icon(Icons.Outlined.OpenWith, contentDescription = null)
                    Text(if (placing) "完成" else "调整位置", modifier = Modifier.padding(start = 6.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("盲区有车时高亮", color = TextPrimary, fontSize = 14.sp)
                    Text("检测到侧后方车辆时显示橙色边框", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = bsdGlow,
                    enabled = enabled,
                    onCheckedChange = {
                        bsdGlow = it
                        prefs.edit().putBoolean(ElectronicMirrorPreferences.KEY_BSD_GLOW, it).apply()
                    },
                    colors = bydSwitchColors(),
                )
            }

            Text(
                "需要车辆配备全景影像。左转显示在仪表，右转显示在中控；没有仪表投射屏时，左转画面会回退到中控。",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ElectronicMirrorSlider(
    label: String,
    value: Int,
    suffix: String,
    enabled: Boolean,
    range: IntRange,
    onChange: (Int) -> Unit,
    onFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = TextPrimary, fontSize = 14.sp)
            Text("$value $suffix", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onFinished,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / 5 - 1).coerceAtLeast(0),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
            ),
        )
    }
}
