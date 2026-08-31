package com.byd.wjx.cluster

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display

/** Conservative cluster display lookup: a numeric id is never trusted without shape validation. */
object ClusterDisplayResolver {
    private const val DISPLAY_NAME = "XDJAScreenProjection"
    private const val LEGACY_DISPLAY_ID = 2

    fun resolve(context: Context, allowLegacyId: Boolean = false): Display? {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val named = manager.displays.filter {
            it.isValid &&
                it.displayId != Display.DEFAULT_DISPLAY &&
                it.name.contains(DISPLAY_NAME, ignoreCase = true)
        }
        val preferred = named.firstOrNull { it.name.endsWith("_1") } ?: named.firstOrNull()
        if (preferred != null) return preferred
        if (!allowLegacyId) return null
        return manager.getDisplay(LEGACY_DISPLAY_ID)?.takeIf(::looksLikeInstrument)
    }

    internal fun looksLikeInstrument(display: Display): Boolean {
        if (!display.isValid || display.displayId == Display.DEFAULT_DISPLAY) return false
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        return size.x >= 800 && size.y >= 240 && size.x > size.y
    }
}
