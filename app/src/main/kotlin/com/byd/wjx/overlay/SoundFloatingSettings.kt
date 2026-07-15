package com.byd.wjx.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import java.io.File
import kotlin.math.max

class SoundFloatingSettings(context: Context) {
    private val appContext = context.applicationContext
    private val storageContext = storageContext(context)
    private val prefs = storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    var expanded: Boolean
        get() = prefs.getBoolean(KEY_EXPANDED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_EXPANDED, value).apply()
        }

    var x: Int
        get() = prefs.getInt(KEY_X, Int.MIN_VALUE)
        set(value) {
            prefs.edit().putInt(KEY_X, value).apply()
        }

    var y: Int
        get() = prefs.getInt(KEY_Y, Int.MIN_VALUE)
        set(value) {
            prefs.edit().putInt(KEY_Y, value).apply()
        }

    var iconStyle: String
        get() {
            val saved = prefs.getString(KEY_ICON_STYLE, ICON_STYLE_CLASSIC) ?: ICON_STYLE_CLASSIC
            return when (saved) {
                ICON_STYLE_CLASSIC,
                ICON_STYLE_TOOLBOX -> saved
                ICON_STYLE_CUSTOM -> if (hasCustomIcon()) ICON_STYLE_CUSTOM else ICON_STYLE_CLASSIC
                else -> ICON_STYLE_CLASSIC
            }
        }
        set(value) {
            val normalized = when (value) {
                ICON_STYLE_CLASSIC,
                ICON_STYLE_TOOLBOX -> value
                ICON_STYLE_CUSTOM -> if (hasCustomIcon()) ICON_STYLE_CUSTOM else ICON_STYLE_CLASSIC
                else -> ICON_STYLE_CLASSIC
            }
            prefs.edit().putString(KEY_ICON_STYLE, normalized).apply()
        }

    var iconAlpha: Float
        get() = prefs.getFloat(KEY_ICON_ALPHA, DEFAULT_ICON_ALPHA).coerceIn(ICON_ALPHA_MIN, ICON_ALPHA_MAX)
        set(value) {
            prefs.edit().putFloat(KEY_ICON_ALPHA, value.coerceIn(ICON_ALPHA_MIN, ICON_ALPHA_MAX)).apply()
        }

    var iconScale: Float
        get() = prefs.getFloat(KEY_ICON_SCALE, DEFAULT_ICON_SCALE).coerceIn(ICON_SCALE_MIN, ICON_SCALE_MAX)
        set(value) {
            prefs.edit().putFloat(KEY_ICON_SCALE, value.coerceIn(ICON_SCALE_MIN, ICON_SCALE_MAX)).apply()
        }

    val customIconPath: String
        get() = customIconFile.absolutePath

    fun savePosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    fun hasCustomIcon(): Boolean = customIconFile.isFile

    fun saveCustomIcon(uri: Uri): Boolean {
        val file = customIconFile
        file.parentFile?.mkdirs()
        return runCatching {
            val bitmap = appContext.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return false
            val normalized = normalizeIconBitmap(bitmap)
            file.outputStream().use { output ->
                normalized.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (normalized !== bitmap) normalized.recycle()
            bitmap.recycle()
            iconStyle = ICON_STYLE_CUSTOM
            true
        }.getOrDefault(false)
    }

    fun resetIcon() {
        iconStyle = ICON_STYLE_CLASSIC
        iconAlpha = DEFAULT_ICON_ALPHA
        iconScale = DEFAULT_ICON_SCALE
    }

    private val customIconFile: File
        get() = File(storageContext.filesDir, "sound_floating/custom_icon")

    private fun normalizeIconBitmap(source: Bitmap): Bitmap {
        val maxSide = max(source.width, source.height).coerceAtLeast(1)
        val scale = CUSTOM_ICON_SIZE.toFloat() / maxSide.toFloat()
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        val output = Bitmap.createBitmap(CUSTOM_ICON_SIZE, CUSTOM_ICON_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            scaled,
            ((CUSTOM_ICON_SIZE - targetWidth) / 2).toFloat(),
            ((CUSTOM_ICON_SIZE - targetHeight) / 2).toFloat(),
            null,
        )
        if (scaled !== source) scaled.recycle()
        return output
    }

    companion object {
        const val ICON_STYLE_CLASSIC = "classic"
        const val ICON_STYLE_TOOLBOX = "toolbox"
        const val ICON_STYLE_CUSTOM = "custom"
        const val DEFAULT_ICON_ALPHA = 1.0f
        const val ICON_ALPHA_MIN = 0.25f
        const val ICON_ALPHA_MAX = 1.0f
        const val DEFAULT_ICON_SCALE = 1.0f
        const val ICON_SCALE_MIN = 0.65f
        const val ICON_SCALE_MAX = 1.45f

        private const val CUSTOM_ICON_SIZE = 512
        private const val PREFS_NAME = "wjx_sound_floating"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_EXPANDED = "expanded"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_ICON_ALPHA = "icon_alpha"
        private const val KEY_ICON_SCALE = "icon_scale"

        private fun storageContext(context: Context): Context {
            val appContext = context.applicationContext
            return appContext.createDeviceProtectedStorageContext()
        }
    }
}
