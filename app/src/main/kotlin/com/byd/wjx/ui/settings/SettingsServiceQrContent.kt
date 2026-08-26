package com.byd.wjx.ui.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentPurple
import com.byd.wjx.ui.theme.CardSurface
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
    val wechatTarget = serviceQrTarget(
        state.codes,
        "wechat",
        "微信客服",
        "扫码添加客服",
        "待配置微信二维码",
        AccentGreen,
    )
    val groupTarget = serviceQrTarget(
        state.codes,
        "wechat_group",
        "微信交流群",
        "扫码加入交流群",
        "待配置微信交流群二维码",
        AccentPurple,
    )

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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 520.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ServiceQrInlineItem(wechatTarget, imageUpdates, Modifier.fillMaxWidth())
                    ServiceQrInlineItem(groupTarget, imageUpdates, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ServiceQrInlineItem(wechatTarget, imageUpdates, Modifier.weight(1f))
                    ServiceQrInlineItem(groupTarget, imageUpdates, Modifier.weight(1f))
                }
            }
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
    val remoteSubtitle = remote?.subtitle
        ?.takeUnless { it.isBlank() || it == "点击查看二维码" }
    return ServiceQrTarget(
        key = key,
        title = remote?.title?.ifBlank { fallbackTitle } ?: fallbackTitle,
        subtitle = remoteSubtitle ?: fallbackSubtitle,
        placeholder = if (remote?.imageUrl.isNullOrBlank()) fallbackPlaceholder else "正在加载二维码",
        imageUrl = remote?.imageUrl.orEmpty(),
        expiresAt = remote?.expiresAt.orEmpty(),
        description = remote?.description.orEmpty(),
        accent = accent,
    )
}

@Composable
private fun ServiceQrInlineItem(
    target: ServiceQrTarget,
    imageUpdates: (String, String) -> Flow<ServiceQrImageUpdate>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(target.accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(target.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(target.subtitle, color = target.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            RemoteQrImage(target, imageUpdates)
        }
        Text(serviceQrNote(target), color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
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
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            QrPlaceholderPattern()
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                if (loadFailed || imageBytes != null) "二维码加载失败" else target.placeholder,
                color = Color(0xFF424750),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun serviceQrNote(target: ServiceQrTarget): String {
    val description = target.description.ifBlank { "二维码由后台动态更新，过期后无需更新 APK。" }
    return if (target.expiresAt.isNotBlank()) "$description\n有效期：${target.expiresAt}" else description
}

@Composable
private fun QrPlaceholderPattern() {
    Column(
        modifier = Modifier
            .size(104.dp)
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
                            .size(7.dp)
                            .background(if (filled) Color(0xFF111318) else Color.White),
                    )
                }
            }
        }
    }
}
