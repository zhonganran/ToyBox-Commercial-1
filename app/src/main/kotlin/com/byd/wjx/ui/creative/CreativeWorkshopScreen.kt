package com.byd.wjx.ui.creative

import android.annotation.SuppressLint
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.LocalAppWallpaper
import com.byd.wjx.ui.theme.LocalSuperToyBoxDarkTheme
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import org.json.JSONObject
import java.util.Locale

private enum class WorkshopMode(val label: String) {
    Color("颜色"),
    Wrap("彩绘"),
    Window("车窗膜"),
}

@Composable
fun CreativeWorkshopScreen(
    onBack: () -> Unit,
    viewModel: CreativeWorkshopViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wallpaper = LocalAppWallpaper.current
    val darkTheme = LocalSuperToyBoxDarkTheme.current
    var mode by rememberSaveable { mutableStateOf(WorkshopMode.Color) }
    var viewer by remember { mutableStateOf<WebView?>(null) }
    val baseDensity = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val contentScale = (screenHeightDp / 860f).coerceIn(0.74f, 1f)
    val workshopDensity = remember(baseDensity.density, baseDensity.fontScale, contentScale) {
        Density(
            density = baseDensity.density * contentScale,
            fontScale = baseDensity.fontScale,
        )
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(state.paintColor, viewer) {
        viewer?.callWorkshop("setColor", state.paintColor)
    }
    LaunchedEffect(state.selectedWrapId, state.catalog, viewer) {
        val wrapUrl = state.catalog?.wraps
            ?.firstOrNull { it.id == state.selectedWrapId }
            ?.textureUrl
            .orEmpty()
        viewer?.callWorkshop("setWrap", wrapUrl)
    }
    LaunchedEffect(state.windowOpacity, viewer) {
        viewer?.evaluateJavascript(
            "window.workshop && window.workshop.setWindowOpacity(${state.windowOpacity})",
            null,
        )
    }

    CompositionLocalProvider(LocalDensity provides workshopDensity) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 1_100.dp
            val panelWidth = if (compact) 378.dp else 430.dp
            val horizontalPadding = if (compact) 22.dp else 34.dp

            Image(
                painter = painterResource(wallpaper.drawableRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF07151B).copy(alpha = if (darkTheme) 0.50f else 0.20f),
                                Color.Transparent,
                                Color(0xFF061116).copy(alpha = if (darkTheme) 0.68f else 0.42f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = 22.dp),
            ) {
                WorkshopTopBar(
                    vehicle = state.selectedVehicle,
                    wallpaperName = wallpaper.displayName,
                    onBack = onBack,
                    onSelectVehicle = viewModel::openVehicleSelector,
                )
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    VehicleStage(
                        state = state,
                        panelWidth = panelWidth,
                        onViewerReady = { viewer = it },
                        onViewerState = viewModel::onViewerState,
                        onRetryCatalog = viewModel::refreshCatalog,
                        onReloadViewer = { viewer?.reload() },
                        onResetView = { viewer?.callWorkshop("resetView") },
                        onRotate = { radians ->
                            viewer?.evaluateJavascript(
                                "window.workshop && window.workshop.rotate($radians)",
                                null,
                            )
                        },
                    )
                    WorkshopPanel(
                        state = state,
                        mode = mode,
                        panelWidth = panelWidth,
                        onModeChange = { mode = it },
                        onPaintSelected = viewModel::selectPaint,
                        onWrapSelected = viewModel::selectWrap,
                        onWindowOpacityChanged = viewModel::setWindowOpacity,
                        onResetDesign = viewModel::resetDesign,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }

        if (state.showVehicleSelector) {
            VehicleSelectorDialog(
                vehicles = state.catalog?.vehicles.orEmpty(),
                selectedVehicleId = state.selectedVehicle?.id,
                required = state.vehicleSelectionRequired,
                loading = state.loadingCatalog,
                error = state.catalogError,
                onRetry = viewModel::refreshCatalog,
                onSelect = viewModel::selectVehicle,
                onDismiss = viewModel::closeVehicleSelector,
            )
        }
    }
}

@Composable
private fun WorkshopTopBar(
    vehicle: CreativeVehicle?,
    wallpaperName: String,
    onBack: () -> Unit,
    onSelectVehicle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "返回设置",
            onClick = onBack,
        )
        Text("创意工坊", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Box(Modifier.size(9.dp).background(AccentGreen, CircleShape))
        Text("在线", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, fontWeight = FontWeight.Medium)

        Spacer(Modifier.weight(1f))
        GlassLabel(
            icon = Icons.Outlined.Image,
            text = wallpaperName,
        )
        OutlinedButton(
            onClick = onSelectVehicle,
            shape = RoundedCornerShape(7.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF142B32).copy(alpha = 0.46f),
                contentColor = Color.White,
            ),
            modifier = Modifier.height(46.dp),
        ) {
            Icon(Icons.Outlined.DirectionsCar, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                vehicle?.displayName ?: "选择我的车型",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GlassLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier
            .height(43.dp)
            .background(Color(0xFF142B32).copy(alpha = 0.42f), RoundedCornerShape(7.dp))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(7.dp))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(18.dp))
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VehicleStage(
    state: CreativeWorkshopUiState,
    panelWidth: androidx.compose.ui.unit.Dp,
    onViewerReady: (WebView?) -> Unit,
    onViewerState: (String) -> Unit,
    onRetryCatalog: () -> Unit,
    onReloadViewer: () -> Unit,
    onResetView: () -> Unit,
    onRotate: (Double) -> Unit,
) {
    val selectedPaint = CreativePaintOptions.firstOrNull { it.color == state.paintColor }
    val selectedWrap = state.catalog?.wraps?.firstOrNull { it.id == state.selectedWrapId }
    val designName = selectedWrap?.name ?: selectedPaint?.name ?: "自定义车漆"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = panelWidth + 22.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 20.dp)) {
            Text(
                state.selectedVehicle?.brand?.uppercase() ?: "MY VEHICLE",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                designName,
                color = Color.White,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                state.selectedVehicle?.name.orEmpty(),
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        val catalog = state.catalog
        val vehicle = state.selectedVehicle
        if (catalog != null && vehicle != null) {
            VehicleWebView(
                viewerUrl = catalog.viewerUrl,
                vehicle = vehicle,
                paintColor = state.paintColor,
                wrapUrl = catalog.wraps.firstOrNull { it.id == state.selectedWrapId }?.textureUrl.orEmpty(),
                windowOpacity = state.windowOpacity,
                onViewerReady = onViewerReady,
                onViewerState = onViewerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .align(Alignment.BottomCenter),
            )
        }

        when {
            state.loadingCatalog -> Box(Modifier.align(Alignment.Center)) {
                WorkshopStatus("正在读取在线车型", loading = true)
            }
            state.catalogError != null -> Box(Modifier.align(Alignment.Center)) {
                WorkshopError(state.catalogError, onRetryCatalog)
            }
            vehicle == null -> Box(Modifier.align(Alignment.Center)) {
                WorkshopStatus("请选择我的车型", loading = false)
            }
            state.viewerLoading -> Box(Modifier.align(Alignment.Center)) {
                WorkshopStatus("正在加载 ${vehicle.displayName}", loading = true)
            }
            state.viewerError != null -> Box(Modifier.align(Alignment.Center)) {
                WorkshopError(state.viewerError, onReloadViewer)
            }
        }

        if (vehicle != null) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Outlined.Rotate90DegreesCcw, "向左旋转", onClick = { onRotate(-0.42) })
                GlassIconButton(Icons.Outlined.Refresh, "恢复视角", onClick = onResetView)
                GlassIconButton(Icons.Outlined.Rotate90DegreesCcw, "向右旋转", onClick = { onRotate(0.42) }, mirror = true)
            }
        }
    }
}

