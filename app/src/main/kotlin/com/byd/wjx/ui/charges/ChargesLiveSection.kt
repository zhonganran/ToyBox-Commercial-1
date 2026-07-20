package com.byd.wjx.ui.charges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.data.autoservice.ChargeGunState
import com.byd.wjx.data.autoservice.ChargingReading
import com.byd.wjx.data.local.entity.ChargeEntity
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChargeActionSheet(
    charge: ChargeEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeletePrompt: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${formatChargeType(charge.type)} • ${charge.kwhCharged?.let { "%.1f".format(it) } ?: "—"} 千瓦时",
                color = TextSecondary, fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.charges_action_edit), color = TextPrimary, fontSize = 16.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDeletePrompt).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.charges_action_delete), color = SocRed, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─── Fallback states ──────────────────────────────────────────────────────────

@Composable
internal fun SentinelEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .background(CardSurface, RoundedCornerShape(12.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 32.dp, vertical = 60.dp)
        ) {
            Text(
                text = stringResource(R.string.charges_sentinel_empty_text),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
internal fun LiveChargingCard(reading: ChargingReading) {
    val status = when (reading.bmsState) {
        1 -> "充电中"
        13 -> "充电暂停"
        else -> "充电枪已连接"
    }
    val type = when (reading.gunConnectState) {
        ChargeGunState.AC -> "交流慢充"
        ChargeGunState.DC, ChargeGunState.AC_DC -> "直流快充"
        else -> when (reading.chargingType) {
            2 -> "交流慢充"
            4, 5 -> "直流快充"
            else -> "充电"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, AccentGreen.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(modifier = Modifier.width(132.dp)) {
                Text("实时充电", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("$type · $status", color = AccentGreen, fontSize = 12.sp)
            }
            LiveChargingMetric(
                label = "功率",
                value = reading.chargePowerKw?.let { "%.1f kW".format(it) } ?: "—",
            )
            LiveChargingMetric(
                label = "电流",
                value = reading.chargeCurrentA?.let { "$it A" } ?: "—",
            )
            LiveChargingMetric(
                label = "电压",
                value = reading.chargeBatteryVoltV?.let { "$it V" } ?: "—",
            )
            LiveChargingMetric(
                label = "本次电量",
                value = reading.chargingCapacityKwh?.let { "%.2f kWh".format(it) } ?: "—",
            )
        }
        HorizontalDivider(color = CardBorder.copy(alpha = 0.45f), thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Spacer(modifier = Modifier.width(132.dp))
            LiveChargingMetric(
                label = "允许充电功率",
                value = reading.batteryMaxChargePowerKw?.let { "$it kW" } ?: "—",
            )
            LiveChargingMetric(
                label = "允许放电功率",
                value = reading.batteryMaxDischargePowerKw?.let { "$it kW" } ?: "—",
            )
            LiveChargingMetric(
                label = "绝缘电阻",
                value = reading.batteryInsulationKohm?.let { "$it kΩ" } ?: "—",
            )
            LiveChargingMetric(
                label = "停止 SOC",
                value = reading.chargeStopSoc?.let { "$it%" } ?: "—",
            )
        }
    }
}

@Composable
private fun LiveChargingMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(
            value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

internal fun formatChargeType(type: String?): String = when (type) {
    "AC" -> "慢充"
    "DC" -> "快充"
    null -> "—"
    else -> type
}
