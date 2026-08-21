package com.byd.wjx.ui.settings

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.byd.wjx.ui.theme.*
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ServiceAuthorizationSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val activation = state.activation
    var activeQrTarget by remember { mutableStateOf<ServiceQrTarget?>(null) }
    val wechatTarget = serviceQrTarget(state, "wechat", "微信客服", "点击查看二维码", "待配置微信二维码", AccentGreen)
    val wechatGroupTarget = serviceQrTarget(state, "wechat_group", "微信交流群", "点击查看二维码", "待配置微信交流群二维码", AccentPurple)

    activeQrTarget?.let { target ->
        ServiceQrDialog(target = target, onDismiss = { activeQrTarget = null })
    }
    ActivationPaymentDialog(state, viewModel)

    SectionHeader(text = "服务与授权")
    ServicePanel(
        title = "官方客服",
        subtitle = "购买授权、功能咨询、车型适配和售后支持",
        accent = AccentBlue,
    ) {
        Text(
            "如需购买授权、功能咨询、车型适配或售后支持，请通过以下方式联系我。购买前请先说明车型、系统版本和需要使用的功能，避免因车型差异导致部分功能不可用。",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.serviceQrMessage ?: if (state.serviceQrLoading) "正在读取后台二维码" else "二维码从后台动态读取",
                color = if (state.serviceQrMessage?.contains("失败") == true) SocRed else TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.refreshServiceQrCodes() },
                enabled = !state.serviceQrLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardSurface, contentColor = TextPrimary),
            ) {
                Text(if (state.serviceQrLoading) "读取中" else "刷新", fontSize = 12.sp)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ServiceQrEntryCard(
                target = wechatTarget,
                modifier = Modifier.weight(1f),
                onClick = { activeQrTarget = it },
            )
            ServiceQrEntryCard(
                target = wechatGroupTarget,
                modifier = Modifier.weight(1f),
                onClick = { activeQrTarget = it },
            )
        }
    }

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
        ServiceInfoLine(
            label = "授权价格",
            value = "$PERMANENT_AUTH_PRICE / 车",
            valueColor = AccentOrange,
        )
        ServiceInfoLine(
            label = "当前状态",
            value = activation.statusLabel,
            valueColor = if (activation.activated) AccentGreen else if (activation.trialActive) AccentOrange else SocRed,
        )
        ServiceInfoLine(
            label = "绑定车架号",
            value = activation.deviceCode.ifBlank { "未读取到车架号" },
            valueColor = if (activation.deviceCode.isBlank()) SocRed else TextPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { viewModel.startActivationPayment("alipay") },
                enabled = !activation.activated && activation.deviceCode.isNotBlank() && !state.activationPaymentLoading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
            ) {
                Text("支付宝扫码", fontSize = 12.sp, maxLines = 1)
            }
            Button(
                onClick = { viewModel.startActivationPayment("wxpay") },
                enabled = !activation.activated && activation.deviceCode.isNotBlank() && !state.activationPaymentLoading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = AccentContent),
            ) {
                Text("微信扫码", fontSize = 12.sp, maxLines = 1)
            }
        }
        state.activationPaymentMessage
            ?.takeIf { state.activationPaymentOrder == null }
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

private data class ServiceQrTarget(
    val key: String,
    val title: String,
    val subtitle: String,
    val placeholder: String,
    val imageUrl: String,
    val expiresAt: String,
    val description: String,
    val accent: Color,
)

private fun serviceQrTarget(
    state: SettingsUiState,
    key: String,
    fallbackTitle: String,
    fallbackSubtitle: String,
    fallbackPlaceholder: String,
    accent: Color,
): ServiceQrTarget {
    val remote = state.serviceQrCodes[key]
    return ServiceQrTarget(
        key = key,
        title = remote?.title?.ifBlank { fallbackTitle } ?: fallbackTitle,
        subtitle = remote?.subtitle?.ifBlank { fallbackSubtitle } ?: fallbackSubtitle,
        placeholder = if (remote?.imageUrl.isNullOrBlank()) fallbackPlaceholder else "正在加载二维码",
        imageUrl = remote?.imageUrl.orEmpty(),
        expiresAt = remote?.expiresAt.orEmpty(),
        description = remote?.description.orEmpty(),
        accent = accent,
    )
}

