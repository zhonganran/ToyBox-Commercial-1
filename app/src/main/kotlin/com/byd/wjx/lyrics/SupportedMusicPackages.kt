package com.byd.wjx.lyrics

import java.util.Locale

/** Central package catalog for notification/session music discovery. */
object SupportedMusicPackages {
    val names: Set<String> = setOf(
        "com.byd.mediacenter",
        "com.byd.synclink",
        "com.netease.cloudmusic",
        "com.netease.cloudmusic.car",
        "com.kugou.android",
        "com.kugou.android.auto",
        "com.qq.music",
        "com.tencent.qqmusic",
        "com.tencent.qqmusiccar",
        "com.tencent.qqmusicpad",
        "cmccwm.mobilemusic",
        "cn.kuwo.player",
        "cn.kuwo.kwmusiccar",
        "cn.kuwo.kwmusiccar.main",
        "remix.music.player",
        "cmgyunting.vehicleplayer.cnr",
    ) + QishuiMusic.packageNames

    fun isSupported(packageName: String): Boolean {
        val normalized = packageName.trim().lowercase(Locale.ROOT)
        return normalized in names || musicPackagePrefixes.any(normalized::startsWith)
    }

    private val musicPackagePrefixes = setOf(
        "com.netease.cloudmusic",
        "com.kugou.android",
        "com.tencent.qqmusic",
        "cmccwm.mobilemusic",
        "cn.kuwo.",
    )
}

internal object QishuiMusic {
    val packageNames: Set<String> = setOf(
        "com.luna.music",
        "com.luna.music.car",
        "com.qishui.app",
    )

    fun isPackage(packageName: String): Boolean = packageName in packageNames

    fun extractTrackId(vararg candidates: String?): String? {
        candidates.forEach { candidate ->
            val value = candidate?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
            trackIdPatterns.firstNotNullOfOrNull { pattern ->
                pattern.find(value)?.groupValues?.getOrNull(1)
            }?.let { return it }
            if (plainTrackId.matches(value)) return value
        }
        return null
    }

    private val trackIdPatterns = listOf(
        Regex("""/(?:track|trace)/(\d+)"""),
        Regex("""(?:^|[?&#])(?:track_id|trace_id|traceId)=(\d+)"""),
        Regex("""["']?(?:track_id|trace_id|traceId)["']?\s*[:=]\s*["']?(\d+)"""),
    )
    private val plainTrackId = Regex("""\d{6,}""")
}
