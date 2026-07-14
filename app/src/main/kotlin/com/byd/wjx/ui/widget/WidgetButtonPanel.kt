package com.byd.wjx.ui.widget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated

object WidgetButtonLayout {
    const val PANEL_WIDTH_DP = 260
    const val PANEL_HEIGHT_DP = 108
    const val BUTTON_DP = 50
    const val GAP_DP = 12

    data class WindowBox(val x: Int, val y: Int, val width: Int, val height: Int)

    fun pocketPx(buttonPx: Int, gapPx: Int): Int = buttonPx + gapPx

    fun expandedWindow(
        collapsedX: Int,
        collapsedY: Int,
        panelWpx: Int,
        panelHpx: Int,
        buttonPx: Int,
        gapPx: Int,
        screenW: Int,
        screenH: Int,
    ): WindowBox {
        val height = panelHpx + pocketPx(buttonPx, gapPx)
        val (x, y) = DragGestureLogic.clampToScreen(
            x = collapsedX,
            y = collapsedY,
            widgetWidth = panelWpx,
            widgetHeight = height,
            screenWidth = screenW,
            screenHeight = screenH,
        )
        return WindowBox(x = x, y = y, width = panelWpx, height = height)
    }
}

@Composable
fun WidgetButtonPanel(
    expanded: Boolean,
    scaleFactor: Float,
    onButtonClick: (Int) -> Unit,
) {
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density * scaleFactor,
        fontScale = baseDensity.fontScale,
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        val button = WidgetButtonLayout.BUTTON_DP.dp
        val gap = WidgetButtonLayout.GAP_DP.dp
        val panelW = WidgetButtonLayout.PANEL_WIDTH_DP.dp
        val panelH = WidgetButtonLayout.PANEL_HEIGHT_DP.dp

        Box(modifier = Modifier.size(width = panelW, height = panelH + button + gap)) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(panelW)
                    .height(button),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ButtonCell(1, expanded, 0, onButtonClick)
                ButtonCell(2, expanded, 1, onButtonClick)
                ButtonCell(3, expanded, 2, onButtonClick)
                ButtonCell(4, expanded, 3, onButtonClick)
            }
        }
    }
}

@Composable
private fun ButtonCell(
    number: Int,
    expanded: Boolean,
    staggerIndex: Int,
    onClick: (Int) -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, delayMillis = staggerIndex * 40),
        label = "widgetButton$number",
    )
    val dy = ((1f - progress) * WidgetButtonLayout.BUTTON_DP).dp

    Box(
        modifier = Modifier
            .size(WidgetButtonLayout.BUTTON_DP.dp)
            .offset(y = -dy)
            .alpha(progress)
            .background(CardSurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.5.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = expanded) { onClick(number) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = AccentGreen,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
