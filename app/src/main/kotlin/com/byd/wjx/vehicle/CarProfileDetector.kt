package com.byd.wjx.vehicle

import com.byd.wjx.audio.ExteriorChannel
import java.io.BufferedReader
import java.io.InputStreamReader

class CarProfileDetector(
    private val clock: () -> Long = System::currentTimeMillis,
    private val propertyReader: (String) -> String = ::readSystemProperty,
) {
    @Volatile private var cachedProfile: CarProfile? = null
    @Volatile private var cachedAtMs: Long = 0L

    fun detect(): CarProfile {
        val now = clock()
        cachedProfile?.let { cached ->
            if (cached.series != 0 || now - cachedAtMs < UNKNOWN_CACHE_MS) return cached
        }
        val readings = CONTROL_SERIES_PROPERTIES
            .map { property -> propertyReader(property).trim() }
            .filter { it.isNotBlank() }
        val matched = readings.firstNotNullOfOrNull { raw ->
            parseControllerSeries(raw)?.let { series -> raw to series }
        }
        val raw = matched?.first ?: readings.firstOrNull().orEmpty()
        val series = matched?.second ?: 0
        val channel = channelForSeries(series)
        return CarProfile(raw = raw, series = series, channel = channel).also {
            cachedProfile = it
            cachedAtMs = now
        }
    }

    companion object {
        private val CONTROL_SERIES_PROPERTIES = listOf(
            "apps.setting.product.outswver",
            "ro.build.version.incremental",
            "ro.vendor.build.version.incremental",
            "ro.build.display.id",
        )
        private val KNOWN_SERIES = setOf(23, 34, 57)
        private val SERIES_TOKEN_REGEX = Regex("""^(23|34|57)(?:\s*控|controller|[_\-.]|$)""", RegexOption.IGNORE_CASE)
        private const val UNKNOWN_CACHE_MS = 30_000L

        fun channelForSeries(series: Int): ExteriorChannel =
            when (series) {
                23 -> ExteriorChannel.Channel0
                34, 57 -> ExteriorChannel.Channel1
                else -> ExteriorChannel.Channel1
            }

        fun parseControllerSeries(raw: String): Int? {
            val trimmed = raw.trim()
            val leading = trimmed.substringBefore(".").toIntOrNull()
            if (leading != null && leading in KNOWN_SERIES) return leading
            return SERIES_TOKEN_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        private fun readSystemProperty(name: String): String {
            val direct = runCatching {
                val type = Class.forName("android.os.SystemProperties")
                type.getMethod("get", String::class.java).invoke(null, name) as? String
            }.getOrNull().orEmpty()
            if (direct.isNotBlank()) return direct

            return runCatching {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop $name"))
                val line = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
                process.waitFor()
                line.orEmpty()
            }.getOrDefault("")
        }
    }
}

data class CarProfile(
    val raw: String,
    val series: Int,
    val channel: ExteriorChannel,
) {
    val seriesLabel: String
        get() = when (series) {
            23 -> "23 控"
            34 -> "34 控"
            57 -> "57 控"
            0 -> "未知"
            else -> "$series 控"
        }
}