@Composable
private fun ServiceQrEntryCard(
    target: ServiceQrTarget,
    modifier: Modifier = Modifier,
    onClick: (ServiceQrTarget) -> Unit,
) {
    Column(
        modifier = modifier
            .background(target.accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .clickable { onClick(target) }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(target.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(
            "点击查看二维码",
            color = target.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp,
            maxLines = 1,
            modifier = Modifier
                .background(CardSurfaceElevated.copy(alpha = 0.86f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ServiceQrDialog(target: ServiceQrTarget, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(target.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(target.subtitle, color = TextSecondary, fontSize = 12.sp)
                    }
                    Text(
                        "X",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(CardSurface, RoundedCornerShape(999.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .size(230.dp)
                        .background(Color.White, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RemoteQrImage(target)
                }
                Text(
                    serviceQrDialogNote(target),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun RemoteQrImage(target: ServiceQrTarget) {
    val context = LocalContext.current
    var imageBytes by remember(target.key, target.imageUrl) { mutableStateOf<ByteArray?>(null) }
    var loadFailed by remember(target.key, target.imageUrl) { mutableStateOf(false) }

    LaunchedEffect(target.key, target.imageUrl) {
        loadFailed = false
        val cacheFile = serviceQrCacheFile(context, target.key)
        val cached = if (target.imageUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching { if (cacheFile.exists()) cacheFile.readBytes() else null }.getOrNull()
            }
        } else {
            null
        }
        if (cached != null) {
            imageBytes = cached
        }
        if (target.imageUrl.isBlank()) {
            loadFailed = cached == null
            return@LaunchedEffect
        }
        val remote = withContext(Dispatchers.IO) {
            runCatching {
                URL(target.imageUrl).openStream().use { input ->
                    input.readBytes()
                }.also { bytes ->
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeBytes(bytes)
                }
            }.getOrNull()
        }
        if (remote != null) {
            imageBytes = remote
        } else if (cached == null) {
            loadFailed = true
        }
    }

    val imageBitmap = remember(imageBytes) {
        imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = target.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    } else {
        QrPlaceholderPattern()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (loadFailed && target.imageUrl.isNotBlank()) "二维码加载失败" else target.placeholder,
            color = Color(0xFF424750),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun serviceQrCacheFile(context: Context, key: String): File =
    File(context.cacheDir, "service_qrcodes/$key.img")

private fun serviceQrDialogNote(target: ServiceQrTarget): String {
    val remoteDescription = target.description.ifBlank {
        if (target.title.contains("付款")) {
            "请先联系客服确认车型、系统版本和功能支持情况后再付款。付款后发送截图和车架号用于开通授权。"
        } else {
            "二维码由后台动态更新，过期后无需更新 APK。"
        }
    }
    val description = if (target.key == "payment") {
        "永久授权价格：$PERMANENT_AUTH_PRICE / 车。\n$remoteDescription"
    } else {
        remoteDescription
    }
    return if (target.expiresAt.isNotBlank()) {
        "$description\n有效期：${target.expiresAt}"
    } else {
        description
    }
}

@Composable
private fun QrPlaceholderPattern() {
    Column(
        modifier = Modifier
            .size(132.dp)
            .background(Color(0xFFF7F8FA), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(9) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(9) { col ->
                    val finder = (row < 3 && col < 3) || (row < 3 && col > 5) || (row > 5 && col < 3)
                    val filled = finder || ((row * 7 + col * 5) % 4 == 0)
                    Spacer(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (filled) Color(0xFF111318) else Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServicePanel(
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
private fun ServiceInfoLine(
    label: String,
    value: String,
    valueColor: Color? = null,
) {
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
