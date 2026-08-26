package com.byd.wjx.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentPurple
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SocRed
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

@Composable
internal fun ServiceQrSupportPanel(
    state: SettingsServiceQrUiState,
    onRefresh: () -> Unit,
    imageUpdates: (String, String) -> Flow<ServiceQrImageUpdate>,
) {
    var activeTarget by remember { mutableStateOf<ServiceQrTarget?>(null) }
    val wechatTarget = serviceQrTarget(
        state.codes,
        "wechat",
        "微信客服",
        "点击查看二维码",
        "待配置微信二维码",
        AccentGreen,
    )
    val groupTarget = serviceQrTarget(
        state.codes,
        "wechat_group",
        "微信交流群",
        "点击查看二维码",
        "待配置微信交流群二维码",
        AccentPurple,
    )

    activeTarget?.let { target ->
        ServiceQrDialog(
            target = target,
            imageUpdates = imageUpdates,
            onDismiss = { activeTarget = null },
        )
    }

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
                state.message ?: if (state.loading) "正在读取后台二维码" else "二维码从后台动态读取",
                color = if (state.messageIsError) SocRed else TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onRefresh,
                enabled = !state.loading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardSurface, contentColor = TextPrimary),
            ) {
                Text(if (state.loading) "读取中" else "刷新", fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServiceQrEntryCard(wechatTarget, Modifier.weight(1f)) { activeTarget = it }
            ServiceQrEntryCard(groupTarget, Modifier.weight(1f)) { activeTarget = it }
        }
    }
}

internal data class ServiceQrTarget(
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
    codes: Map<String, ServiceQrCode>,
    key: String,
    fallbackTitle: String,
    fallbackSubtitle: String,
    fallbackPlaceholder: String,
    accent: Color,
): ServiceQrTarget {
    val remote = codes[key]
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
private fun ServiceQrDialog(
    target: ServiceQrTarget,
    imageUpdates: (String, String) -> Flow<ServiceQrImageUpdate>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(target.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(target.subtitle, color = TextSecondary, fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = TextPrimary)
                    }
                }
                Column(
                    modifier = Modifier
                        .size(230.dp)
                        .background(Color.White, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RemoteQrImage(target, imageUpdates)
                }
                Text(serviceQrDialogNote(target), color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun RemoteQrImage(
    target: ServiceQrTarget,
    imageUpdates: (String, String) -> Flow<ServiceQrImageUpdate>,
) {
    var imageBytes by remember(target.key, target.imageUrl) { mutableStateOf<ByteArray?>(null) }
    var loadFailed by remember(target.key, target.imageUrl) { mutableStateOf(false) }

    LaunchedEffect(target.key, target.imageUrl) {
        imageBytes = null
        loadFailed = false
        imageUpdates(target.key, target.imageUrl).collect { update ->
            when (update) {
                is ServiceQrImageUpdate.Available -> imageBytes = update.bytes
                ServiceQrImageUpdate.Failed -> loadFailed = true
                ServiceQrImageUpdate.Missing -> Unit
            }
        }
    }

    val imageBitmap = remember(imageBytes) {
        imageBytes?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
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
            if (loadFailed || imageBytes != null) "二维码加载失败" else target.placeholder,
            color = Color(0xFF424750),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun serviceQrDialogNote(target: ServiceQrTarget): String {
    val description = target.description.ifBlank { "二维码由后台动态更新，过期后无需更新 APK。" }
    return if (target.expiresAt.isNotBlank()) "$description\n有效期：${target.expiresAt}" else description
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
