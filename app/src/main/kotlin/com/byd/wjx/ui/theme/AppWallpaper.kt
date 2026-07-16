package com.byd.wjx.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.byd.wjx.R

/**
 * Built-in wallpapers shared by every screen in the main application scaffold.
 *
 * Full-size and thumbnail resources are kept separate so opening the wallpaper
 * picker does not decode seven full-resolution images at the same time.
 */
enum class AppWallpaper(
    val storedValue: String,
    val displayName: String,
    val description: String,
    @DrawableRes val drawableRes: Int,
    @DrawableRes val thumbnailRes: Int,
    val darkArtwork: Boolean,
) {
    SAKURA_LAKE(
        storedValue = "sakura_lake",
        displayName = "樱花晴空",
        description = "清透湖山",
        drawableRes = R.drawable.app_wallpaper_sakura_lake,
        thumbnailRes = R.drawable.app_wallpaper_thumb_sakura_lake,
        darkArtwork = false,
    ),
    AURORA_TWILIGHT(
        storedValue = "aurora_twilight",
        displayName = "极光暮色",
        description = "蓝紫雪山",
        drawableRes = R.drawable.app_wallpaper_aurora_twilight,
        thumbnailRes = R.drawable.app_wallpaper_thumb_aurora_twilight,
        darkArtwork = true,
    ),
    SUNRISE_LAKE(
        storedValue = "sunrise_lake",
        displayName = "晨曦湖山",
        description = "暖光云海",
        drawableRes = R.drawable.app_wallpaper_sunrise_lake,
        thumbnailRes = R.drawable.app_wallpaper_thumb_sunrise_lake,
        darkArtwork = false,
    ),
    TECH_NIGHT(
        storedValue = "tech_night",
        displayName = "科技极夜",
        description = "深蓝光轨",
        drawableRes = R.drawable.app_wallpaper_tech_night,
        thumbnailRes = R.drawable.app_wallpaper_thumb_tech_night,
        darkArtwork = true,
    ),
    INK_MOUNTAINS(
        storedValue = "ink_mountains",
        displayName = "水墨云山",
        description = "东方留白",
        drawableRes = R.drawable.app_wallpaper_ink_mountains,
        thumbnailRes = R.drawable.app_wallpaper_thumb_ink_mountains,
        darkArtwork = false,
    ),
    GLACIER_AURORA(
        storedValue = "glacier_aurora",
        displayName = "冰川极光",
        description = "青绿冰原",
        drawableRes = R.drawable.app_wallpaper_glacier_aurora,
        thumbnailRes = R.drawable.app_wallpaper_thumb_glacier_aurora,
        darkArtwork = false,
    ),
    SUNSET_CANYON(
        storedValue = "sunset_canyon",
        displayName = "金色峡谷",
        description = "暖色落日",
        drawableRes = R.drawable.app_wallpaper_sunset_canyon,
        thumbnailRes = R.drawable.app_wallpaper_thumb_sunset_canyon,
        darkArtwork = true,
    );

    companion object {
        val Default: AppWallpaper = SAKURA_LAKE

        fun fromStored(value: String?): AppWallpaper =
            entries.firstOrNull { it.storedValue == value } ?: Default
    }
}

val LocalAppWallpaper = staticCompositionLocalOf { AppWallpaper.Default }
