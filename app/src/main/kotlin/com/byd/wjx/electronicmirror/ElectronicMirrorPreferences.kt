package com.byd.wjx.electronicmirror

import android.content.Context
import android.graphics.Rect
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

enum class ElectronicMirrorDisplayTarget(val storedValue: String) {
    CLUSTER("cluster"),
    CENTER("center");

    companion object {
        fun fromStored(value: String?): ElectronicMirrorDisplayTarget =
            entries.firstOrNull { it.storedValue == value } ?: CLUSTER
    }
}

/** Persistent settings for the turn-signal electronic rearview mirror. */
@Singleton
class ElectronicMirrorPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val enabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, false)
    val thresholdKmh: Int get() = prefs.getInt(KEY_THRESHOLD_KMH, DEFAULT_THRESHOLD_KMH)
    val pipWidthPct: Int
        get() = prefs.getInt(KEY_PIP_WIDTH_PCT, DEFAULT_PIP_WIDTH_PCT)
            .coerceIn(MIN_PIP_WIDTH_PCT, MAX_PIP_WIDTH_PCT)
    val pipXPx: Int get() = prefs.getInt(KEY_PIP_X_PX, UNSET_PX)
    val pipYPx: Int get() = prefs.getInt(KEY_PIP_Y_PX, UNSET_PX)
    val bsdGlow: Boolean get() = prefs.getBoolean(KEY_BSD_GLOW, true)
    val displayTarget: ElectronicMirrorDisplayTarget
        get() = ElectronicMirrorDisplayTarget.fromStored(prefs.getString(KEY_DISPLAY_TARGET, null))

    companion object {
        const val PREFS_NAME = "electronic_mirror"
        const val KEY_ENABLED = "enabled"
        const val KEY_THRESHOLD_KMH = "threshold_kmh"
        const val KEY_PIP_WIDTH_PCT = "pip_width_pct"
        const val KEY_PIP_X_PX = "pip_x_px"
        const val KEY_PIP_Y_PX = "pip_y_px"
        const val KEY_BSD_GLOW = "bsd_glow"
        const val KEY_DISPLAY_TARGET = "display_target"

        const val UNSET_PX = -1
        const val DEFAULT_THRESHOLD_KMH = 20
        const val DEFAULT_PIP_WIDTH_PCT = 36
        const val MIN_THRESHOLD_KMH = 0
        const val MAX_THRESHOLD_KMH = 60
        const val MIN_PIP_WIDTH_PCT = 20
        const val MAX_PIP_WIDTH_PCT = 50

        fun pipSize(displayWidth: Int, widthPct: Int): Size {
            val width = (displayWidth * widthPct.coerceIn(MIN_PIP_WIDTH_PCT, MAX_PIP_WIDTH_PCT) / 100f)
                .roundToInt()
            return Size(width, width * 9 / 16)
        }

        fun defaultPipRect(displayWidth: Int, displayHeight: Int, widthPct: Int): Rect {
            val size = pipSize(displayWidth, widthPct)
            val left = 0
            val top = ((displayHeight - size.height) / 2).coerceAtLeast(0)
            return Rect(left, top, left + size.width, top + size.height)
        }

        fun clusterSideRects(bounds: Rect): Pair<Rect, Rect> {
            val centerX = bounds.left + bounds.width() / 2
            return Rect(bounds.left, bounds.top, centerX, bounds.bottom) to
                Rect(centerX, bounds.top, bounds.right, bounds.bottom)
        }

        fun centerLeftMaxX(displayWidth: Int, windowWidth: Int): Int =
            (displayWidth / 2 - windowWidth).coerceAtLeast(0)
    }
}
