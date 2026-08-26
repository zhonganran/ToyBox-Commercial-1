package com.byd.wjx.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TireRepair
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.data.nativestack.BydSignalSemantics
import com.byd.wjx.ui.dashboard.DashboardViewModel
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.SceneAmber
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneMint
import com.byd.wjx.ui.theme.SocYellow
import com.byd.wjx.ui.theme.TextPrimary
import java.util.Locale

@Composable
fun BatteryHealthScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statusColor = batteryStatusColor(state)
    val metrics = listOf(
        HomeDetailMetric("当前电量", state.soc?.let { "$it%" } ?: "--", AccentGreen),
        HomeDetailMetric("可用电量", state.batteryRemainingKwh?.let { "%.1f kWh".format(Locale.CHINA, it) } ?: "--", SceneMint),
        HomeDetailMetric("电池均温", tempText(state.avgBatTemp), statusColor),
        HomeDetailMetric("最高 / 最低温", maxMinTempText(state), statusColor),
        HomeDetailMetric("电芯压差", formatCellVoltageDelta(state.cellVoltageDelta), statusColor),
        HomeDetailMetric("最低电芯电压", state.cellVoltageMin?.let { "%.3f V".format(Locale.CHINA, it) } ?: "--", AccentBlue),
        HomeDetailMetric("最高电芯电压", state.cellVoltageMax?.let { "%.3f V".format(Locale.CHINA, it) } ?: "--", AccentBlue),
        HomeDetailMetric("12V 电池", state.voltage12v?.let { "%.1f V".format(Locale.CHINA, it) } ?: "--", voltage12vColor(state)),
        HomeDetailMetric("充电枪", chargeGunText(state), if (state.isCharging) AccentGreen else TextPrimary),
        HomeDetailMetric("累计行驶里程", state.currentLifetimeKm?.let { "%,.0f km".format(Locale.CHINA, it) } ?: "--", SceneBlue),
        HomeDetailMetric("累计用电", state.currentLifetimeKwh?.let { "%,.1f kWh".format(Locale.CHINA, it) } ?: "--", SceneAmber),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 30.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeDetailHeader(
                eyebrow = "BATTERY HEALTH",
                title = "电池健康",
                subtitle = "只显示动力电池、电芯、温度与低压电池数据",
                connected = state.vehicleDataConnected,
                onBack = onBack,
            )
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BatteryHealthSummary(state, statusColor, Modifier.weight(0.72f))
                    HomeDetailMetricGrid(metrics, columns = 3, modifier = Modifier.weight(1.6f))
                }
            } else {
                BatteryHealthSummary(state, statusColor, Modifier.fillMaxWidth())
                HomeDetailMetricGrid(metrics, columns = 2, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
fun VehicleEnvironmentScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setHomeTelemetryActive(true)
        onDispose { viewModel.setHomeTelemetryActive(false) }
    }

    val driving = listOf(
        HomeDetailMetric("挡位", gearText(state.gear), SceneBlue),
        HomeDetailMetric("车速", state.speed?.let { "$it km/h" } ?: "--", AccentBlue),
        HomeDetailMetric("驾驶模式", driveModeText(state.driveMode), AccentGreen),
        HomeDetailMetric("动力模式", workModeText(state.workMode), SceneAmber),
        HomeDetailMetric("自动泊车", apaStatusText(state.adasApaParkingStatus), TextPrimary),
        HomeDetailMetric("ACC", accStatusText(state), TextPrimary),
    )
    val cabin = listOf(
        HomeDetailMetric("空调", acMainText(state), if (state.acStatus == 1) AccentGreen else TextPrimary),
        HomeDetailMetric("风量", fanText(state), AccentBlue),
        HomeDetailMetric("循环模式", circText(state.acCirc), AccentBlue),
        HomeDetailMetric("车内温度", tempText(state.insideTemp), SceneMint),
        HomeDetailMetric("车外温度", tempText(state.exteriorTemp), SceneAmber),
        HomeDetailMetric("灯光", lightMainText(state), if (lightsActive(state)) SceneAmber else TextPrimary),
    )
    val body = listOf(
        HomeDetailMetric("左前门", openStateText(state.doorFL), doorColor(state.doorFL)),
        HomeDetailMetric("右前门", openStateText(state.doorFR), doorColor(state.doorFR)),
        HomeDetailMetric("左后门", openStateText(state.doorRL), doorColor(state.doorRL)),
        HomeDetailMetric("右后门", openStateText(state.doorRR), doorColor(state.doorRR)),
        HomeDetailMetric("后备箱", trunkStateText(state.trunk), if (BydSignalSemantics.isTrunkNotClosed(state.trunk)) SocYellow else AccentGreen),
        HomeDetailMetric("前舱盖", openStateText(state.hood), doorColor(state.hood)),
        HomeDetailMetric("天窗", openStateText(state.sunroof), doorColor(state.sunroof)),
        HomeDetailMetric("车锁", lockSummary(state), if (lockSummary(state) == "已锁") AccentGreen else SocYellow),
        HomeDetailMetric("左前窗", openStateText(state.windowFL), doorColor(state.windowFL)),
        HomeDetailMetric("右前窗", openStateText(state.windowFR), doorColor(state.windowFR)),
        HomeDetailMetric("左后窗", openStateText(state.windowRL), doorColor(state.windowRL)),
        HomeDetailMetric("右后窗", openStateText(state.windowRR), doorColor(state.windowRR)),
        HomeDetailMetric("充电口", openCloseText(state.chargingCapCover), TextPrimary),
        HomeDetailMetric("油箱盖", openCloseText(state.fuelCap), TextPrimary),
    )
    val tires = listOf(
        tireMetric("左前轮", state.tirePressFL, state.tireTempFL, state.tireStateFL),
        tireMetric("右前轮", state.tirePressFR, state.tireTempFR, state.tireStateFR),
        tireMetric("左后轮", state.tirePressRL, state.tireTempRL, state.tireStateRL),
        tireMetric("右后轮", state.tirePressRR, state.tireTempRR, state.tireStateRR),
    )
    val lightsAndSafety = listOf(
        HomeDetailMetric("近光灯", onOffText(state.lightLow), lightColor(state.lightLow)),
        HomeDetailMetric("远光灯", onOffText(state.lightHigh), lightColor(state.lightHigh)),
        HomeDetailMetric("日行灯", onOffText(state.drl), lightColor(state.drl)),
        HomeDetailMetric("双闪", onOffText(state.hazardLights), lightColor(state.hazardLights)),
        HomeDetailMetric("转向灯", turnSignalText(state.turnSignalFlash), SceneAmber),
        HomeDetailMetric("主驾安全带", seatbeltText(state.seatbeltFL), safetyColor(state.seatbeltFL)),
        HomeDetailMetric("副驾安全带", seatbeltText(state.seatbeltFR), safetyColor(state.seatbeltFR)),
        HomeDetailMetric("车道保持", adasStateText(state.laneKeepState), AccentBlue),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        val columns = if (wide) 4 else 2
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 30.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeDetailHeader(
                eyebrow = "VEHICLE ENVIRONMENT",
                title = "车身环境",
                subtitle = "车门、车窗、空调、轮胎、灯光和驾驶辅助实时状态",
                connected = state.vehicleDataConnected,
                onBack = onBack,
            )
            HomeDetailSection("行驶状态", Icons.Outlined.Speed, driving, columns)
            HomeDetailSection("座舱与空调", Icons.Outlined.AcUnit, cabin, columns)
            HomeDetailSection("车门、车窗与车锁", Icons.Outlined.Lock, body, columns)
            HomeDetailSection("轮胎", Icons.Outlined.TireRepair, tires, columns)
            HomeDetailSection("灯光与安全辅助", Icons.Outlined.LightMode, lightsAndSafety, columns)
            Spacer(Modifier.height(2.dp))
        }
    }
}
