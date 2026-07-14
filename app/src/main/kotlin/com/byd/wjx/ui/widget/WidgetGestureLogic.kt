package com.byd.wjx.ui.widget

object WidgetGestureLogic {
    fun isLeftZone(eventX: Float, viewWidth: Int, fraction: Float): Boolean =
        viewWidth > 0 && eventX < viewWidth * fraction

    fun isWithinDoubleTapWindow(prevUpMs: Long, nowMs: Long, windowMs: Long): Boolean =
        prevUpMs > 0L && nowMs - prevUpMs <= windowMs
}
