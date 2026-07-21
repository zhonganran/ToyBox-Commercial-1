package com.byd.wjx.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.wjx.ui.dashboard.DashboardViewModel

@Composable
fun ProHomeScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenCharges: () -> Unit,
    onOpenEnergyRecovery: () -> Unit,
    onOpenVehicleSystem: () -> Unit,
    onOpenBatteryHealth: () -> Unit,
    onOpenVehicleEnvironment: () -> Unit,
    onOpenTripMap: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setHomeTelemetryActive(true)
        viewModel.setPowertrainTelemetryActive(true)
        viewModel.setSystemMetricsActive(true)
        onDispose {
            viewModel.setHomeTelemetryActive(false)
            viewModel.setPowertrainTelemetryActive(false)
            viewModel.setSystemMetricsActive(false)
        }
    }
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    var imageVersion by remember { mutableIntStateOf(0) }
    val customVehicleImage = remember(appContext, imageVersion) { loadVehicleImageBitmap(appContext) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (saveVehicleImage(appContext, uri)) {
            imageVersion += 1
            Toast.makeText(appContext, "车辆图片已更新", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(appContext, "车辆图片保存失败", Toast.LENGTH_SHORT).show()
        }
    }
    fun deleteCustomVehicleImage() {
        if (deleteVehicleImage(appContext)) {
            imageVersion += 1
            Toast.makeText(appContext, "车辆图片已删除", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(appContext, "暂无自定义车辆图片", Toast.LENGTH_SHORT).show()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1080.dp
        val compact = maxWidth < 760.dp
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = if (compact) 14.dp else 46.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (wide) {
                HomeHero(
                    state = state,
                    image = customVehicleImage,
                    onPickImage = { imagePicker.launch("image/*") },
                    onDeleteImage = ::deleteCustomVehicleImage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(322.dp),
                )
                HomeMetricRibbon(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp),
                )
                HomeOperationsPanel(
                    state = state,
                    onOpenEnergyRecovery = onOpenEnergyRecovery,
                    onOpenVehicleSystem = onOpenVehicleSystem,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(232.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BatteryHealthPanel(
                        state = state,
                        onClick = onOpenBatteryHealth,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    VehicleEnvironmentPanel(
                        state = state,
                        onClick = onOpenVehicleEnvironment,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    TripRoutePanel(
                        state = state,
                        onOpenTripMap = onOpenTripMap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    ChargeRecordPanel(
                        charge = state.latestCharge,
                        currencySymbol = state.currencySymbol,
                        onOpenCharges = onOpenCharges,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                LocalInsightPanel(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp),
                )
            } else {
                HomeHero(
                    state = state,
                    image = customVehicleImage,
                    onPickImage = { imagePicker.launch("image/*") },
                    onDeleteImage = ::deleteCustomVehicleImage,
                    stacked = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 620.dp else 390.dp),
                )
                HomeMetricRibbon(
                    state = state,
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 220.dp else 150.dp),
                )
                HomeOperationsPanel(
                    state = state,
                    onOpenEnergyRecovery = onOpenEnergyRecovery,
                    onOpenVehicleSystem = onOpenVehicleSystem,
                    stacked = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 218.dp else 112.dp),
                )
                BatteryHealthPanel(
                    state = state,
                    onClick = onOpenBatteryHealth,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                VehicleEnvironmentPanel(
                    state = state,
                    onClick = onOpenVehicleEnvironment,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                TripRoutePanel(
                    state = state,
                    onOpenTripMap = onOpenTripMap,
                    modifier = Modifier.fillMaxWidth().height(270.dp),
                )
                ChargeRecordPanel(
                    charge = state.latestCharge,
                    currencySymbol = state.currencySymbol,
                    onOpenCharges = onOpenCharges,
                    modifier = Modifier.fillMaxWidth().height(270.dp),
                )
                LocalInsightPanel(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(if (compact) 360.dp else 260.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
