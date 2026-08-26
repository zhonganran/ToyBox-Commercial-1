package com.byd.wjx.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.byd.wjx.ui.theme.*
import kotlinx.coroutines.delay

@Composable
internal fun ActivationPaymentDialog(state: SettingsUiState, viewModel: SettingsViewModel) {
    val payment = state.activationPayment
    val order = payment.order
    if (!payment.dialogVisible) return
    Dialog(onDismissRequest = viewModel::dismissActivationPayment) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            if (order == null) {
                PaymentOrderLoadingContent(payment, viewModel::dismissActivationPayment)
            } else {
                PaymentOrderContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun PaymentOrderLoadingContent(
    payment: ActivationPaymentUiState,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (payment.loading) {
            CircularProgressIndicator(color = AccentGreen)
        } else {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = SocRed,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            payment.message ?: "正在创建安全支付订单",
            color = if (payment.loading) TextPrimary else SocRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (!payment.loading) {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CardSurface,
                    contentColor = TextPrimary,
                ),
            ) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun PaymentOrderContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    val payment = state.activationPayment
    val order = requireNotNull(payment.order)
    var nowMillis by remember(order.orderNo) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(order.orderNo, order.expiresAt) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val remainingSeconds = ((order.expiresAt - nowMillis) / 1_000L).coerceAtLeast(0L)
    val remainingLabel = "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)
    val paymentExpired = remainingSeconds <= 0L || payment.message?.contains("过期") == true

    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaymentCheckoutContent(
            order = order,
            modifier = Modifier
                .width(390.dp)
                .height(620.dp)
                .background(Color.White, RoundedCornerShape(8.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PaymentOrderHeader(order.paymentName, viewModel::dismissActivationPayment)
            Text(
                "¥${order.amount}",
                color = AccentOrange,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (paymentExpired) "订单已过期" else "订单有效期剩余 $remainingLabel",
                color = if (paymentExpired) SocRed else AccentOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                payment.message ?: "请扫码支付，到账后会自动激活",
                color = if (state.activation.activated) AccentGreen else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                paymentGuidance(state.activation.activated, paymentExpired, order.paymentName),
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            if (paymentExpired) {
                Button(
                    onClick = { viewModel.startActivationPayment(order.paymentType) },
                    enabled = !payment.loading,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("重新生成订单", fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "订单号 ${order.orderNo}",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PaymentOrderHeader(paymentName: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${paymentName}扫码支付",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text("永久授权 · 绑定当前车辆", color = TextSecondary, fontSize = 12.sp)
        }
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CardSurface,
                contentColor = TextPrimary,
            ),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
        }
    }
}

private fun paymentGuidance(activated: Boolean, expired: Boolean, paymentName: String): String =
    when {
        activated -> "当前车辆已经获得永久授权，可以关闭此窗口。"
        expired -> "订单已过期，请点击下方重新生成订单。"
        else -> "请使用手机${paymentName}完成支付，支付完成后本页面会自动更新。"
    }

internal fun shouldShowActivationPaymentDialog(state: SettingsUiState): Boolean =
    state.activationPayment.dialogVisible