@Composable
private fun WorkshopStatus(text: String, loading: Boolean) {
    Row(
        modifier = Modifier
            .background(Color(0xFF102126).copy(alpha = 0.72f), RoundedCornerShape(7.dp))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(7.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WorkshopError(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .background(Color(0xFF2B1719).copy(alpha = 0.80f), RoundedCornerShape(7.dp))
            .border(1.dp, Color(0xFFFFA5A5).copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFFFB3B3), modifier = Modifier.size(20.dp))
        Text(message, color = Color.White, fontSize = 13.sp, modifier = Modifier.widthIn(max = 330.dp))
        TextButton(onClick = onRetry) { Text("重试", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun WorkshopPanel(
    state: CreativeWorkshopUiState,
    mode: WorkshopMode,
    panelWidth: androidx.compose.ui.unit.Dp,
    onModeChange: (WorkshopMode) -> Unit,
    onPaintSelected: (CreativePaintOption) -> Unit,
    onWrapSelected: (CreativeWrap?) -> Unit,
    onWindowOpacityChanged: (Float) -> Unit,
    onResetDesign: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(panelWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF25292C).copy(alpha = 0.91f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("车辆外观", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("${state.catalog?.vehicles?.size ?: 0} 款在线车型", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp)
            }
            IconButton(onClick = onResetDesign) {
                Icon(Icons.Outlined.Refresh, contentDescription = "恢复默认外观", tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(48.dp)
                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(4.dp),
        ) {
            WorkshopMode.entries.forEach { item ->
                val selected = item == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (selected) Color(0xFF686C70) else Color.Transparent,
                            RoundedCornerShape(5.dp),
                        )
                        .clickable { onModeChange(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item.label,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.58f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        when (mode) {
            WorkshopMode.Color -> PaintPicker(state.paintColor, onPaintSelected)
            WorkshopMode.Wrap -> WrapPicker(state, onWrapSelected)
            WorkshopMode.Window -> WindowFilmPicker(state.windowOpacity, onWindowOpacityChanged)
        }
    }
}

@Composable
private fun PaintPicker(
    selectedColor: String,
    onSelect: (CreativePaintOption) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        SectionHeading(Icons.Outlined.Palette, "车漆颜色", CreativePaintOptions.firstOrNull { it.color == selectedColor }?.name ?: "自定义")
        CreativePaintOptions.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    PaintSwatch(
                        option = option,
                        selected = option.color.equals(selectedColor, ignoreCase = true),
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaintSwatch(
    option: CreativePaintOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.20f))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) AccentBlue else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .background(colorFromHex(option.color), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (option.id == "pearl") Color.Black else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(option.name, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun WrapPicker(
    state: CreativeWorkshopUiState,
    onSelect: (CreativeWrap?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        SectionHeading(Icons.Outlined.Image, "彩绘车衣", state.catalog?.wraps?.firstOrNull { it.id == state.selectedWrapId }?.name ?: "未使用")
        WrapOption(
            name = "不使用彩绘",
            previewColor = Color(0xFF596368),
            selected = state.selectedWrapId == null,
            onClick = { onSelect(null) },
        )
        state.catalog?.wraps.orEmpty().forEach { wrap ->
            WrapOption(
                name = wrap.name,
                previewColor = colorFromHex(wrap.previewColor),
                selected = state.selectedWrapId == wrap.id,
                onClick = { onSelect(wrap) },
            )
        }
    }
}

@Composable
private fun WrapOption(
    name: String,
    previewColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.20f))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) AccentBlue else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .height(46.dp)
                .background(
                    Brush.linearGradient(
                        listOf(previewColor.copy(alpha = 0.65f), previewColor, Color.White.copy(alpha = 0.35f)),
                    ),
                    RoundedCornerShape(5.dp),
                ),
        )
        Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun WindowFilmPicker(
    opacity: Float,
    onOpacityChanged: (Float) -> Unit,
) {
    val darkness = ((1f - opacity) * 100).toInt()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SectionHeading(Icons.Outlined.Tune, "车窗膜", "深色 $darkness%")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(122.dp)
                .background(Color(0xFF0B1114).copy(alpha = 1f - opacity * 0.55f), RoundedCornerShape(6.dp))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp)),
        )
        Slider(
            value = opacity,
            onValueChange = onOpacityChanged,
            valueRange = 0.12f..0.92f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("深色", color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp)
            Text("浅色", color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionHeading(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selection: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(9.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Text(selection, color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    mirror: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .background(Color(0xFF142B32).copy(alpha = 0.52f), RoundedCornerShape(7.dp))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(7.dp)),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer(scaleX = if (mirror) -1f else 1f),
        )
    }
}

@Composable
private fun VehicleSelectorDialog(
    vehicles: List<CreativeVehicle>,
    selectedVehicleId: String?,
    required: Boolean,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelect: (CreativeVehicle) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(vehicles, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) vehicles else vehicles.filter { vehicle ->
            vehicle.displayName.contains(normalized, ignoreCase = true) ||
                vehicle.aliases.any { it.contains(normalized, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = { if (!required) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !required,
            dismissOnClickOutside = !required,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 620.dp, max = 760.dp)
                .heightIn(max = 610.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF202528))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("我的车型", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("选择后会保存到本机", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp)
                }
                if (!required) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("搜索车型") },
            )
            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error, color = Color(0xFFFFB0B0), fontSize = 14.sp)
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                        Text("重新加载")
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(filtered, key = { it.id }) { vehicle ->
                        val selected = vehicle.id == selectedVehicleId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .background(
                                    if (selected) AccentBlue.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.035f),
                                    RoundedCornerShape(6.dp),
                                )
                                .border(
                                    if (selected) 1.5.dp else 1.dp,
                                    if (selected) AccentBlue else Color.White.copy(alpha = 0.10f),
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable { onSelect(vehicle) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(AccentBlue.copy(alpha = 0.14f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(vehicle.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(vehicle.brand, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp)
                            }
                            Text(formatModelSize(vehicle.size), color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp)
                            if (selected) {
                                Spacer(Modifier.width(10.dp))
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VehicleWebView(
    viewerUrl: String,
    vehicle: CreativeVehicle,
    paintColor: String,
    wrapUrl: String,
    windowOpacity: Float,
    onViewerReady: (WebView?) -> Unit,
    onViewerState: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestStateCallback by rememberUpdatedState(onViewerState)
    val bridge: WorkshopJavascriptBridge = remember {
        WorkshopJavascriptBridge { payload -> latestStateCallback(payload) }
    }
    val pageUrl = remember(viewerUrl, vehicle.id) {
        Uri.parse(viewerUrl).buildUpon()
            .appendQueryParameter("model", vehicle.modelUrl)
            .appendQueryParameter("color", paintColor)
            .appendQueryParameter("wrap", wrapUrl)
            .appendQueryParameter("window", windowOpacity.toString())
            .build()
            .toString()
    }
    val allowedHost = remember(viewerUrl) { Uri.parse(viewerUrl).host.orEmpty() }
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.databaseEnabled = false
                settings.mediaPlaybackRequiresUserGesture = true
                settings.setSupportMultipleWindows(false)
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val target = request?.url ?: return true
                        return target.scheme != "https" || target.host != allowedHost
                    }
                }
                addWorkshopJavascriptInterface(bridge)
                loadUrl(pageUrl)
                webView = this
                onViewerReady(this)
            }
        },
        update = { view ->
            if (view.url != pageUrl) {
                view.loadUrl(pageUrl)
            }
            webView = view
            onViewerReady(view)
        },
        modifier = modifier,
    )

    DisposableEffect(Unit) {
        onDispose {
            onViewerReady(null)
            webView?.removeJavascriptInterface("WorkshopBridge")
            webView?.stopLoading()
            webView?.destroy()
        }
    }
}

private class WorkshopJavascriptBridge(
    private val callback: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onState(payload: String) {
        mainHandler.post { callback(payload) }
    }
}

private fun WebView.addWorkshopJavascriptInterface(bridge: WorkshopJavascriptBridge) {
    addJavascriptInterface(bridge, "WorkshopBridge")
}

private fun WebView.callWorkshop(method: String, value: String? = null) {
    val argument = value?.let(JSONObject::quote).orEmpty()
    val call = if (value == null) {
        "window.workshop && window.workshop.$method()"
    } else {
        "window.workshop && window.workshop.$method($argument)"
    }
    evaluateJavascript(call, null)
}

private fun colorFromHex(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF78909C))

private fun formatModelSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
