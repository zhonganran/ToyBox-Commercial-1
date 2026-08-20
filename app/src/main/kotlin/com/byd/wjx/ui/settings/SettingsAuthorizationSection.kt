package com.byd.wjx.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.byd.wjx.ui.theme.*

internal const val PERMANENT_AUTH_PRICE = "15.9 元"

@Composable
internal fun AuthorizationRailCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val darkTheme = LocalSuperToyBoxDarkTheme.current
    val context = LocalContext.current
    val activation = state.activation
    val statusColor = when {
        activation.activated -> AccentGreen
        activation.trialActive -> AccentOrange
        else -> SocRed
    }
    val statusDetail = when {
        activation.activated -> "感谢您对「超级玩具箱」的信任与支持"
        activation.trialActive -> "剩余 ${activation.remainingTrialDays} 天试用，永久授权 $PERMANENT_AUTH_PRICE"
        else -> "试用已结束，永久授权 $PERMANENT_AUTH_PRICE"
    }
    fun copyText(label: String, value: String, toast: String) {
        if (value.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    ActivationPaymentDialog(state, viewModel)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.86f)),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            if (darkTheme) {
                                listOf(Color(0xFF17302F), Color(0xFF182B3C), Color(0xFF1B3340))
                            } else {
                                listOf(Color(0xFFBDF2F0), Color(0xFFC9E8FF), Color(0xFFDDF4FF))
                            },
                        ),
                    )
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                Icon(
                    Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.42f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(150.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .background(AccentGreen.copy(alpha = 0.14f), RoundedCornerShape(46.dp))
                                .border(6.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(46.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(52.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    if (activation.activated) "永久授权" else if (activation.trialActive) "试用授权" else "等待激活",
                                    color = TextPrimary,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    if (activation.deviceCode.isBlank()) "设备未识别" else "设备已识别",
                                    color = if (activation.deviceCode.isBlank()) SocRed else AccentGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background((if (activation.deviceCode.isBlank()) SocRed else AccentGreen).copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                                Text(
                                    activation.statusLabel,
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                            Text(statusDetail, color = TextPrimary.copy(alpha = 0.76f), fontSize = 13.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        AuthorizationValue(
                            label = "已绑定车辆（VIN）",
                            value = activation.deviceCode.ifBlank { "未读取到车架号" },
                            error = activation.deviceCode.isBlank(),
                            modifier = Modifier.weight(1f),
                        )
                        AuthorizationValue(
                            label = "已绑定激活码",
                            value = activation.boundActivationCode.ifBlank {
                                if (activation.activated) "已绑定云端授权" else "尚未绑定"
                            },
                            error = !activation.activated,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { copyText("超级玩具箱授权信息", activation.boundActivationCode.ifBlank { activation.deviceCode }, "授权信息已复制") },
                            enabled = activation.deviceCode.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceElevated.copy(alpha = 0.86f), contentColor = TextPrimary),
                            modifier = Modifier.height(56.dp),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("复制", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.refreshActivation(refreshDeviceCode = true) },
                            enabled = !state.activationLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceElevated.copy(alpha = 0.86f), contentColor = TextPrimary),
                            modifier = Modifier.height(56.dp),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(if (state.activationLoading) "读取中" else "刷新", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (!activation.activated) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActivationMetricPill(
                            label = "试用状态",
                            value = if (activation.trialActive) "剩余 ${activation.remainingTrialDays} 天" else "已结束",
                            color = if (activation.trialActive) AccentOrange else SocRed,
                            modifier = Modifier.weight(1f),
                        )
                        ActivationMetricPill(
                            label = "车辆绑定",
                            value = if (activation.deviceCode.isBlank()) "待读取" else "已识别",
                            color = if (activation.deviceCode.isBlank()) SocRed else AccentBlue,
                            modifier = Modifier.weight(1f),
                        )
                        ActivationMetricPill(
                            label = "永久授权",
                            value = PERMANENT_AUTH_PRICE,
                            color = AccentGreen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { viewModel.startActivationPayment("alipay") },
                            enabled = !state.activationPaymentLoading && activation.deviceCode.isNotBlank(),
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
                        ) {
                            Icon(Icons.Outlined.QrCode2, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("支付宝扫码购买", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.startActivationPayment("wxpay") },
                            enabled = !state.activationPaymentLoading && activation.deviceCode.isNotBlank(),
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
                        ) {
                            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("微信扫码购买", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    state.activationPaymentMessage
                        ?.takeIf { state.activationPaymentOrder == null }
                        ?.let { message ->
                            Text(
                                message,
                                color = if (message.contains("失败") || message.contains("失效")) SocRed else TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    HorizontalDivider(color = CardBorder)
                    Text("已有激活码", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.activationCodeInput,
                        onValueChange = { viewModel.updateActivationCode(it) },
                        label = { Text("输入激活码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = AccentGreen,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = AccentGreen,
                        ),
                    )
                    Button(
                        onClick = { viewModel.activate() },
                        enabled = !state.activationLoading && state.activationCodeInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
                    ) {
                        Text("立即激活", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            state.activationMessage?.let {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        it,
                        color = if (it.contains("成功")) AccentGreen else SocRed,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ActivationPaymentDialog(state: SettingsUiState, viewModel: SettingsViewModel) {
    val order = state.activationPaymentOrder
    if (!state.activationPaymentLoading && order == null) return
    Dialog(onDismissRequest = viewModel::dismissActivationPayment) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            if (order == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = AccentGreen)
                    Text(
                        state.activationPaymentMessage ?: "正在创建安全支付订单",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                val qrBitmap = remember(order.payInfo) { createPaymentQrBitmap(order.payInfo) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "支付二维码",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text("二维码生成失败", color = SocRed, fontSize = 13.sp)
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${order.paymentName}扫码支付",
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text("永久授权 · 绑定当前车辆", color = TextSecondary, fontSize = 12.sp)
                            }
                            Button(
                                onClick = viewModel::dismissActivationPayment,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CardSurface,
                                    contentColor = TextPrimary,
                                ),
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            "¥${order.amount}",
                            color = AccentOrange,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            state.activationPaymentMessage ?: "请扫码支付，到账后会自动激活",
                            color = if (state.activation.activated) AccentGreen else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (state.activation.activated) {
                                "当前车辆已经获得永久授权，可以关闭此窗口。"
                            } else {
                                "请勿重复付款。服务器验签并确认到账后，本页面会自动更新。"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
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
        }
    }
}

private fun createPaymentQrBitmap(content: String, size: Int = 720): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()

@Composable
private fun AuthorizationValue(
    label: String,
    value: String,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = TextPrimary.copy(alpha = 0.70f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(
            value,
            color = if (error) SocRed else TextPrimary,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActivationMetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
internal fun SettingsCapabilityCard(state: SettingsUiState) {
    val activation = state.activation
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.88f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("授权能力", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            CapabilityRow("基础功能", "音效播放、歌词显示、常用设置", "可用", AccentGreen)
            CapabilityRow(
                "高级功能",
                "解锁/锁车/哨兵音效、按键快捷操作、仪表投射",
                if (activation.activated || activation.trialActive) "可用" else "需激活",
                if (activation.activated || activation.trialActive) AccentBlue else SocRed,
            )
            CapabilityRow(
                "云端恢复",
                if (activation.activated) "重装或更新后按车架号恢复授权" else "激活后自动绑定当前车架号",
                if (activation.activated) "已开启" else "待激活",
                if (activation.activated) AccentGreen else TextMuted,
            )
        }
    }
}

@Composable
private fun CapabilityRow(
    title: String,
    subtitle: String,
    status: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Text(
            status,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(color.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
