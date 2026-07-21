package com.byd.wjx.statusbar

import android.content.Context
import android.location.Location
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class StatusBarWeatherSnapshot(
    val temperatureC: Int? = null,
    val weatherCode: Int? = null,
    val updatedAtMs: Long = 0L,
) {
    val condition: String? get() = weatherCode?.let(StatusBarWeatherCode::label)
    val displayText: String?
        get() = when {
            condition != null && temperatureC != null -> "$condition ${temperatureC}°C"
            condition != null -> condition
            temperatureC != null -> "${temperatureC}°C"
            else -> null
        }
    val available: Boolean get() = displayText != null
}

@Singleton
class StatusBarWeatherRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _snapshot = MutableStateFlow(loadCache())
    val snapshot: StateFlow<StatusBarWeatherSnapshot> = _snapshot.asStateFlow()

    suspend fun refresh(location: Location) = withContext(Dispatchers.IO) {
        val url = WEATHER_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", location.latitude.toString())
            .addQueryParameter("longitude", location.longitude.toString())
            .addQueryParameter("current", "temperature_2m,weather_code")
            .addQueryParameter("timezone", "auto")
            .build()
        runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                parseStatusBarWeather(response.body?.string().orEmpty(), System.currentTimeMillis())
                    ?: error("invalid weather response")
            }
        }.onSuccess { weather ->
            _snapshot.value = weather
            prefs.edit()
                .putInt(KEY_TEMPERATURE_C, weather.temperatureC ?: Int.MIN_VALUE)
                .putInt(KEY_WEATHER_CODE, weather.weatherCode ?: Int.MIN_VALUE)
                .putLong(KEY_UPDATED_AT_MS, weather.updatedAtMs)
                .apply()
            WjxFileLogger.i(TAG, "updated code=${weather.weatherCode} tempC=${weather.temperatureC}")
        }.onFailure { error ->
            WjxFileLogger.w(TAG, "refresh failed: ${error.message}")
        }
    }

    private fun loadCache(): StatusBarWeatherSnapshot {
        val updatedAt = prefs.getLong(KEY_UPDATED_AT_MS, 0L)
        if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt > MAX_CACHE_AGE_MS) {
            return StatusBarWeatherSnapshot()
        }
        return StatusBarWeatherSnapshot(
            temperatureC = prefs.getInt(KEY_TEMPERATURE_C, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
            weatherCode = prefs.getInt(KEY_WEATHER_CODE, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
            updatedAtMs = updatedAt,
        )
    }

    private companion object {
        const val TAG = "StatusBarWeather"
        const val PREFS_NAME = "wjx_statusbar_weather_cache"
        const val KEY_TEMPERATURE_C = "temperature_c"
        const val KEY_WEATHER_CODE = "weather_code"
        const val KEY_UPDATED_AT_MS = "updated_at_ms"
        const val WEATHER_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val MAX_CACHE_AGE_MS = 6 * 60 * 60 * 1_000L
    }
}

internal fun parseStatusBarWeather(json: String, nowMs: Long): StatusBarWeatherSnapshot? = runCatching {
    val current = JSONObject(json).getJSONObject("current")
    val temperature = current.optDouble("temperature_2m", Double.NaN)
        .takeIf(Double::isFinite)
        ?.roundToInt()
    val code = current.optInt("weather_code", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
    if (temperature == null && code == null) return@runCatching null
    StatusBarWeatherSnapshot(temperatureC = temperature, weatherCode = code, updatedAtMs = nowMs)
}.getOrNull()

internal object StatusBarWeatherCode {
    fun label(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "大部晴朗"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        in 51..57 -> "毛毛雨"
        in 61..67, in 80..82 -> "雨"
        in 71..77, in 85..86 -> "雪"
        in 95..99 -> "雷雨"
        else -> "天气"
    }
}
