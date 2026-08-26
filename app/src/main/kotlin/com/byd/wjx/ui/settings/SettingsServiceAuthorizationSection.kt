package com.byd.wjx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentContent
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.AccentPurple
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun ServiceAuthorizationSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val activation = state.activation
    ActivationPaymentDialog(state, viewModel)

    SectionHeader(text = "服务与授权")
    ServiceQrSupportPanel(
        state = state.serviceQr,
        onRefresh = viewModel::refreshServiceQrCodes,
        imageUpdates = viewModel::serviceQrImageUpdates,
    )

    ServicePanel(
        title = "购买永久授权 · $PERMANENT_AUTH_PRICE",
        subtitle = if (activation.activated) "当前车辆已开通永久授权" else "$PERMANENT_AUTH_PRICE / 车，一车一授权",
        accent = if (activation.activated) AccentGreen else AccentOrange,
    ) {
        Text(
            "超级玩具箱永久授权价格为 $PERMANENT_AUTH_PRICE / 车，采用一车一授权并绑定当前车辆 VIN。扫码付款后由服务器核实到账并自动激活，不需要发送付款截图。",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        ServiceInfoLine("授权价格", "$PERMANENT_AUTH_PRICE / 车", AccentOrange)
        ServiceInfoLine(
            "当前状态",
            activation.statusLabel,
            if (activation.activated) AccentGreen else if (activation.trialActive) AccentOrange else SocRed,
        )
        ServiceInfoLine(
            "绑定车架号",
            activation.deviceCode.ifBlank { "未读取到车架号" },
            if (activation.deviceCode.isBlank()) SocRed else TextPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { viewModel.startActivationPayment("alipay") },
                enabled = !activation.activated &&
                    activation.deviceCode.isNotBlank() &&
                    !state.activationPayment.loading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
            ) {
                Text("支付宝扫码", fontSize = 12.sp, maxLines = 1)
            }
            Button(
                onClick = { viewModel.startActivationPayment("wxpay") },
                enabled = !activation.activated &&
                    activation.deviceCode.isNotBlank() &&
                    !state.activationPayment.loading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
            ) {
                Text("微信扫码", fontSize = 12.sp, maxLines = 1)
            }
        }
        state.activationPayment.message
            ?.takeIf { state.activationPayment.order == null }
            ?.let { message ->
                Text(
                    message,
                    color = if (message.contains("失败") || message.contains("失效")) SocRed else TextSecondary,
                    fontSize = 11.sp,
                )
            }
    }

    ServicePanel(
        title = "付款与售后规则",
        subtitle = "永久授权 $PERMANENT_AUTH_PRICE，确认后付款",
        accent = AccentPurple,
    ) {
        ServiceBullet("永久授权价格为 $PERMANENT_AUTH_PRICE / 车，一车一授权。")
        ServiceBullet("请先联系客服确认车型、系统版本和需要使用的功能。")
        ServiceBullet("请在软件内选择支付宝或微信，打开支付页面后用手机扫码完成付款。")
        ServiceBullet("服务器确认到账后自动绑定当前 VIN 并开通永久授权。")
        ServiceBullet("授权开通后，更新或重新安装会按车架号自动恢复。")
        ServiceBullet("付款即表示你已阅读并同意软件免责声明及授权规则。")
    }
}

@Composable
internal fun ServicePanel(
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.96f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    title.take(1),
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            HorizontalDivider(color = CardBorder.copy(alpha = 0.72f))
            content()
        }
    }
}

@Composable
private fun ServiceInfoLine(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.36f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            color = valueColor ?: TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ServiceBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("•", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}
