package com.byd.wjx.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.R
import com.byd.wjx.ui.theme.AppWallpaper
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun SettingsAppearanceSection(
    preferences: SettingsAppPreferencesUiState,
    onThemeModeChange: (String) -> Unit,
    onWallpaperChange: (String) -> Unit,
) {
    SectionHeader(text = stringResource(R.string.settings_appearance_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.settings_theme_title),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.settings_theme_description),
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitChip(
                    stringResource(R.string.settings_theme_light),
                    preferences.themeMode == "light",
                ) { onThemeModeChange("light") }
                UnitChip(
                    stringResource(R.string.settings_theme_dark),
                    preferences.themeMode == "dark",
                ) { onThemeModeChange("dark") }
                UnitChip(
                    stringResource(R.string.settings_theme_system),
                    preferences.themeMode == "system",
                ) { onThemeModeChange("system") }
            }

            HorizontalDivider(color = CardBorder.copy(alpha = 0.68f))
            Text(
                "背景壁纸",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "切换后会同步应用到首页、个性音效、桌面组件、智能联动和设置等全部页面。",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(AppWallpaper.entries, key = { it.storedValue }) { wallpaper ->
                    WallpaperOption(
                        wallpaper = wallpaper,
                        selected = preferences.appWallpaper == wallpaper.storedValue,
                        onClick = { onWallpaperChange(wallpaper.storedValue) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperOption(
    wallpaper: AppWallpaper,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) AccentGreen else CardBorder,
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            Image(
                painter = painterResource(wallpaper.thumbnailRes),
                contentDescription = wallpaper.displayName,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "当前壁纸",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                        .padding(2.dp),
                )
            }
        }
        Text(
            wallpaper.displayName,
            color = if (selected) AccentGreen else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            wallpaper.description,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}
