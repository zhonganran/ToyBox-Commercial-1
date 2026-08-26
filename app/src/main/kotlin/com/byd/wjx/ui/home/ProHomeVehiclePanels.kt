package com.byd.wjx.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.ui.dashboard.DashboardUiState
import kotlin.math.roundToInt

@Composable
internal fun BatteryHealthPanel(
    state: DashboardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xE20E9C8E), Color(0xE21F7E77)),
        title = "电池健康",
        icon = Icons.Outlined.HealthAndSafety,
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(0.92f)) {
                Text("SoH", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.currentSoh?.roundToInt()?.toString() ?: "--",
                        color = Color.White,
                        fontSize = 46.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        "%",
                        color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    batteryHint(state),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    maxLines = 2,
                )
            }
            Column(
                modifier = Modifier.weight(1.28f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                WhiteMetric("电池均温", tempText(state.avgBatTemp))
                WhiteMetric("最高/最低", maxMinTempText(state))
                WhiteMetric("电芯压差", formatCellVoltageDelta(state.cellVoltageDelta))
                WhiteMetric("12V 电池", state.voltage12v?.let { "%.1f V".format(it) } ?: "--")
                WhiteMetric("充电枪", chargeGunText(state))
            }
        }
    }
}

@Composable
internal fun VehicleEnvironmentPanel(
    state: DashboardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDarkPanel(
        modifier = modifier,
        colors = listOf(Color(0xDF168FBD), Color(0xDF245AA8)),
        title = "车身环境",
        icon = Icons.Outlined.DirectionsCar,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                EnvironmentItem(Icons.Outlined.AcUnit, "空调", acMainText(state), state.acStatus == 1, Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.Air, "风量", fanText(state), (state.fanLevel ?: 0) > 0, Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.DirectionsCar, "内循环", circText(state.acCirc), state.acCirc == 1, Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.LightMode, "灯光", lightMainText(state), lightsActive(state), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                EnvironmentItem(Icons.Outlined.Lock, "车锁", lockSummary(state), lockSummary(state) == "已锁", Modifier.weight(1f))
                EnvironmentItem(Icons.Outlined.DirectionsCar, "车门", doorSummary(state), doorSummary(state) == "全关", Modifier.weight(1f))
                EnvironmentItem(
                    Icons.Outlined.EvStation,
                    "充电口",
                    openCloseText(state.chargingCapCover),
                    BydSignalSemantics.isCoverOpen(state.chargingCapCover),
                    Modifier.weight(1f),
                )
                EnvironmentItem(
                    Icons.Outlined.LocalGasStation,
                    "油箱盖",
                    openCloseText(state.fuelCap),
                    BydSignalSemantics.isCoverOpen(state.fuelCap),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WhiteMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun EnvironmentItem(
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(26.dp),
        )
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
