package com.byd.wjx.ui.sound

import androidx.annotation.DrawableRes
import com.byd.wjx.R

@DrawableRes
internal fun soundArtworkRes(name: String, seed: String): Int {
    val normalized = name.lowercase()
    return when {
        "银河" in normalized || "星空" in normalized || "宇宙" in normalized -> R.drawable.sound_art_galaxy
        "机甲" in normalized || "机器人" in normalized || "启动" in normalized -> R.drawable.sound_art_mech
        "回家" in normalized || "欢迎" in normalized || "到家" in normalized -> R.drawable.sound_art_home
        "倒车" in normalized || "泊车" in normalized || "雷达" in normalized -> R.drawable.sound_art_reverse
        "节日" in normalized || "彩蛋" in normalized || "庆祝" in normalized -> R.drawable.sound_art_party
        "充电" in normalized || "电量" in normalized -> R.drawable.sound_art_charge
        "提示" in normalized || "铃" in normalized || "通知" in normalized -> R.drawable.sound_art_bell
        else -> listOf(
            R.drawable.sound_art_wave,
            R.drawable.sound_art_galaxy,
            R.drawable.sound_art_home,
            R.drawable.sound_art_party,
            R.drawable.sound_art_bell,
        )[(seed.hashCode() and Int.MAX_VALUE) % 5]
    }
}

@DrawableRes
internal fun soundBackgroundRes(name: String, category: String, seed: Long): Int {
    val normalizedName = name.lowercase()
    val normalizedCategory = category.lowercase()
    val direct = when {
        listOf("能量", "充电", "电力", "电量", "提示", "铃", "通知").any(normalizedName::contains) -> R.drawable.sound_bg_energy_awaken
        listOf("银河", "星空", "宇宙", "星云").any(normalizedName::contains) -> R.drawable.sound_bg_galaxy_start
        listOf("机甲", "机器人", "机械", "启动").any(normalizedName::contains) -> R.drawable.sound_bg_mech_awaken
        listOf("星际", "飞船", "太空", "启程").any(normalizedName::contains) -> R.drawable.sound_bg_interstellar
        listOf("运动", "律动", "速度", "跑车").any(normalizedName::contains) -> R.drawable.sound_bg_sport_rhythm
        listOf("竹", "清风", "森林", "自然").any(normalizedName::contains) -> R.drawable.sound_bg_bamboo_breeze
        listOf("国风", "古风", "雅韵", "山水").any(normalizedName::contains) -> R.drawable.sound_bg_chinese_elegance
        listOf("海浪", "海洋", "潮汐", "浪花").any(normalizedName::contains) -> R.drawable.sound_bg_ocean_whisper
        listOf("雨夜", "白噪", "雨声", "睡眠").any(normalizedName::contains) -> R.drawable.sound_bg_rainy_noise
        listOf("未来", "城市", "科技", "赛博", "倒车", "泊车", "雷达", "哨兵").any(normalizedName::contains) -> R.drawable.sound_bg_future_city
        listOf("回家", "欢迎", "到家", "归家", "锁车", "解锁").any(normalizedName::contains) -> R.drawable.sound_bg_welcome_home
        listOf("节日", "庆祝", "欢庆", "烟花", "彩蛋", "新年").any(normalizedName::contains) -> R.drawable.sound_bg_festival
        else -> null
    }
    if (direct != null) return direct

    val candidates = when {
        "启动" in normalizedCategory -> listOf(
            R.drawable.sound_bg_galaxy_start,
            R.drawable.sound_bg_mech_awaken,
            R.drawable.sound_bg_interstellar,
            R.drawable.sound_bg_energy_awaken,
        )
        "氛围" in normalizedCategory -> listOf(
            R.drawable.sound_bg_bamboo_breeze,
            R.drawable.sound_bg_chinese_elegance,
            R.drawable.sound_bg_ocean_whisper,
            R.drawable.sound_bg_rainy_noise,
            R.drawable.sound_bg_future_city,
        )
        "趣味" in normalizedCategory -> listOf(
            R.drawable.sound_bg_sport_rhythm,
            R.drawable.sound_bg_future_city,
            R.drawable.sound_bg_festival,
        )
        "节日" in normalizedCategory -> listOf(
            R.drawable.sound_bg_festival,
            R.drawable.sound_bg_welcome_home,
        )
        "提示" in normalizedCategory -> listOf(
            R.drawable.sound_bg_energy_awaken,
            R.drawable.sound_bg_welcome_home,
            R.drawable.sound_bg_future_city,
        )
        else -> listOf(
            R.drawable.sound_bg_galaxy_start,
            R.drawable.sound_bg_mech_awaken,
            R.drawable.sound_bg_interstellar,
            R.drawable.sound_bg_sport_rhythm,
            R.drawable.sound_bg_bamboo_breeze,
            R.drawable.sound_bg_chinese_elegance,
            R.drawable.sound_bg_ocean_whisper,
            R.drawable.sound_bg_rainy_noise,
            R.drawable.sound_bg_future_city,
            R.drawable.sound_bg_energy_awaken,
            R.drawable.sound_bg_welcome_home,
            R.drawable.sound_bg_festival,
        )
    }
    return candidates[((seed and Long.MAX_VALUE) % candidates.size).toInt()]
}
